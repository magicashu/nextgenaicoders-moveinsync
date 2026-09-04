package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MetricResult(
        MetricId metricId,
        String metricName,
        MetricUnit unit,
        MetricStatus status,
        BigDecimal value,
        BigDecimal baselineValue,
        BigDecimal delta,
        BigDecimal numerator,
        BigDecimal denominator,
        long supportingCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, String> filters,
        String contractVersion,
        String dataVersion,
        String source,
        List<String> caveats) {

    public MetricResult {
        Objects.requireNonNull(metricId, "metricId is required");
        Objects.requireNonNull(metricName, "metricName is required");
        Objects.requireNonNull(unit, "unit is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(periodStart, "periodStart is required");
        Objects.requireNonNull(periodEnd, "periodEnd is required");
        Objects.requireNonNull(contractVersion, "contractVersion is required");
        Objects.requireNonNull(dataVersion, "dataVersion is required");
        Objects.requireNonNull(source, "source is required");
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must not be after periodEnd");
        }
        if (supportingCount < 0) {
            throw new IllegalArgumentException("supportingCount must be non-negative");
        }
        if (status == MetricStatus.SUPPORTED && value == null) {
            throw new IllegalArgumentException("supported metrics require a value");
        }
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
        if (status == MetricStatus.UNSUPPORTED && caveats.isEmpty()) {
            throw new IllegalArgumentException("unsupported metrics require a caveat");
        }
    }
}
