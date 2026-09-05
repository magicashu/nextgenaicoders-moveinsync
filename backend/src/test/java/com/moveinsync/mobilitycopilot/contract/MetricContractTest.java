package com.moveinsync.mobilitycopilot.contract;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricContractTest {

    @Test
    void buildsTheFrozenTrailingWeekAndFourWeekBaseline() {
        MetricQuery query = MetricQuery.trailingWeekWithFourWeekBaseline(
                new TenantContext("pinnacle-Slc"),
                MetricId.M01_DELAYED_TRIP_RATE,
                LocalDate.parse("2026-06-08"));

        assertThat(query.currentStart()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(query.currentEnd()).isEqualTo(LocalDate.parse("2026-06-07"));
        assertThat(query.baselineStart()).isEqualTo(LocalDate.parse("2026-05-04"));
        assertThat(query.baselineEnd()).isEqualTo(LocalDate.parse("2026-05-31"));
    }

    @Test
    void exposesAllEighteenGovernedMetricIdentifiers() {
        assertThat(MetricId.values()).hasSize(18);
        assertThat(MetricId.M09_MEDIAN_COST_PER_TRIP.name()).startsWith("M09_");
        assertThat(MetricId.M18_ESCORT_PRESENT_RATE.name()).startsWith("M18_");
    }

    @Test
    void rejectsSupportedMetricWithoutAValue() {
        assertThatThrownBy(() -> metric(MetricStatus.SUPPORTED, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require a value");
    }

    @Test
    void rejectsUnsupportedMetricWithoutAnExplanation() {
        assertThatThrownBy(() -> metric(MetricStatus.UNSUPPORTED, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require a caveat");
    }

    private MetricResult metric(MetricStatus status, BigDecimal value, List<String> caveats) {
        return new MetricResult(
                MetricId.M10_COST_PER_BILLED_KM,
                "Cost per billed km",
                MetricUnit.CURRENCY_PER_KM,
                status,
                value,
                null,
                null,
                null,
                null,
                0,
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-07"),
                Map.of(),
                "metrics-v1.1",
                "fixture-v1",
                "sql/metrics/m10_cost_per_billed_km.sql",
                caveats);
    }
}
