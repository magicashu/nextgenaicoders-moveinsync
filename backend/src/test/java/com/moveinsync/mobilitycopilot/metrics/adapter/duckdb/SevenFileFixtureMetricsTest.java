package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.FixtureStores;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequestException;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricSeries;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SevenFileFixtureMetricsTest {

    private static final TenantContext PINNACLE = new TenantContext("pinnacle-Slc");
    private static final TenantContext ORBIT = new TenantContext("orbit-Slc");
    private static final LocalDate AS_OF = LocalDate.parse("2026-06-08");
    private static final MetricWindow CURRENT = MetricWindow.trailingWeek(AS_OF);
    private static final MetricWindow BASELINE = MetricWindow.priorFourWeeks(CURRENT);

    private final var_store store = new var_store();

    static final class var_store {
        final com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.DuckDbAnalyticsStore analytics = FixtureStores.sevenFileFixture();
        final DuckDbCapabilityMatrixService capabilities = new DuckDbCapabilityMatrixService(analytics);
        final DuckDbMetricService metrics = new DuckDbMetricService(analytics, capabilities);
        final DuckDbContributionService contributions = new DuckDbContributionService(analytics);
        final DuckDbMetricSnapshotService snapshots = new DuckDbMetricSnapshotService(analytics);
    }

    private MetricResult query(TenantContext tenant, MetricId id, Map<String, String> filters) {
        return store.metrics.query(new MetricQuery(tenant, id, CURRENT.start(), CURRENT.end(), BASELINE.start(), BASELINE.end(), filters));
    }

    @Test
    void reproducesHeadlineM01WithProvenance() {
        MetricResult m01 = query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Map.of());
        assertThat(m01.status()).isEqualTo(MetricStatus.SUPPORTED);
        assertThat(m01.value()).isEqualByComparingTo("25.00");
        assertThat(m01.baselineValue()).isEqualByComparingTo("10.00");
        assertThat(m01.delta()).isEqualByComparingTo("15.00");
        assertThat(m01.numerator()).isEqualByComparingTo("35");
        assertThat(m01.denominator()).isEqualByComparingTo("140");
        assertThat(m01.supportingCount()).isEqualTo(140);
        assertThat(m01.contractVersion()).isEqualTo("metrics-v1.1");
        assertThat(m01.dataVersion()).isEqualTo(store.analytics.catalog().dataVersion());
        assertThat(m01.source()).isEqualTo("sql/metrics/m01_delayed_trip_rate.sql");
        assertThat(m01.caveats()).anyMatch(c -> c.startsWith("Below minimum volume"));
    }

    @Test
    void appliesAllowlistedFiltersAndRejectsOthers() {
        assertThat(query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Map.of("site_id", "Clearwater Campus")).value()).isEqualByComparingTo("50.00");
        assertThat(query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Map.of("direction", "LOGIN")).value()).isEqualByComparingTo("30.00");
        assertThatThrownBy(() -> query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Map.of("employee", "x")))
                .isInstanceOf(MetricRequestException.class);
    }

    @Test
    void reproducesTheRemainingContracts() {
        assertThat(query(PINNACLE, MetricId.M02_DELAY_MINUTES, Map.of()).value()).isEqualByComparingTo("140.80");
        assertThat(query(PINNACLE, MetricId.M02_DELAY_MINUTES, Map.of("statistic", "P90")).value()).isEqualByComparingTo("600.00");
        assertThat(query(PINNACLE, MetricId.M03_DELAY_REASON_MIX, Map.of("delay_reason", "DRIVER")).value()).isEqualByComparingTo("34.29");
        MetricResult m04 = query(PINNACLE, MetricId.M04_ON_TIME_PICKUP_RATE, Map.of());
        assertThat(m04.value()).isEqualByComparingTo("67.77");
        assertThat(m04.baselineValue()).isEqualByComparingTo("91.03");
        assertThat(m04.denominator()).isEqualByComparingTo("273");
        assertThat(query(PINNACLE, MetricId.M06_NO_SHOW_RATE, Map.of()).value()).isEqualByComparingTo("2.50");
        assertThat(query(PINNACLE, MetricId.M07_DASHBOARD_CANCELLATION_RATE, Map.of()).value()).isEqualByComparingTo("0.00");
        assertThat(query(PINNACLE, MetricId.M08_OCCUPANCY_RATE, Map.of()).value()).isEqualByComparingTo("52.50");
        assertThat(query(PINNACLE, MetricId.M09_MEDIAN_COST_PER_TRIP, Map.of()).value()).isEqualByComparingTo("1040.00");
        assertThat(query(PINNACLE, MetricId.M10_COST_PER_BILLED_KM, Map.of()).value()).isEqualByComparingTo("83.20");
        assertThat(query(PINNACLE, MetricId.M11_LOW_DRIVER_RATING_RATE, Map.of()).value()).isEqualByComparingTo("2.50");
        assertThat(query(PINNACLE, MetricId.M12_MEAN_DRIVER_SAFETY_RATING, Map.of()).value()).isEqualByComparingTo("4.93");
        assertThat(query(PINNACLE, MetricId.M13_ALERT_RATE, Map.of()).value()).isEqualByComparingTo("164.29");
        assertThat(query(PINNACLE, MetricId.M14_SEVERE_ALERT_RATE, Map.of()).value()).isEqualByComparingTo("14.29");
        assertThat(query(PINNACLE, MetricId.M16_TRACKING_GAP_RATE, Map.of()).value()).isEqualByComparingTo("100.00");
        assertThat(query(PINNACLE, MetricId.M17_EV_SHARE, Map.of()).value()).isEqualByComparingTo("20.00");
    }

    @Test
    void unsupportedMetricsReturnTypedReasons() {
        MetricResult orbitCostPerKm = query(ORBIT, MetricId.M10_COST_PER_BILLED_KM, Map.of());
        assertThat(orbitCostPerKm.status()).isEqualTo(MetricStatus.UNSUPPORTED);
        assertThat(orbitCostPerKm.value()).isNull();
        assertThat(orbitCostPerKm.caveats().getFirst()).contains("zero km");

        assertThat(query(PINNACLE, MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90, Map.of()).status()).isEqualTo(MetricStatus.UNSUPPORTED);
        assertThat(query(PINNACLE, MetricId.M18_ESCORT_PRESENT_RATE, Map.of()).status()).isEqualTo(MetricStatus.UNSUPPORTED);
        MetricResult orbitEscort = store.metrics.query(new MetricQuery(ORBIT, MetricId.M18_ESCORT_PRESENT_RATE,
                LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-31"), LocalDate.parse("2026-04-06"), LocalDate.parse("2026-05-03"), Map.of()));
        assertThat(orbitEscort.value()).isEqualByComparingTo("100.00");
        assertThat(orbitEscort.caveats()).anyMatch(c -> c.contains("no compliance claim"));
    }

    @Test
    void capabilityMatrixFollowsTheData() {
        CapabilityMatrix pinnacle = store.capabilities.matrix(PINNACLE);
        assertThat(pinnacle.forMetric(MetricId.M10_COST_PER_BILLED_KM)).get().extracting(CapabilityStatus::support).isEqualTo(CapabilityStatus.Support.SUPPORTED);
        assertThat(pinnacle.forMetric(MetricId.M11_LOW_DRIVER_RATING_RATE)).get().extracting(CapabilityStatus::support).isEqualTo(CapabilityStatus.Support.SUPPORTED);
        CapabilityMatrix orbit = store.capabilities.matrix(ORBIT);
        assertThat(orbit.isUnsupported(MetricId.M10_COST_PER_BILLED_KM)).isTrue();
        assertThat(orbit.forMetric(MetricId.M11_LOW_DRIVER_RATING_RATE)).get().extracting(CapabilityStatus::support).isEqualTo(CapabilityStatus.Support.DERIVABLE);
        assertThat(orbit.statuses()).anyMatch(s -> s.analysis().equals("gps_location") && s.support() == CapabilityStatus.Support.UNSUPPORTED);
    }

    @Test
    void ranksContributorsWithMinimumVolumeQualification() {
        ContributionRanking bySite = store.contributions.rankContributors(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Dimension.SITE_ID, CURRENT, BASELINE, Map.of());
        assertThat(bySite.rows()).hasSize(2);
        assertThat(bySite.rows().getFirst().member()).isEqualTo("Clearwater Campus");
        assertThat(bySite.rows().getFirst().shareOfCurrentNumerator()).isEqualByComparingTo("100.0");
        assertThat(bySite.rows().getFirst().qualified()).as("70 trips is below the 300 minimum").isFalse();
        assertThat(bySite.evidenceId()).startsWith("pinnacle-Slc:m01_delayed_trip_rate:site_id:2026-06-07");
        ContributionRanking byVendor = store.contributions.rankContributors(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Dimension.VENDOR_ID, CURRENT, BASELINE, Map.of());
        assertThat(byVendor.minimumVolume()).isEqualTo(500);
        assertThat(byVendor.allQualifiedIncreased()).isFalse();
        assertThatThrownBy(() -> store.contributions.rankContributors(PINNACLE, MetricId.M09_MEDIAN_COST_PER_TRIP, Dimension.VENDOR_ID, CURRENT, BASELINE, Map.of("statistic", "x")))
                .isInstanceOf(MetricRequestException.class);
    }

    @Test
    void distributionsImpactAndSnapshotsCarryProvenance() {
        Distribution mix = store.contributions.delayReasonMix(PINNACLE, CURRENT, BASELINE, Map.of());
        assertThat(mix.currentTotal()).isEqualTo(35);
        assertThat(mix.rows()).extracting("category").contains("DRIVER", "EMPLOYEE", "TRAFFIC");
        var impact = store.contributions.delayedRiderLegs(PINNACLE, CURRENT, Map.of());
        assertThat(impact.delayedTrips()).isEqualTo(35);
        assertThat(impact.delayedRiderLegs()).isEqualTo(70);
        MetricSeries series = store.snapshots.dailySeries(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, CURRENT);
        assertThat(series.points()).hasSize(7);
        assertThat(series.points().stream().mapToLong(p -> p.numerator()).sum()).isEqualTo(35);
        assertThat(series.points().getFirst().value()).isEqualByComparingTo("25.00");
        var peers = store.contributions.crossTenantDelayedTripRate(CURRENT);
        assertThat(peers).extracting("businessUnit").containsExactly("pinnacle-Slc", "orbit-Slc");
    }
}
