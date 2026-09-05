package com.moveinsync.mobilitycopilot.evidence.domain;

import java.util.List;
import java.util.Objects;

/**
 * Structured but untrusted semantic review. It is deliberately separate from
 * {@link VerificationResult}: only the deterministic verifier can verify a claim.
 */
public record CritiqueResult(Status overallStatus, List<ClaimReview> claims,
                             List<String> globalCaveats) {
    public CritiqueResult {
        Objects.requireNonNull(overallStatus, "overallStatus is required");
        claims = List.copyOf(claims == null ? List.of() : claims);
        globalCaveats = List.copyOf(globalCaveats == null ? List.of() : globalCaveats);
    }

    public enum Status { ACCEPTABLE, NEEDS_CORRECTION, INSUFFICIENT_EVIDENCE }

    public record ClaimReview(String claimId, Decision decision, List<CritiqueIssue> issues,
                              List<String> requiredCaveats) {
        public ClaimReview {
            Objects.requireNonNull(claimId, "claimId is required");
            Objects.requireNonNull(decision, "decision is required");
            issues = List.copyOf(issues == null ? List.of() : issues);
            requiredCaveats = List.copyOf(requiredCaveats == null ? List.of() : requiredCaveats);
        }
    }

    public enum Decision { ACCEPT, QUALIFY, REJECT }
}
