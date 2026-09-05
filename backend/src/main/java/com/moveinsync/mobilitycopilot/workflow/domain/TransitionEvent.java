package com.moveinsync.mobilitycopilot.workflow.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One traceable transition: which node ran, how long, with which outcome and safe attributes. */
public record TransitionEvent(
        UUID runId,
        String traceId,
        WorkflowNode node,
        String subNode,
        WorkflowStep fromStep,
        WorkflowStep toStep,
        Instant startedAt,
        long durationMs,
        String outcome,
        Map<String, String> attributes) {

    public TransitionEvent {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(traceId);
        Objects.requireNonNull(node);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(outcome);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
