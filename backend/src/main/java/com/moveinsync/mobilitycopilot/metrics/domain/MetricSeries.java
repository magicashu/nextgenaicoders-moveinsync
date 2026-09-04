package com.moveinsync.mobilitycopilot.metrics.domain;

import java.util.List;
import java.util.Objects;

/** Daily snapshot series for one metric, read from the precomputed snapshot tables. */
public record MetricSeries(
        String businessUnit,
        MetricId metricId,
        MetricUnit unit,
        MetricWindow window,
        List<DailyPoint> points,
        String contractVersion,
        String dataVersion,
        String source) {

    public MetricSeries {
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(metricId);
        points = points == null ? List.of() : List.copyOf(points);
    }
}
