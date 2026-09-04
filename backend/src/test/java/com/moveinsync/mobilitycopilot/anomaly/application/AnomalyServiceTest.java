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

    @Test
    void marksAHighMaterialIncrease() {
        MetricResult metric = new MetricResult(
                MetricId.M01_DELAYED_TRIP_RATE,
                "Delayed-trip rate",
                MetricUnit.PERCENT,
                MetricStatus.SUPPORTED,
                new BigDecimal("30.00"),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("3"),
                new BigDecimal("10"),
                10,
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-07"),
                Map.of(),
                "metrics-v1.1",
                "fixture-v1",
                "sql/metrics/m01_delayed_trip_rate.sql",
                List.of());

        var finding = new AnomalyService().assess(metric);

        assertThat(finding.material()).isTrue();
        assertThat(finding.severity()).isEqualTo("HIGH");
    }
}
