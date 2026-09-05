package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgent;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Node 12 — Evidence Critic.
 *
 * Deterministic rules (no LLM required):
 *   R1 — Universal vendor blame: rejects any claim attributing delay to a single vendor
 *        when VendorWorker evidence carries the "universal vendor claim requires qualification" warning.
 *   R2 — Missing coverage caveat: any claim based on FeedbackWorker PARTIAL evidence
 *        must carry a coverage caveat or is flagged for correction.
 *   R3 — Regime-change escalation: any claim that escalates an operational incident
 *        when TrackingSafetyWorker evidence is PARTIAL (regime change detected) is rejected.
 *   R4 — Unsupported cost claim: rejects cost-per-km claims when CostBillingWorker
 *        evidence is PARTIAL (majority zero-km rows).
 *
 * Status rules:
 *   VERIFIED  — all claims pass
 *   QUALIFIED — some claims corrected/removed but at least one remains
 *   REJECTED  — no claims survive
 */
@Service
public final class EvidenceCriticAgentImpl implements EvidenceCriticAgent {

    @Override
    public VerificationResult review(RunContext context, InvestigationResult investigation) {
        List<MetricEvidence> evidence = investigation.evidence();
        List<String> warnings = new ArrayList<>(investigation.warnings());

        // Build candidate claims from evidence (one claim per AVAILABLE/PARTIAL evidence item)
        List<Claim> candidates = buildCandidateClaims(evidence);

        Set<String> rejectedIds = new LinkedHashSet<>();

        for (Claim claim : candidates) {
            List<MetricEvidence> supporting = evidence.stream()
                    .filter(e -> claim.evidenceIds().contains(e.evidenceId()))
                    .toList();

            // R1: universal vendor blame
            if (isUniversalVendorBlame(claim, supporting)) {
                rejectedIds.add(claim.claimId());
                warnings.add("critic R1: rejected claim=" + claim.claimId()
                        + " — single-vendor blame unsupported; all vendors degraded");
            }

            // R2: feedback coverage caveat missing
            else if (isFeedbackClaimWithoutCoverageCaveat(claim, supporting)) {
                rejectedIds.add(claim.claimId());
                warnings.add("critic R2: rejected claim=" + claim.claimId()
                        + " — feedback claim requires low-coverage caveat");
            }

            // R3: regime-change evidence used for operational escalation
            else if (isRegimeChangeEscalation(claim, supporting)) {
                rejectedIds.add(claim.claimId());
                warnings.add("critic R3: rejected claim=" + claim.claimId()
                        + " — operational escalation not supported when tracking evidence shows regime change");
            }

            // R4: unsupported cost-per-km claim
            else if (isUnsupportedCostPerKmClaim(claim, supporting)) {
                rejectedIds.add(claim.claimId());
                warnings.add("critic R4: rejected claim=" + claim.claimId()
                        + " — cost-per-km claim not supported when majority of trips have zero km");
            }
        }

        List<VerifiedClaim> accepted = candidates.stream()
                .filter(c -> !rejectedIds.contains(c.claimId()))
                .map(c -> new VerifiedClaim(
                        c.claimId(), context.tenant(),
                        context.versions().data(), context.versions().metrics(),
                        c.text(), c.evidenceIds(), VerifiedClaim.Kind.DIRECT))
                .toList();

        VerificationResult.Status status;
        if (candidates.isEmpty() || accepted.isEmpty()) {
            status = rejectedIds.isEmpty()
                    ? VerificationResult.Status.QUALIFIED  // no claims to make
                    : VerificationResult.Status.REJECTED;
        } else if (!rejectedIds.isEmpty()) {
            status = VerificationResult.Status.QUALIFIED;
        } else {
            status = VerificationResult.Status.VERIFIED;
        }

        return new VerificationResult(status, accepted, rejectedIds, List.copyOf(warnings));
    }

    // -------------------------------------------------------------------------
    // Claim builder — one candidate claim per non-UNAVAILABLE evidence item
    // -------------------------------------------------------------------------

    private List<Claim> buildCandidateClaims(List<MetricEvidence> evidence) {
        List<Claim> claims = new ArrayList<>();
        for (MetricEvidence e : evidence) {
            if (MetricStatus.UNAVAILABLE.equals(e.status())) continue;
            String text = e.request().metricId().name() + " = " + e.value()
                    + " " + e.unit().name()
                    + " for " + e.request().tenant().businessUnit()
                    + " period " + e.request().window().start() + "/" + e.request().window().end();
            claims.add(new Claim(
                    "claim-" + e.evidenceId(),
                    text,
                    Set.of(e.evidenceId()),
                    VerifiedClaim.Kind.DIRECT));
        }
        return claims;
    }

    // -------------------------------------------------------------------------
    // Rule predicates
    // -------------------------------------------------------------------------

    private boolean isUniversalVendorBlame(Claim claim, List<MetricEvidence> supporting) {
        return supporting.stream().anyMatch(e ->
                e.warnings().stream().anyMatch(w ->
                        w.contains("universal vendor claim requires qualification")));
    }

    private boolean isFeedbackClaimWithoutCoverageCaveat(Claim claim, List<MetricEvidence> supporting) {
        return supporting.stream().anyMatch(e ->
                MetricStatus.PARTIAL.equals(e.status())
                && e.warnings().stream().anyMatch(w -> w.contains("low feedback coverage"))
                && e.warnings().stream().noneMatch(w -> w.contains("coverage")));
    }

    private boolean isRegimeChangeEscalation(Claim claim, List<MetricEvidence> supporting) {
        return supporting.stream().anyMatch(e ->
                MetricStatus.PARTIAL.equals(e.status())
                && e.warnings().stream().anyMatch(w -> w.contains("recording regime change")));
    }

    private boolean isUnsupportedCostPerKmClaim(Claim claim, List<MetricEvidence> supporting) {
        return supporting.stream().anyMatch(e ->
                MetricStatus.PARTIAL.equals(e.status())
                && e.warnings().stream().anyMatch(w -> w.contains("cost_per_km unsupported")));
    }
}
