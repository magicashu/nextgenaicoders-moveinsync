package com.moveinsync.mobilitycopilot.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.RunVersions;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicEvidenceVerifierTest {
    private final DeterministicEvidenceVerifier verifier = new DeterministicEvidenceVerifier();

    @Test
    void verifies_a_supported_scoped_factual_claim() {
        VerificationResult result = verifier.verify(context(), List.of(claim("C001", "Delayed trips increased.")),
                List.of(evidence("E001", tenant(), MetricStatus.AVAILABLE, "data-v1", "metrics-v1")));

        assertThat(result.status()).isEqualTo(VerificationResult.Status.VERIFIED);
        assertThat(result.claims()).extracting(VerifiedClaim::claimId).containsExactly("C001");
        assertThat(result.rejectedClaimIds()).isEmpty();
    }

    @Test
    void rejects_unsupported_single_vendor_causality_even_when_vendor_metric_exists() {
        VerificationResult result = verifier.verify(context(),
                List.of(claim("C001", "Vendor ABC caused the increase in delays.")),
                List.of(evidence("E001", tenant(), MetricStatus.AVAILABLE, "data-v1", "metrics-v1")));

        assertThat(result.status()).isEqualTo(VerificationResult.Status.REJECTED);
        assertThat(result.rejectedClaimIds()).containsExactly("C001");
    }

    @Test
    void rejects_missing_references_and_tenant_or_version_mismatches() {
        VerificationResult missing = verifier.verify(context(), List.of(claim("C001", "Unsupported.")), List.of());
        VerificationResult wrongTenant = verifier.verify(context(), List.of(claim("C001", "Wrong tenant.")),
                List.of(evidence("E001", new TenantContext("other"), MetricStatus.AVAILABLE, "data-v1", "metrics-v1")));
        VerificationResult wrongVersion = verifier.verify(context(), List.of(claim("C001", "Wrong version.")),
                List.of(evidence("E001", tenant(), MetricStatus.AVAILABLE, "data-v2", "metrics-v1")));

        assertThat(missing.status()).isEqualTo(VerificationResult.Status.REJECTED);
        assertThat(wrongTenant.status()).isEqualTo(VerificationResult.Status.REJECTED);
        assertThat(wrongVersion.status()).isEqualTo(VerificationResult.Status.REJECTED);
    }

    @Test
    void qualifies_partial_evidence_instead_of_treating_it_as_complete() {
        VerificationResult result = verifier.verify(context(), List.of(claim("C001", "Feedback worsened.")),
                List.of(evidence("E001", tenant(), MetricStatus.PARTIAL, "data-v1", "metrics-v1")));

        assertThat(result.status()).isEqualTo(VerificationResult.Status.QUALIFIED);
        assertThat(result.claims().getFirst().kind()).isEqualTo(VerifiedClaim.Kind.QUALIFIED_INFERENCE);
    }

    private static Claim claim(String id, String text) {
        return new Claim(id, text, Set.of("E001"), VerifiedClaim.Kind.DIRECT);
    }

    private static MetricEvidence evidence(String id, TenantContext tenant, MetricStatus status,
                                           String dataVersion, String metricVersion) {
        return new MetricEvidence(id, new MetricRequest(tenant, MetricId.M01_DELAYED_TRIP_RATE,
                MetricRequest.Measure.VALUE, new MetricWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)),
                Map.of(), dataVersion), status, new BigDecimal("0.219"), MetricUnit.PERCENT,
                new BigDecimal("4357"), new BigDecimal("19913"), 19913, metricVersion, "M01", List.of());
    }

    private static RunContext context() {
        TenantContext tenant = tenant();
        return new RunContext(UUID.randomUUID(), new ActorContext("tester", Set.of("TRANSPORT_MANAGER"), Set.of(tenant)),
                tenant, "TRANSPORT_MANAGER", LocalDate.of(2026, 6, 8),
                new RunVersions("data-v1", "metrics-v1", "workflow-v1", "prompt-v1", "none", "config-v1"),
                new WorkflowBudget(12, 4, 1, Duration.ofSeconds(10), 4), Instant.now().plusSeconds(30));
    }

    private static TenantContext tenant() { return new TenantContext("pinnacle-Slc"); }
}
