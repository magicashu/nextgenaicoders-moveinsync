package com.moveinsync.mobilitycopilot.approval.domain;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ApprovalDecision(
        UUID approvalId,
        UUID actionId,
        UUID runId,
        ApprovalDecisionType decision,
        String decidedBy,
        Instant decidedAt,
        String comment,
        ActionProposal editedProposal) {

    public ApprovalDecision {
        Objects.requireNonNull(approvalId, "approvalId is required");
        Objects.requireNonNull(actionId, "actionId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(decision, "decision is required");
        Objects.requireNonNull(decidedBy, "decidedBy is required");
        Objects.requireNonNull(decidedAt, "decidedAt is required");
        if (decidedBy.isBlank()) {
            throw new IllegalArgumentException("decidedBy must not be blank");
        }
        if (decision == ApprovalDecisionType.EDIT && editedProposal == null) {
            throw new IllegalArgumentException("EDIT requires editedProposal");
        }
        if (decision != ApprovalDecisionType.EDIT && editedProposal != null) {
            throw new IllegalArgumentException("editedProposal is allowed only for EDIT");
        }
    }
}
