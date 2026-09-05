package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionTarget;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Node 14 — deterministic brief renderer.
 *
 * No LLM required: derives both summary paragraphs entirely from verified claims and
 * the run context. Action proposals are constructed for each DIRECT verified claim.
 *
 * Operational summary — fact-level: metric name, value, window, tenant.
 * Leadership summary  — impact-level: qualified/verified claim count, degraded dimensions,
 *                        data version, key caveats from warnings.
 */
@Service
public final class BriefingActionAgentImpl implements BriefingActionAgent {

    @Override
    public DecisionBrief draft(RunContext context, VerificationResult verifiedEvidence) {
        List<VerifiedClaim> claims = verifiedEvidence.claims();
        List<String> warnings = verifiedEvidence.warnings();

        String operational = buildOperationalSummary(context, claims, verifiedEvidence.status());
        String leadership  = buildLeadershipSummary(context, claims, warnings, verifiedEvidence.status());
        List<ActionProposal> proposals = buildProposals(context, claims);
        List<String> caveats = extractCaveats(warnings);

        return new DecisionBrief(context, operational, leadership, verifiedEvidence, proposals, caveats);
    }

    // -------------------------------------------------------------------------

    private String buildOperationalSummary(RunContext context,
                                            List<VerifiedClaim> claims,
                                            VerificationResult.Status status) {
        if (claims.isEmpty()) {
            return String.format(
                    "No verified metrics available for tenant %s (data version %s). " +
                    "Verification status: %s. Review evidence warnings before acting.",
                    context.tenant().businessUnit(), context.versions().data(), status);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Verified metrics for %s (data v%s, metrics v%s):\n",
                context.tenant().businessUnit(),
                context.versions().data(),
                context.versions().metrics()));

        for (VerifiedClaim claim : claims) {
            sb.append("  • ").append(claim.text()).append("\n");
        }

        if (status == VerificationResult.Status.QUALIFIED) {
            sb.append("Note: one or more claims were rejected during review — see caveats.");
        }
        return sb.toString().trim();
    }

    private String buildLeadershipSummary(RunContext context,
                                           List<VerifiedClaim> claims,
                                           List<String> warnings,
                                           VerificationResult.Status status) {
        long directCount = claims.stream()
                .filter(c -> c.kind() == VerifiedClaim.Kind.DIRECT)
                .count();
        long inferredCount = claims.size() - directCount;

        String statusPhrase = switch (status) {
            case VERIFIED  -> "All claims fully verified.";
            case QUALIFIED -> "Some claims were qualified or removed; remaining claims are verified.";
            case REJECTED  -> "Verification failed — no claims survived review.";
        };

        long regimeChangeWarnings = warnings.stream()
                .filter(w -> w.contains("recording regime change") || w.contains("R3:"))
                .count();
        long vendorWarnings = warnings.stream()
                .filter(w -> w.contains("R1:") || w.contains("universal vendor"))
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Decision brief for %s — data version %s. %s\n",
                context.tenant().businessUnit(), context.versions().data(), statusPhrase));

        sb.append(String.format(
                "%d direct metric(s) verified; %d qualified inference(s).\n",
                directCount, inferredCount));

        if (regimeChangeWarnings > 0) {
            sb.append("Tracking regime change detected — alert volume reflects instrumentation change, not operational incident.\n");
        }
        if (vendorWarnings > 0) {
            sb.append("Cross-vendor degradation observed — single-vendor attribution requires further investigation.\n");
        }
        if (claims.isEmpty()) {
            sb.append("Insufficient verified evidence to support operational decisions at this time.");
        }
        return sb.toString().trim();
    }

    private List<ActionProposal> buildProposals(RunContext context, List<VerifiedClaim> claims) {
        List<ActionProposal> proposals = new ArrayList<>();
        for (VerifiedClaim claim : claims) {
            if (claim.kind() != VerifiedClaim.Kind.DIRECT) continue;

            ActionTarget target = new ActionTarget(claim.tenant(), Set.of(), Map.of());
            proposals.add(new ActionProposal(
                    UUID.randomUUID(),
                    context.runId(),
                    1L,
                    "METRIC_ALERT",
                    "Alert: " + claim.text(),
                    "Supported by verified evidence: " + claim.evidenceIds(),
                    "DRAFT",
                    target,
                    claim.dataVersion(),
                    claim.metricVersion(),
                    claim.evidenceIds(),
                    Map.of(),
                    Instant.now(),
                    Instant.now().plusSeconds(86400)));
        }
        return Collections.unmodifiableList(proposals);
    }

    private List<String> extractCaveats(List<String> warnings) {
        return warnings.stream()
                .filter(w -> w.startsWith("critic ") || w.startsWith("verify ") || w.contains("caveat")
                        || w.contains("partial") || w.contains("PARTIAL"))
                .toList();
    }
}
