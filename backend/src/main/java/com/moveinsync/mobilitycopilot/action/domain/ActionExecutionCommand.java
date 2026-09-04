package com.moveinsync.mobilitycopilot.action.domain;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

import java.time.Instant;
import java.util.Objects;

public record ActionExecutionCommand(
        ActorContext actor,
        TenantContext tenant,
        ActionProposal proposal,
        String idempotencyKey,
        String expectedEvidenceVersion,
        Instant requestedAt) {

    public ActionExecutionCommand {
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(tenant, "tenant is required");
        Objects.requireNonNull(proposal, "proposal is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(expectedEvidenceVersion, "expectedEvidenceVersion is required");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        if (!actor.businessUnit().equals(tenant.businessUnit())) {
            throw new IllegalArgumentException("actor and tenant business units must match");
        }
        if (!proposal.evidenceVersion().equals(expectedEvidenceVersion)) {
            throw new IllegalArgumentException("proposal evidence version must match expected version");
        }
    }
}
