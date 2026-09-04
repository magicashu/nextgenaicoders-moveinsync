package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;

/** One category of a governed distribution (for example a delay reason). */
public record DistributionRow(String category, long count, BigDecimal share, long baselineCount, BigDecimal baselineShare) {
}
