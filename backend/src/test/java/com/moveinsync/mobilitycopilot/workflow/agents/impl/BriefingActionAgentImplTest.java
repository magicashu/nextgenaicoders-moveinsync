package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.RunVersions;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BriefingActionAgentImplTest {

    private final TenantContext tenant = new TenantContext("pinnacle-Slc");
    private final RunContext context = new RunContext(
            UUID.randomUUID(),
            new ActorContext("actor-1", Set.of("TRANSPORT_MANAGER"), Set.of(tenant)),
            tenant,
            "TRANSPORT_MANAGER",
            LocalDate.of(2026, 7, 31),
            new RunVersions("data-v1", "metrics-v1", "workflow-v1", "prompts-v1", "model-v1", "config-v1"),
            new WorkflowBudget(12, 4, 1, Duration.ofSeconds(10), 4),
            Instant.parse("2026-09-05T12:00:00Z"));

    @Test
    void rendersTheSameVerifiedClaimsForBothAudiencesAndKeepsCaveats() {
        var direct = new VerifiedClaim(
                "claim-direct",
                tenant,
                "data-v1",
                "metrics-v1",
                "The delayed-trip rate increased in the selected site and shift.",
                Set.of("evidence-2"),
                VerifiedClaim.Kind.DIRECT);
        var qualified = new VerifiedClaim(
                "claim-qualified",
                tenant,
                "data-v1",
                "metrics-v1",
                "The concentration is a qualified inference, not a root-cause finding.",
                Set.of("evidence-1"),
                VerifiedClaim.Kind.QUALIFIED_INFERENCE);

        var brief = new BriefingActionAgentImpl().draft(
                context,
                new VerificationResult(
                        VerificationResult.Status.QUALIFIED,
                        List.of(qualified, direct),
                        Set.of(),
                        List.of("Feedback coverage is limited.")));

        assertThat(brief.operationalSummary()).contains(direct.text(), qualified.text(), "Feedback coverage is limited.");
        assertThat(brief.operationalSummary()).contains("evidence-1", "evidence-2");
        assertThat(brief.leadershipSummary()).contains(direct.text(), qualified.text(), "Feedback coverage is limited.");
        assertThat(brief.proposedActions()).isEmpty();
        assertThat(brief.caveats()).contains("Feedback coverage is limited.");
    }

    @Test
    void suppressesForeignVersionClaimsAndNeverCreatesAnAction() {
        var foreign = new VerifiedClaim(
                "foreign-claim",
                new TenantContext("other-tenant"),
                "data-v2",
                "metrics-v2",
                "This claim must not cross the run boundary.",
                Set.of("foreign-evidence"),
                VerifiedClaim.Kind.DIRECT);

        var brief = new BriefingActionAgentImpl().draft(
                context,
                new VerificationResult(VerificationResult.Status.VERIFIED, List.of(foreign), Set.of(), List.of()));

        assertThat(brief.operationalSummary()).contains("No verified findings are available");
        assertThat(brief.operationalSummary()).doesNotContain(foreign.text());
        assertThat(brief.caveats()).anyMatch(caveat -> caveat.contains("foreign-claim"));
        assertThat(brief.proposedActions()).isEmpty();
    }
}
