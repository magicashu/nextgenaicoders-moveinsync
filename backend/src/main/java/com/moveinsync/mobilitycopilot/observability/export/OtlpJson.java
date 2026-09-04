package com.moveinsync.mobilitycopilot.observability.export;

import com.moveinsync.mobilitycopilot.observability.Span;
import com.moveinsync.mobilitycopilot.observability.TraceRecorder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an OTLP/HTTP JSON payload (ExportTraceServiceRequest) from a recorded trace. Langfuse
 * accepts OTLP JSON on /api/public/otel/v1/traces and maps langfuse.* / gen_ai.* attributes
 * (verified against the Langfuse OpenTelemetry integration docs, 2026-09-05). Written by hand to
 * avoid a root dependency on the OpenTelemetry SDK; the Integration Owner may swap in the SDK later.
 */
public final class OtlpJson {

    private OtlpJson() {
    }

    public static Map<String, Object> request(TraceRecorder.Trace trace, String serviceName, String environment) {
        List<Map<String, Object>> spans = new ArrayList<>();
        for (Span span : trace.spans()) {
            spans.add(span(trace.traceId(), span));
        }
        Map<String, Object> scopeSpans = new LinkedHashMap<>();
        scopeSpans.put("scope", Map.of("name", "mobility-decision-copilot", "version", "workflow-v1"));
        scopeSpans.put("spans", spans);
        Map<String, Object> resourceSpans = new LinkedHashMap<>();
        resourceSpans.put("resource", Map.of("attributes", List.of(
                attribute("service.name", serviceName),
                attribute("deployment.environment", environment),
                attribute("langfuse.environment", environment))));
        resourceSpans.put("scopeSpans", List.of(scopeSpans));
        return Map.of("resourceSpans", List.of(resourceSpans));
    }

    static Map<String, Object> span(String traceId, Span span) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("traceId", traceId);
        out.put("spanId", span.spanId());
        if (span.parentSpanId() != null) {
            out.put("parentSpanId", span.parentSpanId());
        }
        out.put("name", span.name());
        out.put("kind", 1);
        out.put("startTimeUnixNano", nanos(span.startedAt()));
        out.put("endTimeUnixNano", nanos(span.endedAt() == null ? span.startedAt() : span.endedAt()));
        List<Map<String, Object>> attributes = new ArrayList<>();
        span.attributes().forEach((k, v) -> attributes.add(attribute(k, v)));
        attributes.add(attribute("mobility.span_kind", span.kind().name()));
        out.put("attributes", attributes);
        out.put("status", Map.of("code", "OK".equals(span.status()) ? 1 : 2, "message", span.status()));
        return out;
    }

    static Map<String, Object> attribute(String key, String value) {
        return Map.of("key", key, "value", Map.of("stringValue", value == null ? "" : value));
    }

    static String nanos(java.time.Instant instant) {
        return String.valueOf(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }
}
