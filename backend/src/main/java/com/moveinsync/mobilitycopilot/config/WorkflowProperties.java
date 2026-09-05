package com.moveinsync.mobilitycopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "mobility.workflow")
public record WorkflowProperties(
        int maxInvestigationSteps,
        int maxCorrectionCycles,
        int maxToolCalls,
        Duration toolTimeout,
        Duration approvalTtl) {

    public WorkflowProperties {
        if (maxInvestigationSteps < 1) {
            throw new IllegalArgumentException("maxInvestigationSteps must be positive");
        }
        if (maxCorrectionCycles < 0) {
            throw new IllegalArgumentException("maxCorrectionCycles must be non-negative");
        }
        if (maxToolCalls < 1) {
            throw new IllegalArgumentException("maxToolCalls must be positive");
        }
        Objects.requireNonNull(toolTimeout, "toolTimeout is required");
        Objects.requireNonNull(approvalTtl, "approvalTtl is required");
        if (toolTimeout.isZero() || toolTimeout.isNegative()) {
            throw new IllegalArgumentException("toolTimeout must be positive");
        }
        if (approvalTtl.isZero() || approvalTtl.isNegative()) {
            throw new IllegalArgumentException("approvalTtl must be positive");
        }
    }
}
