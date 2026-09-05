package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Tenant-configured target. Never presented as organizer-supplied (D-031). */
public record ConfiguredTarget(
        MetricId metricId,
        BigDecimal value,
        boolean lowerIsBetter,
        String label,
        String targetVersion) {

    public static final String LABEL = "Configured target, editable per tenant";

    public ConfiguredTarget {
        Objects.requireNonNull(metricId);
        Objects.requireNonNull(value);
        Objects.requireNonNull(label);
        Objects.requireNonNull(targetVersion);
    }

    public boolean isMet(BigDecimal actual) {
        int cmp = actual.compareTo(value);
        return lowerIsBetter ? cmp <= 0 : cmp >= 0;
    }
}
