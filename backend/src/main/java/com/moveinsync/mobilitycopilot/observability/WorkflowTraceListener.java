package com.moveinsync.mobilitycopilot.observability;

import com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener;
import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit run/worker association survives parallel threads without an ambient span stack. */
public final class WorkflowTraceListener implements TransitionListener {
    private final TraceRecorder recorder;
    private final Map<UUID, List<TransitionEvent>> pending = new ConcurrentHashMap<>();

    public WorkflowTraceListener(TraceRecorder recorder) { this.recorder = recorder; }

    @Override
    public void onTransition(TransitionEvent event) {
        List<TransitionEvent> children = pending.computeIfAbsent(event.runId(), ignored -> new ArrayList<>());
        synchronized (children) {
            if (event.subNode() != null) {
                children.add(event);
                return;
            }
            var trace = recorder.start(event.runId(), event.attributes().getOrDefault("businessUnit", "unknown"),
                    Map.of("workflow.version", "langgraph4j-v1"), event.startedAt());
            Span parent = record(trace, trace.root(), event);
            Map<String, List<TransitionEvent>> workers = new LinkedHashMap<>();
            for (TransitionEvent child : children) {
                String worker = child.attributes().get("worker");
                if (worker == null) record(trace, parent, child);
                else workers.computeIfAbsent(worker, ignored -> new ArrayList<>()).add(child);
            }
            workers.forEach((worker, events) -> {
                var start = events.stream().map(TransitionEvent::startedAt).min(java.time.Instant::compareTo).orElse(event.startedAt());
                var end = events.stream().map(e -> e.startedAt().plusMillis(e.durationMs())).max(java.time.Instant::compareTo).orElse(start);
                Span branch = trace.recordUnder(parent, "investigator." + worker, Span.Kind.AGENT, start,
                        java.time.Duration.between(start, end).toMillis(), "OK", Map.of("worker", worker));
                events.forEach(child -> record(trace, branch, child));
            });
            children.clear();
            if (event.node() == WorkflowNode.APPROVAL_INTERRUPT || event.node() == WorkflowNode.APPEND_AUDIT_EVENT
                    || event.toStep() == WorkflowStep.FAILED) {
                recorder.finish(trace, event.toStep() == WorkflowStep.FAILED ? "ERROR" : "OK",
                        Map.of("workflow.final_step", event.toStep().name(), "audit.run_id", event.runId().toString()));
                pending.remove(event.runId(), children);
            }
        }
    }

    private Span record(TraceRecorder.Trace trace, Span parent, TransitionEvent event) {
        Map<String, String> attrs = new LinkedHashMap<>(event.attributes());
        attrs.put("workflow.node", event.node().name());
        attrs.put("workflow.from_step", String.valueOf(event.fromStep()));
        attrs.put("workflow.to_step", String.valueOf(event.toStep()));
        attrs.put("workflow.outcome", event.outcome());
        attrs.put("langfuse.session.id", event.runId().toString());
        String name = event.subNode() == null ? event.node().spanName() : event.subNode();
        Span.Kind kind = event.subNode() != null && event.subNode().startsWith("llm.") ? Span.Kind.MODEL
                : event.subNode() != null ? Span.Kind.TOOL
                : switch (event.node()) {
                    case SUPERVISOR_PLAN, RUN_INVESTIGATIONS, EVIDENCE_CRITIC, COMPOSE_DECISION_BRIEF -> Span.Kind.AGENT;
                    case AUTHORIZE_SCOPE -> Span.Kind.AUTHORIZATION;
                    case APPROVAL_INTERRUPT -> Span.Kind.APPROVAL;
                    case REVALIDATE_AND_EXECUTE -> Span.Kind.EXECUTION;
                    default -> Span.Kind.WORKFLOW_NODE;
                };
        boolean error = event.toStep() == WorkflowStep.FAILED || event.outcome().equals("error") || event.outcome().equals("fallback");
        return trace.recordUnder(parent, name, kind, event.startedAt(), event.durationMs(), error ? "ERROR" : "OK", attrs);
    }
}
