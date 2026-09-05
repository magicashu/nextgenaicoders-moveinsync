package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionTarget;
import com.moveinsync.mobilitycopilot.action.domain.AllowedActionPolicy;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Creates a draft only. Approval, fresh revalidation, execution and audit stay in WS3.
 */
public final class ActionProposalDraftFactory {
    private final Clock clock;

    public ActionProposalDraftFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public ActionProposal draft(
            RunContext context,
            AllowedActionPolicy policy,
            String type,
            String title,
            String rationale,
            ActionTarget target,
            Set<VerifiedClaim> claims) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(policy, "policy is required");
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(claims, "claims are required");
        requireText(type, "type");
        requireText(title, "title");
        requireText(rationale, "rationale");

        if (!policy.mockExecutionOnly() || policy.externalCommunicationAllowed()) {
            throw new IllegalArgumentException("only internal mock actions may be drafted");
        }
        if (!policy.allowedTypes().contains(type)) {
            throw new IllegalArgumentException("action type is not allowed by policy");
        }
        if (target.tenant() == null || !context.tenant().equals(target.tenant())) {
            throw new IllegalArgumentException("action target must use the run tenant");
        }
        if (!policy.allowedDimensionKeys().containsAll(target.dimensions().keySet())) {
            throw new IllegalArgumentException("action target contains an unallowlisted dimension");
        }
        if(target.trips()==null||target.trips().stream().anyMatch(trip->trip==null||!context.tenant().equals(trip.tenant())))
            throw new IllegalArgumentException("Every target trip must belong to the authorized tenant");
        if (claims.isEmpty()) {
            throw new IllegalArgumentException("at least one verified claim is required");
        }

        var evidenceIds = new java.util.LinkedHashSet<String>();
        for (var claim : claims) {
            if (claim == null || claim.evidenceIds() == null || claim.evidenceIds().isEmpty()) {
                throw new IllegalArgumentException("every draft claim requires evidence");
            }
            if (!context.tenant().equals(claim.tenant())
                    || !context.versions().data().equals(claim.dataVersion())
                    || !context.versions().metrics().equals(claim.metricVersion())) {
                throw new IllegalArgumentException("draft claims must match the run scope and versions");
            }
            evidenceIds.addAll(claim.evidenceIds());
        }

        var createdAt = Instant.now(clock);
        return new ActionProposal(
                UUID.randomUUID(),
                context.runId(),
                1,
                type,
                title,
                rationale,
                "DRAFT",
                target,
                context.versions().data(),
                context.versions().metrics(),
                Set.copyOf(evidenceIds),
                Map.of(),
                createdAt,
                createdAt.plus(policy.proposalLifetime()));
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
