package com.moveinsync.mobilitycopilot.approval.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Request plus its current status and (optional) decision, as persisted. */
public record ApprovalRecord(ApprovalRequest request, ApprovalStatus status, ApprovalDecision decision, Instant updatedAt) {

    public ApprovalRecord {
        Objects.requireNonNull(request);
        Objects.requireNonNull(status);
        Objects.requireNonNull(updatedAt);
    }

    public Optional<ApprovalDecision> decisionOptional() {
        return Optional.ofNullable(decision);
    }

    public boolean pendingAt(Instant now) {
        return status == ApprovalStatus.PENDING && request.expiresAt().isAfter(now);
    }
}
