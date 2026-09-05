package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.config.SarvamProperties;
import com.moveinsync.mobilitycopilot.reporting.application.SnapshotCache;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/** Explicit read-aloud only; the provider key and provider failures never reach the browser. */
@RestController
@RequestMapping("/api/v1/speech")
public final class SpeechController {
    private final RequestContext context;
    private final AccessAuthorizer authorizer;
    private final SarvamProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore slots = new Semaphore(2);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SnapshotCache<Key, Audio> cache = new SnapshotCache<>(16);
    private record Key(String actor, String tenant, Set<String> roles, String text, String voice) {}
    public record SpeechRequest(@NotBlank @Size(max = 2500) String text, @NotBlank String voice) {}
    public record Audio(String audio, String contentType) {}

    public SpeechController(RequestContext context, AccessAuthorizer authorizer, SarvamProperties properties) {
        this.context = context; this.authorizer = authorizer; this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<?> speak(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @Valid @RequestBody SpeechRequest request) {
        var actor = context.actor(actorId, businessUnit, roles);
        authorizer.require(actor, context.tenant(actor), Permission.READ_TENANT_METRICS);
        if (!Set.of("shubh", "ritu", "rahul").contains(request.voice()))
            return ResponseEntity.badRequest().body(Map.of("message", "Please choose an available voice."));
        if (properties.apiKey() == null || properties.apiKey().isBlank())
            return unavailable();
        if (!slots.tryAcquire()) return ResponseEntity.status(429).body(Map.of("message", "Audio is busy. Please try again shortly."));
        try {
            var key = new Key(actor.actorId(), actor.businessUnit(), Set.copyOf(actor.roles()), request.text(), request.voice());
            return ResponseEntity.ok(cache.get(key, false, () -> convert(request)));
        } catch (RuntimeException failure) {
            return unavailable();
        } finally { slots.release(); }
    }

    private Audio convert(SpeechRequest request) {
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("text", request.text(), "language_code", "en-IN",
                    "model", "bulbul:v3", "speaker", request.voice(), "output_audio_codec", "mp3"));
            var outgoing = HttpRequest.newBuilder(URI.create("https://api.sarvam.ai/text-to-speech"))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                    .header("api-subscription-key", properties.apiKey()).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = client.send(outgoing, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() != 200) throw new IllegalStateException("Audio unavailable");
                var reading = readers.submit(() -> stream.readNBytes(2_000_001));
                byte[] bytes;
                try { bytes = reading.get(30, TimeUnit.SECONDS); }
                finally { reading.cancel(true); }
                if (bytes.length > 2_000_000) throw new IllegalStateException("Audio unavailable");
                String audio = mapper.readTree(bytes).path("audios").path(0).asText("");
                if (audio.isBlank()) throw new IllegalStateException("Audio unavailable");
                java.util.Base64.getDecoder().decode(audio);
                return new Audio(audio, "audio/mpeg");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Audio unavailable");
        } catch (Exception failure) { throw new IllegalStateException("Audio unavailable"); }
    }

    private static ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(Map.of("message", "Read-aloud is temporarily unavailable."));
    }
    @PreDestroy void close() { readers.shutdownNow(); client.close(); }
}
