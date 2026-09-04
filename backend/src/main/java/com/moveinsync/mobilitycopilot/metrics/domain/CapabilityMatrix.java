package com.moveinsync.mobilitycopilot.metrics.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CapabilityMatrix(String businessUnit, String dataVersion, List<CapabilityStatus> statuses) {

    public CapabilityMatrix {
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(dataVersion);
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }

    public Optional<CapabilityStatus> forMetric(MetricId metricId) {
        return statuses.stream().filter(s -> s.metrics().contains(metricId)).findFirst();
    }

    public boolean isUnsupported(MetricId metricId) {
        return forMetric(metricId).map(s -> s.support() == CapabilityStatus.Support.UNSUPPORTED).orElse(false);
    }
}
