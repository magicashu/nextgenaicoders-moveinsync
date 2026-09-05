package com.moveinsync.mobilitycopilot.metrics.domain;

import java.util.List;
import java.util.Objects;

/** Per-tenant availability of one analysis (S supported, D derivable with caveat, U unsupported). */
public record CapabilityStatus(
        String analysis,
        Support support,
        String reason,
        List<MetricId> metrics) {

    public enum Support { SUPPORTED, DERIVABLE, UNSUPPORTED }

    public CapabilityStatus {
        Objects.requireNonNull(analysis);
        Objects.requireNonNull(support);
        Objects.requireNonNull(reason);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
