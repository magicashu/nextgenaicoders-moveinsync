package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.anomaly.domain.RegimeChangeFinding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Classifies a step change confined to a single alert type as a data-regime change (G3 rule).
 * Input: weekly alert counts per event type for one tenant. A type whose weekly volume falls to at
 * most 5% of its prior two-week average (or appears from near zero) while it dominated the tenant's
 * alert volume is a configuration artefact, not an operations signal.
 */
public final class RegimeChangeDetector {

    static final double DROP_RATIO = 0.05;
    static final double DOMINANCE_SHARE = 0.50;
    static final long MINIMUM_WEEKLY_VOLUME = 100;

    public record WeeklyCount(String eventType, LocalDate weekStart, long alerts) {
    }

    public List<RegimeChangeFinding> detect(String businessUnit, List<WeeklyCount> weekly) {
        Map<String, TreeMap<LocalDate, Long>> byType = new TreeMap<>();
        TreeMap<LocalDate, Long> totals = new TreeMap<>();
        for (WeeklyCount w : weekly) {
            byType.computeIfAbsent(w.eventType(), k -> new TreeMap<>()).put(w.weekStart(), w.alerts());
            totals.merge(w.weekStart(), w.alerts(), Long::sum);
        }
        List<LocalDate> weeks = new ArrayList<>(totals.keySet());
        List<RegimeChangeFinding> findings = new ArrayList<>();
        for (var entry : byType.entrySet()) {
            String type = entry.getKey();
            TreeMap<LocalDate, Long> series = entry.getValue();
            for (int i = 2; i < weeks.size(); i++) {
                LocalDate w1 = weeks.get(i - 2);
                LocalDate w2 = weeks.get(i - 1);
                LocalDate w3 = weeks.get(i);
                long before = (series.getOrDefault(w1, 0L) + series.getOrDefault(w2, 0L)) / 2;
                long after = series.getOrDefault(w3, 0L);
                long totalBefore = (totals.getOrDefault(w1, 0L) + totals.getOrDefault(w2, 0L)) / 2;
                boolean dominant = totalBefore > 0 && before >= DOMINANCE_SHARE * totalBefore;
                boolean dropped = before >= MINIMUM_WEEKLY_VOLUME && after <= before * DROP_RATIO && dominant
                        && otherTypesStable(byType, type, w1, w2, w3);
                if (dropped) {
                    findings.add(new RegimeChangeFinding(
                            "%s:regime:%s:%s".formatted(businessUnit, type.toLowerCase(Locale.ROOT), w3),
                            businessUnit, type, w1, w3, before, after, "STEP_DOWN",
                            "%s alerts fell from about %d per week to %d in the week of %s while other alert types stayed stable. Classified as a data-regime change (alert configuration), not an operational issue. Excluded from alert-rate metrics and never escalated."
                                    .formatted(type, before, after, w3)));
                    break;
                }
            }
        }
        return findings;
    }

    private static boolean otherTypesStable(Map<String, TreeMap<LocalDate, Long>> byType, String type, LocalDate w1, LocalDate w2, LocalDate w3) {
        long othersBefore = 0;
        long othersAfter = 0;
        for (var e : byType.entrySet()) {
            if (e.getKey().equals(type)) {
                continue;
            }
            othersBefore += (e.getValue().getOrDefault(w1, 0L) + e.getValue().getOrDefault(w2, 0L)) / 2;
            othersAfter += e.getValue().getOrDefault(w3, 0L);
        }
        if (othersBefore == 0) {
            return true;
        }
        double ratio = othersAfter / (double) othersBefore;
        return ratio >= 0.5 && ratio <= 2.0;
    }
}
