package com.moveinsync.mobilitycopilot.evidence.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Deterministic verifier output (node 13). */
public record VerificationResult(
        boolean passed,
        List<Violation> violations,
        List<String> removedClaimIds,
        BigDecimal confidence,
        List<String> confidenceComponents) {

    public record Violation(String code, String claimId, String message) {
    }

    public VerificationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        removedClaimIds = removedClaimIds == null ? List.of() : List.copyOf(removedClaimIds);
        confidenceComponents = confidenceComponents == null ? List.of() : List.copyOf(confidenceComponents);
        Objects.requireNonNull(confidence);
    }

    public boolean hasBlocking() {
        return violations.stream().anyMatch(v -> !v.code().startsWith("WARN"));
    }
}
