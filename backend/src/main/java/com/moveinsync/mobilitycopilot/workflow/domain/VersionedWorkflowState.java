package com.moveinsync.mobilitycopilot.workflow.domain;

import java.time.Instant;
import java.util.Objects;

public record VersionedWorkflowState(
        WorkflowState state,
        long version,
        Instant persistedAt) {

    public VersionedWorkflowState {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(persistedAt, "persistedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
    }
}
