package com.moveinsync.mobilitycopilot.metrics.domain;

import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricRegistryTest {

    @Test
    void definesAllEighteenContractsWithReviewedSql() {
        assertThat(MetricRegistry.all()).hasSize(18);
        for (MetricDefinition definition : MetricRegistry.all()) {
            String sql = SqlResources.read(definition.sqlResource());
            assertThat(sql).as(definition.id().name()).contains("{{bu}}").contains("metrics-v1.1");
            assertThat(definition.requiredFiles()).isNotEmpty();
        }
        assertThat(SqlResources.read(MetricRegistry.sqlResource(MetricId.M02_DELAY_MINUTES, Map.of("statistic", "P90")))).contains("quantile_cont");
        assertThat(SqlResources.read(MetricRegistry.sqlResource(MetricId.M12_MEAN_DRIVER_SAFETY_RATING, Map.of("rating", "safety")))).contains("safety_rating");
    }

    @Test
    void freezesTheV11Semantics() {
        assertThat(MetricRegistry.definition(MetricId.M09_MEDIAN_COST_PER_TRIP).numerator()).contains("median");
        assertThat(MetricRegistry.definition(MetricId.M11_LOW_DRIVER_RATING_RATE).name()).isEqualTo("Low driver-rating rate");
        assertThat(MetricRegistry.definition(MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90).denominator()).contains("Sev-1/2");
        assertThat(MetricRegistry.definition(MetricId.M18_ESCORT_PRESENT_RATE).name()).isEqualTo("Escort-present rate");
        assertThat(MetricRegistry.minimumVolume(MetricId.M01_DELAYED_TRIP_RATE, Dimension.VENDOR_ID)).isEqualTo(500);
        assertThat(MetricRegistry.minimumVolume(MetricId.M01_DELAYED_TRIP_RATE, Dimension.SITE_ID)).isEqualTo(300);
        assertThat(ConfiguredTargets.defaultFor(MetricId.M01_DELAYED_TRIP_RATE)).get()
                .extracting(ConfiguredTarget::label).isEqualTo("Configured target, editable per tenant");
    }

    @Test
    void windowsFollowTheFrozenTrailingWeekAndPriorFourWeeks() {
        MetricWindow current = MetricWindow.trailingWeek(java.time.LocalDate.parse("2026-06-08"));
        MetricWindow baseline = MetricWindow.priorFourWeeks(current);
        assertThat(current.start()).isEqualTo(java.time.LocalDate.parse("2026-06-01"));
        assertThat(current.end()).isEqualTo(java.time.LocalDate.parse("2026-06-07"));
        assertThat(baseline.start()).isEqualTo(java.time.LocalDate.parse("2026-05-04"));
        assertThat(baseline.end()).isEqualTo(java.time.LocalDate.parse("2026-05-31"));
        assertThat(baseline.days()).isEqualTo(28);
    }
}
