package com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb;

import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityReport;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetCatalog;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SevenFileFixtureIngestionTest {

    private final DuckDbAnalyticsStore store = FixtureStores.sevenFileFixture();

    @Test
    void discoversValidatesAndNormalisesAllSevenFiles() {
        DatasetCatalog catalog = store.catalog();
        assertThat(catalog.missingFiles()).isEmpty();
        assertThat(catalog.dataVersion()).startsWith("data-");
        assertThat(catalog.profile(DatasetFile.RIDES).paths()).hasSize(2);
        assertThat(catalog.profile(DatasetFile.RIDES).rawRows()).isEqualTo(875);
        assertThat(catalog.profile(DatasetFile.LEGS).rawRows()).isEqualTo(1401);
        assertThat(catalog.profile(DatasetFile.LEGS).normalizedRows()).isEqualTo(1400);
        assertThat(catalog.profile(DatasetFile.BILLS).rawRows()).isEqualTo(884);
        assertThat(catalog.profile(DatasetFile.BILLS).normalizedRows()).isEqualTo(877);
        assertThat(catalog.profile(DatasetFile.ALERTS).missingColumns()).isEmpty();
    }

    @Test
    void reportsTheDocumentedQualityFindings() {
        DataQualityReport report = store.qualityReport();
        assertThat(report.finding("Q1-TRIP-ID-COLLISIONS")).isEqualTo(3);
        assertThat(report.finding("Q1-DUPLICATE-LEGS")).isEqualTo(1);
        assertThat(report.finding("Q1-DUPLICATE-BILLS")).isEqualTo(7);
        assertThat(report.finding("Q3-NEGATIVE-BILLS")).isEqualTo(1);
        assertThat(report.finding("Q4-CAPPED-DELAYS")).isEqualTo(8);
        assertThat(report.finding("Q4-QUARANTINED-DELAYS")).isEqualTo(1);
        assertThat(report.finding("Q10-NEGATIVE-DISTANCE")).isEqualTo(1);
        assertThat(report.finding("Q9-OCCUPANCY-CAPPED")).isEqualTo(35);
        assertThat(report.finding("Q6-UNCLASSIFIED-SEVERITY")).isEqualTo(35);
        assertThat(report.finding("Q6-NULL-SEVERITY")).isEqualTo(400);
        assertThat(report.finding("Q5-SIGN-OFF-VIOLATIONS")).isEqualTo(400);
        assertThat(report.zeroBilledKmShareByTenant().get("orbit-Slc")).isEqualTo(1.0);
        assertThat(report.feedbackCoverageByTenant().get("pinnacle-Slc")).isEqualTo(1.0);
        assertThat(report.joinCoverage().get("legs")).isEqualTo(1.0);
    }

    @Test
    void compositeKeyJoinNeverMixesTenants() throws Exception {
        try (Connection connection = store.borrow(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT count(*) FROM legs l JOIN trips t ON t.business_unit = l.business_unit AND t.trip_id = l.trip_id
                    WHERE l.business_unit <> t.business_unit""");
            rs.next();
            assertThat(rs.getLong(1)).isZero();
            rs = statement.executeQuery("SELECT count(*) FROM trips WHERE trip_id IN (3000001, 3000002, 3000003)");
            rs.next();
            assertThat(rs.getLong(1)).as("each colliding id exists once per tenant").isEqualTo(6);
            rs = statement.executeQuery("SELECT count(*) FROM alerts WHERE severity IS NULL");
            rs.next();
            assertThat(rs.getLong(1)).as("NA tokens normalised to NULL").isEqualTo(400);
        }
    }
}
