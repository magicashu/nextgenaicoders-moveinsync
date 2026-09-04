package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyServiceTest {

    private final AnomalyService service = new AnomalyService();

    @Test
    void marksTheG1RiseAsHighMaterialIncrease() {
        var finding = service.assess(metric("21.88", "12.28", 19913));

        assertThat(finding.material()).isTrue();
        assertThat(finding.severity()).isEqualTo("HIGH");
        assertThat(finding.ruleVersion()).isEqualTo("anomaly-rules-v1");
        assertThat(finding.summary()).contains("+9.60 points").contains("+78% relative");
    }

    @Test
    void healthyInputDoesNotProduceAForcedAnomaly() {
        var finding = service.assess(metric("4.33", "3.92", 4849));

        assertThat(finding.material()).isFalse();
        assertThat(finding.severity()).isEqualTo("HEALTHY");
    }

    @Test
    void lowVolumeIsNeverMaterial() {
        var finding = service.assess(metric("30.00", "10.00", 10));

        assertThat(finding.material()).isFalse();
        assertThat(finding.severity()).isEqualTo("LOW_VOLUME");
    }

    @Test
    void absoluteGapAloneIsNotEnough() {
        var finding = service.assess(metric("53.0", "50.0", 5000));
        assertThat(finding.material()).isFalse();
    }

    @Test
    void unsupportedMetricMakesNoClaim() {
        MetricResult unsupported = new MetricResult(MetricId.M10_COST_PER_BILLED_KM, "Cost per billed km", MetricUnit.CURRENCY_PER_KM,
                MetricStatus.UNSUPPORTED, null, null, null, null, null, 0, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"),
                Map.of(), "metrics-v1.1", "data-x", "sql/metrics/m10_cost_per_billed_km.sql", List.of("Unsupported: zero km"));
        var finding = service.assess(unsupported);
        assertThat(finding.material()).isFalse();
        assertThat(finding.severity()).isEqualTo("UNSUPPORTED");
    }

    private static MetricResult metric(String value, String baseline, long population) {
        BigDecimal v = new BigDecimal(value);
        BigDecimal b = new BigDecimal(baseline);
        return new MetricResult(MetricId.M01_DELAYED_TRIP_RATE, "Delayed-trip rate", MetricUnit.PERCENT, MetricStatus.SUPPORTED,
                v, b, v.subtract(b), BigDecimal.valueOf(Math.round(population * v.doubleValue() / 100)), BigDecimal.valueOf(population), population,
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"), Map.of(), "metrics-v1.1", "data-x",
                "sql/metrics/m01_delayed_trip_rate.sql", List.of());
    }
}
