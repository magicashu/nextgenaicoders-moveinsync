package com.moveinsync.mobilitycopilot.approval.domain;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ApprovalRequest(
        UUID approvalId,
        UUID runId,
        String businessUnit,
        ActionProposal proposal,
        String evidenceVersion,
        Instant createdAt,
        Instant expiresAt) {

    public ApprovalRequest {
        Objects.requireNonNull(approvalId, "approvalId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(businessUnit, "businessUnit is required");
        Objects.requireNonNull(proposal, "proposal is required");
        Objects.requireNonNull(evidenceVersion, "evidenceVersion is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!proposal.runId().equals(runId)) {
            throw new IllegalArgumentException("proposal runId must match approval runId");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }
}
