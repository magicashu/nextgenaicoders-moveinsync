package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.anomaly.domain.RegimeChangeFinding;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegimeChangeDetectorTest {

    private final RegimeChangeDetector detector = new RegimeChangeDetector();

    @Test
    void classifiesSingleTypeStepToZeroAsRegimeChange() {
        List<RegimeChangeDetector.WeeklyCount> weekly = new ArrayList<>();
        LocalDate week = LocalDate.parse("2026-05-04");
        long[] signOff = {3466, 4111, 0, 0, 0, 0};
        long[] geofence = {330, 340, 350, 345, 360, 355};
        for (int i = 0; i < signOff.length; i++) {
            weekly.add(new RegimeChangeDetector.WeeklyCount("EMPLOYEE_SIGN_OFF_TIME_VIOLATION", week.plusWeeks(i), signOff[i]));
            weekly.add(new RegimeChangeDetector.WeeklyCount("EMPLOYEE_GEOFENCE_VIOLATION", week.plusWeeks(i), geofence[i]));
        }

        List<RegimeChangeFinding> findings = detector.detect("pinnacle-Slc", weekly);

        assertThat(findings).hasSize(1);
        RegimeChangeFinding finding = findings.getFirst();
        assertThat(finding.eventType()).isEqualTo("EMPLOYEE_SIGN_OFF_TIME_VIOLATION");
        assertThat(finding.direction()).isEqualTo("STEP_DOWN");
        assertThat(finding.afterWeekStart()).isEqualTo(LocalDate.parse("2026-05-18"));
        assertThat(finding.note()).contains("data-regime change").contains("never escalated");
    }

    @Test
    void doesNotFlagBroadDeteriorationOrHealthySeries() {
        List<RegimeChangeDetector.WeeklyCount> weekly = new ArrayList<>();
        LocalDate week = LocalDate.parse("2026-05-04");
        for (int i = 0; i < 6; i++) {
            weekly.add(new RegimeChangeDetector.WeeklyCount("DEVICE_NOT_REACHABLE", week.plusWeeks(i), 200 + 40L * i));
            weekly.add(new RegimeChangeDetector.WeeklyCount("VEHICLE_STOPPAGE", week.plusWeeks(i), 150 + 30L * i));
        }
        assertThat(detector.detect("vanta-Aus", weekly)).isEmpty();
    }

    @Test
    void ignoresLowVolumeTypes() {
        List<RegimeChangeDetector.WeeklyCount> weekly = List.of(
                new RegimeChangeDetector.WeeklyCount("SUPPLEMENTARY_ALERT", LocalDate.parse("2026-05-04"), 20),
                new RegimeChangeDetector.WeeklyCount("SUPPLEMENTARY_ALERT", LocalDate.parse("2026-05-11"), 20),
                new RegimeChangeDetector.WeeklyCount("SUPPLEMENTARY_ALERT", LocalDate.parse("2026-05-18"), 0));
        assertThat(detector.detect("orbit-Slc", weekly)).isEmpty();
    }
}
