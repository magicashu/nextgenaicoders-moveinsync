package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic Agent 4 implementation. It composes verified claims only;
 * policy and action execution remain outside this class.
 */
@Service
public final class BriefingActionAgentImpl implements BriefingActionAgent {

    @Override
    public DecisionBrief draft(RunContext context, VerificationResult verifiedEvidence) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(verifiedEvidence, "verifiedEvidence is required");

        var caveats = new ArrayList<String>();
        var claims = eligibleClaims(context, verifiedEvidence, caveats);
        var findings = renderFindings(claims);
        var status = verifiedEvidence.status().name();

        if (claims.isEmpty()) {
            findings = "No verified findings are available for this run.";
        }

        var operational = renderSummary("Operations decision brief", context, status, findings, caveats);
        var leadership = renderSummary("Leadership summary", context, status, findings, caveats);

        return new DecisionBrief(
                context,
                operational,
                leadership,
                verifiedEvidence,
                List.of(),
                List.copyOf(caveats));
    }

    private List<VerifiedClaim> eligibleClaims(
            RunContext context,
            VerificationResult verification,
            List<String> caveats) {
        if (verification.status() == VerificationResult.Status.REJECTED) {
            caveats.add("Verified evidence was rejected; no action draft was created.");
        }

        var rejected = verification.rejectedClaimIds() == null
                ? Set.<String>of()
                : verification.rejectedClaimIds();
        var seen = new LinkedHashSet<String>();
        var eligible = new ArrayList<VerifiedClaim>();

        if (verification.claims() == null) {
            caveats.add("The verification result contained no claim list.");
            return eligible;
        }

        for (var claim : verification.claims()) {
            if (claim == null || claim.claimId() == null || claim.claimId().isBlank()) {
                caveats.add("A verified claim without an identifier was suppressed.");
                continue;
            }
            if (!seen.add(claim.claimId())) {
                caveats.add("Duplicate claim " + claim.claimId() + " was suppressed.");
                continue;
            }
            if (rejected.contains(claim.claimId())) {
                caveats.add("Rejected claim " + claim.claimId() + " was suppressed.");
                continue;
            }
            if (claim.tenant() == null || !context.tenant().equals(claim.tenant())) {
                caveats.add("Claim " + claim.claimId() + " was suppressed because its tenant differs from the run scope.");
                continue;
            }
            if (!Objects.equals(context.versions().data(), claim.dataVersion())
                    || !Objects.equals(context.versions().metrics(), claim.metricVersion())) {
                caveats.add("Claim " + claim.claimId() + " was suppressed because its data or metric version differs from the run.");
                continue;
            }
            if (claim.text() == null || claim.text().isBlank()) {
                caveats.add("Claim " + claim.claimId() + " was suppressed because it has no approved wording.");
                continue;
            }
            if (claim.evidenceIds() == null || claim.evidenceIds().isEmpty()) {
                caveats.add("Claim " + claim.claimId() + " was suppressed because it has no evidence reference.");
                continue;
            }
            eligible.add(claim);
        }

        eligible.sort(Comparator
                .comparingInt((VerifiedClaim claim) -> claim.kind() == VerifiedClaim.Kind.DIRECT ? 0 : 1)
                .thenComparing(VerifiedClaim::claimId));

        if (verification.warnings() != null) {
            caveats.addAll(verification.warnings().stream()
                    .filter(Objects::nonNull)
                    .filter(warning -> !warning.isBlank())
                    .toList());
        }
        return eligible;
    }

    private String renderFindings(List<VerifiedClaim> claims) {
        return claims.stream()
                .map(claim -> "- " + claim.text() + " [evidence: " + evidenceIds(claim.evidenceIds()) + "]")
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String evidenceIds(Set<String> evidenceIds) {
        return evidenceIds.stream().sorted().collect(Collectors.joining(", "));
    }

    private String renderSummary(
            String heading,
            RunContext context,
            String verificationStatus,
            String findings,
            List<String> caveats) {
        var builder = new StringBuilder()
                .append(heading)
                .append(" | tenant=").append(context.tenant().businessUnit())
                .append(" | asOf=").append(context.asOfDate())
                .append(System.lineSeparator())
                .append("Verification status: ").append(verificationStatus)
                .append(System.lineSeparator())
                .append("Verified findings:").append(System.lineSeparator())
                .append(findings);

        if (!caveats.isEmpty()) {
            builder.append(System.lineSeparator()).append("Caveats:").append(System.lineSeparator());
            caveats.stream().distinct().forEach(caveat -> builder.append("- ").append(caveat).append(System.lineSeparator()));
        }
        builder.append("Draft actions require separate policy validation and human approval.");
        return builder.toString();
    }
}
