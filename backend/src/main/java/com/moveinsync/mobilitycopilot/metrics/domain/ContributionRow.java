package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** One dimension member inside a contribution ranking. */
public record ContributionRow(
        String member,
        BigDecimal currentValue,
        BigDecimal baselineValue,
        BigDecimal delta,
        long currentNumerator,
        long currentDenominator,
        long baselineNumerator,
        long baselineDenominator,
        BigDecimal shareOfCurrentNumerator,
        boolean qualified) {

    public ContributionRow {
        Objects.requireNonNull(member, "member is required");
    }
}
