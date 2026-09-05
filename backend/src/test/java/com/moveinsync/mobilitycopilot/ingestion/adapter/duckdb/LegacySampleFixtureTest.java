package com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetFileCatalog;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbCapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The scaffold's four-column ride sample must still load, with missing columns and files reported. */
class LegacySampleFixtureTest {

    @Test
    void loadsAPartialRideFileAndDegradesEverythingElse() {
        DuckDbAnalyticsStore store = FixtureStores.at(DatasetFileCatalog.resolveDirectory(FixtureStores.LEGACY_FIXTURE));
        var catalog = store.catalog();
        assertThat(catalog.isPresent(DatasetFile.RIDES)).isTrue();
        assertThat(catalog.profile(DatasetFile.RIDES).missingColumns()).contains("vendor_id", "office", "trip_direction");
        assertThat(catalog.missingFiles()).containsExactlyInAnyOrder(DatasetFile.LEGS, DatasetFile.BILLS, DatasetFile.FEEDBACK, DatasetFile.ALERTS);

        DuckDbMetricService metrics = new DuckDbMetricService(store, new DuckDbCapabilityMatrixService(store));
        MetricResult m01 = metrics.delayedTripRate(new TenantContext("pinnacle-Slc"), LocalDate.parse("2026-06-08"));
        assertThat(m01.status()).isEqualTo(MetricStatus.SUPPORTED);
        assertThat(m01.value()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(m01.baselineValue()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(m01.caveats()).anyMatch(c -> c.startsWith("Below minimum volume"));

        MetricResult m04 = metrics.query(com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery.trailingWeekWithFourWeekBaseline(
                new TenantContext("pinnacle-Slc"), MetricId.M04_ON_TIME_PICKUP_RATE, LocalDate.parse("2026-06-08")));
        assertThat(m04.status()).isEqualTo(MetricStatus.UNSUPPORTED);
        assertThat(m04.caveats().getFirst()).contains("emp_Data.csv");
        store.close();
    }
}
