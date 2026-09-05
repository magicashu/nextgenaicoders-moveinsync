package com.moveinsync.mobilitycopilot.anomaly.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A step change confined to one alert type for one tenant (G3). Surfaced as a data-quality note,
 * never as an operational anomaly, and it can never create an action proposal.
 */
public record RegimeChangeFinding(
        String findingId,
        String businessUnit,
        String eventType,
        LocalDate beforeWeekStart,
        LocalDate afterWeekStart,
        long beforeWeeklyAverage,
        long afterWeeklyAverage,
        String direction,
        String note) {

    public RegimeChangeFinding {
        Objects.requireNonNull(findingId);
        Objects.requireNonNull(businessUnit);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(note);
    }
}
