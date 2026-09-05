package com.moveinsync.mobilitycopilot.workflow.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.application.DeterministicEvidenceVerifier;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.RunVersions;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceCriticAgentImplTest {
    @Test
    void model_critique_is_recorded_but_cannot_override_deterministic_verification() {
        LanguageModelPort port = request -> new LanguageModelPort.ModelResponse("gemma3:4b", """
                {"overallStatus":"NEEDS_CORRECTION","claims":[{"claimId":"C001","decision":"QUALIFY",
                "issues":[{"type":"MISSING_CAVEAT","severity":"HIGH","explanation":"Coverage is limited."}],
                "requiredCaveats":["Coverage is limited."]}],"globalCaveats":[]}
                """, 1, 1, 1);
        EvidenceCriticAgent agent = new EvidenceCriticAgentImpl(new DeterministicEvidenceVerifier(), Optional.of(port),
                new ObjectMapper().findAndRegisterModules());

        VerificationResult result = agent.review(context(), List.of(new Claim("C001", "Delay increased.",
                Set.of("E001"), VerifiedClaim.Kind.DIRECT)), investigation());

        assertThat(result.claims()).extracting(VerifiedClaim::claimId).containsExactly("C001");
        assertThat(result.status()).isEqualTo(VerificationResult.Status.QUALIFIED);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("MISSING_CAVEAT"));
    }

    @Test
    void provider_failure_leaves_the_safe_deterministic_result_visible() {
        EvidenceCriticAgent agent = new EvidenceCriticAgentImpl(new DeterministicEvidenceVerifier(),
                Optional.of(request -> { throw new IllegalStateException("offline"); }),
                new ObjectMapper().findAndRegisterModules());

        VerificationResult result = agent.review(context(), List.of(new Claim("C001", "Delay increased.",
                Set.of("E001"), VerifiedClaim.Kind.DIRECT)), investigation());

        assertThat(result.claims()).hasSize(1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Semantic critique unavailable"));
    }

    private static InvestigationResult investigation() {
        TenantContext tenant = new TenantContext("pinnacle-Slc");
        MetricEvidence evidence = new MetricEvidence("E001", new MetricRequest(tenant, MetricId.M01_DELAYED_TRIP_RATE,
                MetricRequest.Measure.VALUE, new MetricWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)),
                Map.of(), "data-v1"), MetricStatus.AVAILABLE, new BigDecimal("0.219"), MetricUnit.PERCENT,
                new BigDecimal("4357"), new BigDecimal("19913"), 19913, "metrics-v1", "M01", List.of());
        return new InvestigationResult(List.of(evidence), List.of(), List.of(), List.of());
    }

    private static RunContext context() {
        TenantContext tenant = new TenantContext("pinnacle-Slc");
        return new RunContext(UUID.randomUUID(), new ActorContext("tester", Set.of("TRANSPORT_MANAGER"), Set.of(tenant)),
                tenant, "TRANSPORT_MANAGER", LocalDate.of(2026, 6, 8),
                new RunVersions("data-v1", "metrics-v1", "workflow-v1", "prompt-v1", "none", "config-v1"),
                new WorkflowBudget(12, 4, 1, Duration.ofSeconds(10), 4), Instant.now().plusSeconds(30));
    }
}
