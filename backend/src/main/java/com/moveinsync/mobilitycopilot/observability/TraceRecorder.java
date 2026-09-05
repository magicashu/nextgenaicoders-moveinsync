package com.moveinsync.mobilitycopilot.observability;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One root trace per decision run with nested spans for request, authorization, roles, tools,
 * critic, report, approval, revalidation, execution and the audit link. Trace ids are generated
 * locally, so a run remains traceable when the exporter or Langfuse is unavailable.
 */
public final class TraceRecorder {

    public static final String TRACE_NAME = "mobility_run";

    private final Map<String, Trace> traces = new ConcurrentHashMap<>();
    private final TraceExporter exporter;

    public TraceRecorder(TraceExporter exporter) {
        this.exporter = exporter;
    }

    public Trace start(UUID runId, String businessUnit, Map<String, ?> attributes) {
        return start(runId, businessUnit, attributes, Instant.now());
    }

    public Trace start(UUID runId, String businessUnit, Map<String, ?> attributes, Instant startedAt) {
        String traceId = traceIdFor(runId);
        Map<String, String> safe = Redaction.attributes(attributes);
        safe.put(TraceAttributes.RUN_ID, runId.toString());
        safe.put(TraceAttributes.BUSINESS_UNIT, businessUnit);
        safe.put(TraceAttributes.TRACE_ID, traceId);
        safe.put(TraceAttributes.LANGFUSE_TRACE_NAME, TRACE_NAME);
        safe.put(TraceAttributes.LANGFUSE_SESSION, runId.toString());
        return traces.computeIfAbsent(traceId, ignored -> new Trace(traceId, runId, businessUnit,
                new Span(null, TRACE_NAME, Span.Kind.REQUEST, safe, startedAt)));
    }

    public Optional<Trace> find(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    /** Ends the root span, queues export (never blocks) and returns the trace for the caller. */
    public Trace finish(Trace trace, String status, Map<String, ?> attributes) {
        trace.root.end(status, Redaction.attributes(attributes));
        exporter.export(trace);
        return trace;
    }

    /** Deterministic 32-hex trace id derived from the run id so audit events and spans share it. */
    public static String traceIdFor(UUID runId) {
        return runId.toString().replace("-", "");
    }

    public final class Trace {
        private final String traceId;
        private final UUID runId;
        private final String businessUnit;
        private final Span root;
        private final Deque<Span> open = new ArrayDeque<>();
        private final CostAndLatencyLedger ledger = new CostAndLatencyLedger();

        Trace(String traceId, UUID runId, String businessUnit, Span root) {
            this.traceId = traceId;
            this.runId = runId;
            this.businessUnit = businessUnit;
            this.root = root;
            this.open.push(root);
        }

        public String traceId() { return traceId; }
        public UUID runId() { return runId; }
        public String businessUnit() { return businessUnit; }
        public Span root() { return root; }
        public CostAndLatencyLedger ledger() { return ledger; }

        /** Record a completed operation with its real wall-clock interval and explicit parallel parent. */
        public synchronized Span recordUnder(Span parent, String name, Span.Kind kind, Instant startedAt,
                                             long durationMs, String status, Map<String, ?> attributes) {
            Map<String, String> safe = Redaction.attributes(attributes);
            safe.put(TraceAttributes.LANGFUSE_OBSERVATION_TYPE, observationType(kind));
            Span span = new Span(parent.spanId(), name, kind, safe, startedAt);
            span.endAt(status, Map.of(), startedAt.plusMillis(Math.max(0, durationMs)));
            parent.addChild(span);
            return span;
        }

        /** Opens a child of the innermost open span. */
        public synchronized Span begin(String name, Span.Kind kind, Map<String, ?> attributes) {
            Span parent = open.peek();
            Map<String, String> safe = Redaction.attributes(attributes);
            safe.put(TraceAttributes.LANGFUSE_OBSERVATION_TYPE, observationType(kind));
            Span span = new Span(Objects.requireNonNull(parent).spanId(), name, kind, safe);
            parent.addChild(span);
            open.push(span);
            return span;
        }

        /** Opens a child under an explicit parent (for parallel branches). */
        public synchronized Span beginUnder(Span parent, String name, Span.Kind kind, Map<String, ?> attributes) {
            Map<String, String> safe = Redaction.attributes(attributes);
            safe.put(TraceAttributes.LANGFUSE_OBSERVATION_TYPE, observationType(kind));
            Span span = new Span(parent.spanId(), name, kind, safe);
            parent.addChild(span);
            return span;
        }

        public synchronized void end(Span span, String status, Map<String, ?> attributes) {
            span.end(status, Redaction.attributes(attributes));
            if (open.peek() == span) {
                open.pop();
            } else {
                open.remove(span);
            }
        }

        public synchronized void recordModelCall(String role, String modelId, long inputTokens, long outputTokens, long latencyMs, boolean fallback) {
            ledger.recordModelCall(role, modelId, inputTokens, outputTokens, latencyMs, fallback);
        }

        public synchronized void recordToolCall(String worker, long latencyMs, boolean ok) {
            ledger.recordToolCall(worker, latencyMs, ok);
        }

        public List<Span> spans() {
            return Collections.unmodifiableList(root.flatten());
        }

        public Map<String, String> summary() {
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put(TraceAttributes.TRACE_ID, traceId);
            summary.put(TraceAttributes.RUN_ID, runId.toString());
            summary.put(TraceAttributes.BUSINESS_UNIT, businessUnit);
            summary.put("spans", String.valueOf(spans().size()));
            summary.put("latencyMs", String.valueOf(root.ended() ? root.durationMs() : java.time.Duration.between(root.startedAt(), Instant.now()).toMillis()));
            summary.putAll(ledger.attributes());
            return summary;
        }
    }

    static String observationType(Span.Kind kind) {
        return switch (kind) {
            case MODEL -> "generation";
            case AGENT -> "agent";
            case TOOL -> "tool";
            case CRITIC, REVALIDATION -> "evaluator";
            case REQUEST -> "span";
            default -> "span";
        };
    }
}
