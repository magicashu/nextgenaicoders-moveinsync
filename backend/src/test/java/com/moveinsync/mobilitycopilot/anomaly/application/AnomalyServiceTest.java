package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyServiceTest {

    @Test
    void marksAHighMaterialIncrease() {
        MetricResult metric = new MetricResult(
                MetricId.M01_DELAYED_TRIP_RATE,
                "Delayed-trip rate",
                new BigDecimal("30.00"),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                3,
                10,
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-07"),
                "M01-v1",
                "fixture-v1");

        var finding = new AnomalyService().assess(metric);

        assertThat(finding.material()).isTrue();
        assertThat(finding.severity()).isEqualTo("HIGH");
    }
}
