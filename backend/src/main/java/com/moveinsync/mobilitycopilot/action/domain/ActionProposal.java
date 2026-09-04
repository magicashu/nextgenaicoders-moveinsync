package com.moveinsync.mobilitycopilot.action.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ActionProposal(
        UUID actionId,
        UUID runId,
        ActionType type,
        String title,
        String rationale,
        Map<String, String> scope,
        String evidenceVersion,
        Instant createdAt,
        Instant expiresAt,
        ActionStatus status) {

    public ActionProposal {
        Objects.requireNonNull(actionId, "actionId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(rationale, "rationale is required");
        Objects.requireNonNull(evidenceVersion, "evidenceVersion is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        Objects.requireNonNull(status, "status is required");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        scope = scope == null ? Map.of() : Map.copyOf(scope);
    }
}
