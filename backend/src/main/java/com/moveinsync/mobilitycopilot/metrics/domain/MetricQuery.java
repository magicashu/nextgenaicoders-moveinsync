package com.moveinsync.mobilitycopilot.metrics.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public record MetricQuery(
        TenantContext tenant,
        MetricId metricId,
        LocalDate currentStart,
        LocalDate currentEnd,
        LocalDate baselineStart,
        LocalDate baselineEnd,
        Map<String, String> dimensions) {

    public MetricQuery {
        Objects.requireNonNull(tenant, "tenant is required");
        Objects.requireNonNull(metricId, "metricId is required");
        Objects.requireNonNull(currentStart, "currentStart is required");
        Objects.requireNonNull(currentEnd, "currentEnd is required");
        Objects.requireNonNull(baselineStart, "baselineStart is required");
        Objects.requireNonNull(baselineEnd, "baselineEnd is required");
        if (currentStart.isAfter(currentEnd) || baselineStart.isAfter(baselineEnd)) {
            throw new IllegalArgumentException("metric windows must start on or before they end");
        }
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }

    public static MetricQuery trailingWeekWithFourWeekBaseline(
            TenantContext tenant,
            MetricId metricId,
            LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate is required");
        LocalDate currentStart = asOfDate.minusDays(7);
        LocalDate currentEnd = asOfDate.minusDays(1);
        LocalDate baselineEnd = currentStart.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(27);
        return new MetricQuery(
                tenant,
                metricId,
                currentStart,
                currentEnd,
                baselineStart,
                baselineEnd,
                Map.of());
    }
}
