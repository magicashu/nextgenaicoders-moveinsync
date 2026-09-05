package com.moveinsync.mobilitycopilot.observability.export;

import com.moveinsync.mobilitycopilot.observability.Redaction;
import com.moveinsync.mobilitycopilot.observability.TraceExporter;
import com.moveinsync.mobilitycopilot.observability.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Non-blocking Langfuse export over OTLP/HTTP JSON. Traces are queued on a bounded buffer and sent by
 * one daemon thread with a short timeout; when the queue is full or Langfuse is unreachable the
 * trace is dropped and counted, the local trace id remains valid, and the workflow is never delayed.
 */
public final class LangfuseOtlpExporter implements TraceExporter, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LangfuseOtlpExporter.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final URI endpoint;
    private final String authorization;
    private final String serviceName;
    private final String environment;
    private final HttpClient client;
    private final BlockingQueue<String> queue;
    private final Thread worker;
    private final AtomicLong exported = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private volatile boolean running = true;

    public LangfuseOtlpExporter(String baseUrl, String publicKey, String secretKey, String serviceName, String environment, int queueCapacity, Duration timeout) {
        this(URI.create(baseUrl.replaceAll("/$", "") + "/api/public/otel/v1/traces"),
                "Basic " + Base64.getEncoder().encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8)),
                serviceName, environment, queueCapacity, timeout);
    }

    public LangfuseOtlpExporter(URI endpoint, String authorization, String serviceName, String environment, int queueCapacity, Duration timeout) {
        this.endpoint = endpoint;
        this.authorization = authorization;
        this.serviceName = serviceName;
        this.environment = environment;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.worker = new Thread(() -> drain(timeout), "langfuse-otlp-exporter");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void export(TraceRecorder.Trace trace) {
        String snapshot = MAPPER.writeValueAsString(OtlpJson.request(trace, serviceName, environment));
        if (!queue.offer(snapshot)) {
            dropped.incrementAndGet();
            lastError.set("export queue full; trace " + trace.traceId() + " dropped (local id preserved)");
        }
    }

    private void drain(Duration timeout) {
        while (running || !queue.isEmpty()) {
            try {
                String trace = queue.poll(100, TimeUnit.MILLISECONDS);
                if (trace == null) {
                    continue;
                }
                send(trace, timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                failures.incrementAndGet();
                lastError.set(Redaction.text(e.getMessage()));
            }
        }
    }

    void send(String body, Duration timeout) {
        try {
            var builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-langfuse-ingestion-version", "4")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (authorization != null && !authorization.isBlank()) builder.header("Authorization", authorization);
            HttpRequest request = builder.build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                exported.incrementAndGet();
            } else {
                failures.incrementAndGet();
                lastError.set("OTLP endpoint responded " + response.statusCode());
            }
        } catch (Exception e) {
            failures.incrementAndGet();
            lastError.set(Redaction.text(e.getClass().getSimpleName() + ": " + e.getMessage()));
            log.warn("OTLP export failed (workflow unaffected): {}", lastError.get());
        }
    }

    @Override
    public ExporterStatus status() {
        return new ExporterStatus(endpoint.toString(), true, queue.size(), exported.get(), dropped.get(), failures.get(), lastError.get(),
                failures.get() > 0 || dropped.get() > 0);
    }

    @Override
    public void close() {
        running = false;
        try { worker.join(5000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (worker.isAlive()) worker.interrupt();
        client.shutdownNow();
    }
}
