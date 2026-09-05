package com.moveinsync.mobilitycopilot.workflow.adapter.sarvam;

import com.moveinsync.mobilitycopilot.config.SarvamProperties;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.ModelCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.moveinsync.mobilitycopilot.workflow.application.ports.ModelCallException.Reason.*;

/** One bounded HTTP request per attempt; ModelAssist owns the single JSON repair retry. */
public final class SarvamLanguageModelAdapter implements LanguageModelPort, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SarvamLanguageModelAdapter.class);
    private static final Set<String> ROLES = Set.of("supervisor", "investigator", "evidence-critic", "briefing-action");
    private final SarvamProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final Semaphore slots;

    public SarvamLanguageModelAdapter(SarvamProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("SARVAM_API_KEY is required when LANGUAGE_MODEL=sarvam");
        }
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.slots = new Semaphore(properties.maxConcurrentCalls());
        LOG.info("Sarvam enabled: model={}", properties.model());
    }

    @Override
    public Optional<Completion> complete(Request request) {
        if (request == null || !ROLES.contains(request.role()) || request.systemPrompt() == null
                || request.userPayloadJson() == null || request.maxOutputTokens() < 1 || request.maxOutputTokens() > 4096
                || request.timeout() == null || request.timeout().isNegative() || request.timeout().isZero()) {
            throw new IllegalArgumentException("Invalid bounded model request");
        }
        if (!slots.tryAcquire()) throw new ModelCallException(CAPACITY);
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var payload = mapper.createObjectNode();
            payload.put("model", properties.model());
            payload.put("temperature", 0);
            payload.put("stream", false);
            payload.put("n", 1);
            payload.put("max_tokens", request.maxOutputTokens());
            payload.putNull("reasoning_effort");
            payload.putObject("response_format").put("type", "json_object");
            var messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.systemPrompt());
            messages.addObject().put("role", "user").put("content", request.userPayloadJson());
            byte[] body = mapper.writeValueAsBytes(payload);
            if (body.length > properties.maxRequestBytes()) throw new ModelCallException(REQUEST_TOO_LARGE);
            Duration timeout = request.timeout().compareTo(properties.timeout()) < 0 ? request.timeout() : properties.timeout();
            var http = HttpRequest.newBuilder(properties.endpoint()).timeout(timeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("api-subscription-key", properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending = client.sendAsync(http, info -> new LimitedBody(properties.maxResponseBytes()));
            var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) {
                throw new ModelCallException(switch (response.statusCode()) {
                    case 401, 403 -> HTTP_AUTH;
                    case 429 -> HTTP_RATE_LIMIT;
                    default -> HTTP_ERROR;
                });
            }
            var root = mapper.readTree(response.body());
            var choice = root.path("choices").path(0);
            if (!"stop".equals(choice.path("finish_reason").asText())) throw new ModelCallException(INCOMPLETE_RESPONSE);
            var content = choice.path("message").path("content");
            if (!content.isString() || content.asText().isBlank()) throw new ModelCallException(INVALID_RESPONSE);
            var usage = root.path("usage");
            var result = new Completion(content.asText(), Math.max(0, usage.path("prompt_tokens").asLong(0)),
                    Math.max(0, usage.path("completion_tokens").asLong(0)), elapsed(started));
            LOG.info("Sarvam completed: role={} model={} inputTokens={} outputTokens={} latencyMs={}",
                    request.role(), modelId(), result.inputTokens(), result.outputTokens(), result.latencyMs());
            return Optional.of(result);
        } catch (TimeoutException e) {
            throw new ModelCallException(TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException(INTERRUPTED);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause.getCause() != null && !(cause instanceof ModelCallException)) cause = cause.getCause();
            if (cause instanceof ModelCallException failure) throw failure;
            if (cause instanceof java.net.http.HttpTimeoutException) throw new ModelCallException(TIMEOUT);
            throw new ModelCallException(TRANSPORT);
        } catch (tools.jackson.core.JacksonException e) {
            throw new ModelCallException(INVALID_RESPONSE);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            slots.release();
        }
    }

    private static long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }

    @Override public String modelId() { return properties.model(); }
    @Override public void close() { client.shutdownNow(); }

    /** Reject oversized responses during download, including chunked bodies without Content-Length. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new ModelCallException(RESPONSE_TOO_LARGE));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
