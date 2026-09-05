package com.moveinsync.mobilitycopilot.workflow.domain;

import java.time.Instant;
import java.util.Objects;

/** Qualified failure record. Unexpected exceptions still surface; known failures are classified. */
public record WorkflowError(WorkflowNode node, String code, String message, boolean transientFailure, Instant occurredAt) {

    public WorkflowError {
        Objects.requireNonNull(node);
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
        Objects.requireNonNull(occurredAt);
    }
}
