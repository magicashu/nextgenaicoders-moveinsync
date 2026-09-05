package com.moveinsync.mobilitycopilot.integration;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialDatasetReconciliationTest {

    private static final String DATASET =
            "outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset";

    @Test
    void reproducesTheOfficialG1HeadlineAndBaseline() {
        Path dataDirectory = locateDataset();
        Assumptions.assumeTrue(
                dataDirectory != null,
                "Official dataset is not available in this checkout");

        var service = new DuckDbMetricService(new MobilityDataProperties(dataDirectory.toString()));
        var result = service.delayedTripRate(
                new TenantContext("pinnacle-Slc"),
                LocalDate.parse("2026-06-08"));

        assertThat(result.status()).isEqualTo(MetricStatus.SUPPORTED);
        assertThat(result.unit()).isEqualTo(MetricUnit.PERCENT);
        assertThat(result.numerator()).isEqualByComparingTo(new BigDecimal("4357"));
        assertThat(result.denominator()).isEqualByComparingTo(new BigDecimal("19913"));
        assertThat(result.value()).isEqualByComparingTo(new BigDecimal("21.88"));
        assertThat(result.baselineValue()).isEqualByComparingTo(new BigDecimal("12.28"));
        assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("9.60"));
        assertThat(result.contractVersion()).isEqualTo("metrics-v1.1");
    }

    private Path locateDataset() {
        var configured=com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.FixtureStores.officialDirectory();
        if(configured.isPresent())return configured.get();
        for (Path candidate : new Path[]{Path.of(DATASET), Path.of("..").resolve(DATASET)}) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
