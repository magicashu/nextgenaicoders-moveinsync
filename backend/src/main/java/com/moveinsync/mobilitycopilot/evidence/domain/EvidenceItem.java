package com.moveinsync.mobilitycopilot.evidence.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record EvidenceItem(
        String evidenceId,
        String metricId,
        BigDecimal value,
        String unit,
        BigDecimal baselineValue,
        BigDecimal delta,
        BigDecimal numerator,
        BigDecimal denominator,
        long supportingCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, String> filters,
        String source,
        String contractVersion,
        String dataVersion) {
}
