package com.moveinsync.mobilitycopilot.action.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExecutionReceipt(
        UUID actionId,
        UUID runId,
        String idempotencyKey,
        ActionStatus status,
        Instant attemptedAt,
        Instant completedAt,
        String externalReference,
        String message) {

    public ExecutionReceipt {
        Objects.requireNonNull(actionId, "actionId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(attemptedAt, "attemptedAt is required");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (status != ActionStatus.EXECUTED && status != ActionStatus.APPROVED_NOT_EXECUTED) {
            throw new IllegalArgumentException("execution receipt requires a terminal execution status");
        }
        if (status == ActionStatus.EXECUTED && completedAt == null) {
            throw new IllegalArgumentException("executed receipt requires completedAt");
        }
    }
}
