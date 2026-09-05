package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionTarget;
import com.moveinsync.mobilitycopilot.action.domain.AllowedActionPolicy;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.RunVersions;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionProposalDraftFactoryTest {

    @Test
    void createsAnExpiringDraftWithVerifiedEvidenceAndTenantScope() {
        var tenant = new TenantContext("pinnacle-Slc");
        var context = context(tenant);
        var claim = new VerifiedClaim(
                "claim-1", tenant, "data-v1", "metrics-v1", "Verified finding", Set.of("evidence-1"),
                VerifiedClaim.Kind.DIRECT);
        var policy = new AllowedActionPolicy(
                "policy-v1", Set.of("REVIEW_SITE"), Set.of("site_id"), Duration.ofMinutes(30), false, true);

        var proposal = new ActionProposalDraftFactory(Clock.fixed(
                Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC)).draft(
                context,
                policy,
                "REVIEW_SITE",
                "Review selected site",
                "Review the verified finding.",
                new ActionTarget(tenant, Set.of(), Map.of("site_id", "site-1")),
                Set.of(claim));

        assertThat(proposal.status()).isEqualTo("DRAFT");
        assertThat(proposal.target().tenant()).isEqualTo(tenant);
        assertThat(proposal.evidenceIds()).containsExactly("evidence-1");
        assertThat(proposal.expiresAt()).isEqualTo(Instant.parse("2026-09-05T12:30:00Z"));
    }

    @Test
    void rejectsForeignTenantTargets() {
        var tenant = new TenantContext("pinnacle-Slc");
        var policy = new AllowedActionPolicy(
                "policy-v1", Set.of("REVIEW_SITE"), Set.of("site_id"), Duration.ofMinutes(30), false, true);
        var claim = new VerifiedClaim(
                "claim-1", tenant, "data-v1", "metrics-v1", "Verified finding", Set.of("evidence-1"),
                VerifiedClaim.Kind.DIRECT);

        assertThatThrownBy(() -> new ActionProposalDraftFactory(Clock.systemUTC()).draft(
                context(tenant),
                policy,
                "REVIEW_SITE",
                "Review selected site",
                "Review the verified finding.",
                new ActionTarget(new TenantContext("other-tenant"), Set.of(), Map.of("site_id", "site-1")),
                Set.of(claim)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("run tenant");
    }

    private RunContext context(TenantContext tenant) {
        return new RunContext(
                UUID.randomUUID(),
                new ActorContext("actor-1", Set.of("TRANSPORT_MANAGER"), Set.of(tenant)),
                tenant,
                "TRANSPORT_MANAGER",
                LocalDate.of(2026, 7, 31),
                new RunVersions("data-v1", "metrics-v1", "workflow-v1", "prompts-v1", "model-v1", "config-v1"),
                new WorkflowBudget(12, 4, 1, Duration.ofSeconds(10), 4),
                Instant.parse("2026-09-05T12:00:00Z"));
    }
}
