package com.moveinsync.mobilitycopilot.anomaly.domain;

import java.math.BigDecimal;

/**
 * Profiled materiality rules (architecture plan section 10): a material rate issue needs at least
 * 300 trips, an absolute gap of 3 percentage points and a relative rise of 25% against the prior
 * four complete weeks; any Sev-1/2 alert-rate doubling is material.
 */
public record AnomalyThresholds(
        long minimumVolume,
        BigDecimal absolutePointGap,
        BigDecimal relativeRise,
        BigDecimal severeAlertMultiplier,
        BigDecimal highSeverityRelativeRise,
        String ruleVersion) {

    public static final AnomalyThresholds DEFAULT = new AnomalyThresholds(
            300, new BigDecimal("3.00"), new BigDecimal("0.25"), new BigDecimal("2.0"), new BigDecimal("0.50"), "anomaly-rules-v1");
}
