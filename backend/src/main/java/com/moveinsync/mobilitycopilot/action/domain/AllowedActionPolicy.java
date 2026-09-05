package com.moveinsync.mobilitycopilot.action.domain;

import java.time.Duration;
import java.util.Set;

/** WS3-owned policy input; Agent 4 can draft only within this allowlist. */
public record AllowedActionPolicy(
        String policyVersion,
        Set<String> allowedTypes,
        Set<String> allowedDimensionKeys,
        Duration proposalLifetime,
        boolean externalCommunicationAllowed,
        boolean mockExecutionOnly) {

    public AllowedActionPolicy {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion is required");
        }
        if (allowedTypes == null || allowedDimensionKeys == null || proposalLifetime == null) {
            throw new IllegalArgumentException("action policy collections and lifetime are required");
        }
        if (proposalLifetime.isZero() || proposalLifetime.isNegative()) {
            throw new IllegalArgumentException("proposalLifetime must be positive");
        }
    }
}
