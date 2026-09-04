package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MetricResult(
        MetricId metricId,
        String metricName,
        BigDecimal valuePercent,
        BigDecimal baselinePercent,
        BigDecimal deltaPercentagePoints,
        long numerator,
        long denominator,
        LocalDate periodStart,
        LocalDate periodEnd,
        String contractVersion,
        String dataVersion) {
}
