package com.moveinsync.mobilitycopilot.observability;

import com.moveinsync.mobilitycopilot.observability.export.LangfuseOtlpExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Telemetry wiring. Langfuse is optional: without LANGFUSE_HOST/keys the in-memory exporter is used
 * and the product runs unchanged. Keys are read from the environment only and never logged.
 */
@Configuration
public class TelemetryBeans {

    @Bean(destroyMethod = "close")
    public TraceExporter traceExporter(
            @Value("${LANGFUSE_HOST:}") String legacyHost,
            @Value("${LANGFUSE_BASE_URL:}") String baseUrl,
            @Value("${LANGFUSE_PUBLIC_KEY:}") String publicKey,
            @Value("${LANGFUSE_SECRET_KEY:}") String secretKey,
            @Value("${MOBILITY_ENVIRONMENT:local}") String environment,
            @Value("${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:}") String collectorEndpoint) {
        String host = baseUrl.isBlank() ? legacyHost : baseUrl;
        var exporters = new java.util.ArrayList<TraceExporter>();
        if (!host.isBlank() || !publicKey.isBlank() || !secretKey.isBlank()) {
            if (host.isBlank() || publicKey.isBlank() || secretKey.isBlank()) {
                throw new IllegalArgumentException("Set LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY together");
            }
            exporters.add(new LangfuseOtlpExporter(host, publicKey, secretKey, "mobility-decision-copilot", environment, 256, Duration.ofSeconds(3)));
        }
        if (!collectorEndpoint.isBlank()) exporters.add(new LangfuseOtlpExporter(java.net.URI.create(collectorEndpoint), "",
                "mobility-decision-copilot", environment, 256, Duration.ofSeconds(3)));
        if (exporters.isEmpty()) return new InMemoryTraceExporterHolder();
        return new CompositeTraceExporter(exporters);
    }

    @Bean
    public TraceRecorder traceRecorder(TraceExporter exporter) {
        return new TraceRecorder(exporter);
    }

    /** Closeable wrapper so the same destroyMethod applies to both exporter types. */
    static final class InMemoryTraceExporterHolder extends TraceExporterDelegate implements AutoCloseable {
        InMemoryTraceExporterHolder() {
            super(new TraceExporter.InMemory());
        }

        @Override
        public void close() {
        }
    }

    static class TraceExporterDelegate implements TraceExporter {
        private final TraceExporter delegate;

        TraceExporterDelegate(TraceExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void export(TraceRecorder.Trace trace) {
            delegate.export(trace);
        }

        @Override
        public ExporterStatus status() {
            return delegate.status();
        }

        public TraceExporter delegate() {
            return delegate;
        }
    }
}
