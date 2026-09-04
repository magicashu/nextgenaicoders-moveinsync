package com.moveinsync.mobilitycopilot.metrics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPoint(LocalDate date, long numerator, long denominator, BigDecimal value) {
}
