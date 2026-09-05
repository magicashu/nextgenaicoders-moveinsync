package com.moveinsync.mobilitycopilot.evidence.domain;

import java.math.BigDecimal;

public record EvidenceItem(
        String evidenceId,
        String metricId,
        BigDecimal valuePercent,
        BigDecimal baselinePercent,
        long numerator,
        long denominator,
        String source,
        String contractVersion,
        String dataVersion) {
}
