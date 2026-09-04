package com.moveinsync.mobilitycopilot.metrics.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Inclusive date window. */
public record MetricWindow(LocalDate start, LocalDate end) {

    public MetricWindow {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("window start must not be after end");
        }
    }

    /** Seven days ending the day before as-of. */
    public static MetricWindow trailingWeek(LocalDate asOf) {
        return new MetricWindow(asOf.minusDays(7), asOf.minusDays(1));
    }

    /** Prior four complete weeks (28 days) ending the day before the window starts. */
    public static MetricWindow priorFourWeeks(MetricWindow current) {
        LocalDate end = current.start().minusDays(1);
        return new MetricWindow(end.minusDays(27), end);
    }

    public long days() {
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }
}
