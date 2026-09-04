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
            @Value("${LANGFUSE_HOST:}") String host,
            @Value("${LANGFUSE_PUBLIC_KEY:}") String publicKey,
            @Value("${LANGFUSE_SECRET_KEY:}") String secretKey,
            @Value("${MOBILITY_ENVIRONMENT:local}") String environment) {
        if (host == null || host.isBlank() || publicKey.isBlank() || secretKey.isBlank()) {
            return new InMemoryTraceExporterHolder();
        }
        return new LangfuseOtlpExporter(host, publicKey, secretKey, "mobility-decision-copilot", environment, 256, Duration.ofSeconds(3));
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
