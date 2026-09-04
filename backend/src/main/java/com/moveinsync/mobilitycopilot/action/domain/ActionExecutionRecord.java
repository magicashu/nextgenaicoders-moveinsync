package com.moveinsync.mobilitycopilot.action.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persisted execution state keyed by idempotency key. Exactly one row per key, ever. */
public record ActionExecutionRecord(
        String idempotencyKey,
        UUID actionId,
        UUID runId,
        String businessUnit,
        ActionType type,
        ActionStatus status,
        String evidenceVersion,
        Instant claimedAt,
        Instant revalidatedAt,
        Instant executedAt,
        String externalReference,
        String message,
        int attempts) {

    public ActionExecutionRecord {
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(type);
        Objects.requireNonNull(status);
        Objects.requireNonNull(claimedAt);
    }

    public boolean terminal() {
        return status == ActionStatus.EXECUTED;
    }

    public ExecutionReceipt toReceipt() {
        ActionStatus receiptStatus = status == ActionStatus.EXECUTED ? ActionStatus.EXECUTED : ActionStatus.APPROVED_NOT_EXECUTED;
        String receiptMessage = status == ActionStatus.EXECUTING && message == null ? "Execution in progress under the same idempotency key" : message;
        return new ExecutionReceipt(actionId, runId, idempotencyKey, receiptStatus, claimedAt, executedAt, externalReference, receiptMessage);
    }
}
