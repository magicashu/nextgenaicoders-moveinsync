package com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetFileCatalog;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbCapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** V1-V5 degraded-data behaviour, built from copies of the fixture. The originals are never touched. */
class CorruptedVariantTest {

    private static final TenantContext PINNACLE = new TenantContext("pinnacle-Slc");
    private static final LocalDate AS_OF = LocalDate.parse("2026-06-08");

    private static void copyFixture(Path target, String... exclude) throws IOException {
        Path source = DatasetFileCatalog.resolveDirectory(FixtureStores.SEVEN_FILE_FIXTURE);
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".csv") && List.of(exclude).stream().noneMatch(name::equals)) {
                    Files.copy(file, target.resolve(name));
                }
            }
        }
    }

    private static MetricResult query(DuckDbAnalyticsStore store, MetricId id) {
        DuckDbMetricService metrics = new DuckDbMetricService(store, new DuckDbCapabilityMatrixService(store));
        return metrics.query(MetricQuery.trailingWeekWithFourWeekBaseline(PINNACLE, id, AS_OF));
    }

    @Test
    void v1MissingLegsDisablesLegMetricsOnly(@TempDir Path dir) throws IOException {
        copyFixture(dir, "emp_Data.csv");
        try (DuckDbAnalyticsStore store = FixtureStores.at(dir)) {
            assertThat(store.catalog().missingFiles()).containsExactly(DatasetFile.LEGS);
            assertThat(query(store, MetricId.M04_ON_TIME_PICKUP_RATE).status()).isEqualTo(MetricStatus.UNSUPPORTED);
            assertThat(query(store, MetricId.M06_NO_SHOW_RATE).caveats().getFirst()).contains("emp_Data.csv");
            assertThat(query(store, MetricId.M01_DELAYED_TRIP_RATE).value()).isEqualByComparingTo("25.00");
        }
    }

    @Test
    void v2MissingBillsDisablesCostBranch(@TempDir Path dir) throws IOException {
        copyFixture(dir, "bill_data.csv");
        try (DuckDbAnalyticsStore store = FixtureStores.at(dir)) {
            assertThat(query(store, MetricId.M09_MEDIAN_COST_PER_TRIP).status()).isEqualTo(MetricStatus.UNSUPPORTED);
            assertThat(query(store, MetricId.M10_COST_PER_BILLED_KM).status()).isEqualTo(MetricStatus.UNSUPPORTED);
            assertThat(new DuckDbCapabilityMatrixService(store).matrix(PINNACLE).isUnsupported(MetricId.M09_MEDIAN_COST_PER_TRIP)).isTrue();
        }
    }

    @Test
    void v3ShuffledFeedbackKeysAreQuarantinedNotJoined(@TempDir Path dir) throws IOException {
        copyFixture(dir);
        Path feedback = dir.resolve("trip_feedback.csv");
        List<String> lines = Files.readAllLines(feedback);
        for (int i = 1; i < lines.size(); i++) {
            if (i % 20 == 0) {
                lines.set(i, lines.get(i).replaceFirst("\"3,000,(\\d{3})\"", "\"9,000,$1\""));
            }
        }
        Files.write(feedback, lines);
        try (DuckDbAnalyticsStore store = FixtureStores.at(dir)) {
            double coverage = store.qualityReport().joinCoverage().get("feedback");
            assertThat(coverage).isLessThan(1.0).isGreaterThan(0.9);
            assertThat(query(store, MetricId.M11_LOW_DRIVER_RATING_RATE).status()).isEqualTo(MetricStatus.SUPPORTED);
        }
    }

    @Test
    void v4InjectedDuplicateRidesAreRemovedAndReported(@TempDir Path dir) throws IOException {
        copyFixture(dir);
        Path june = dir.resolve("Ride_data _trip-June_2026.csv");
        List<String> lines = Files.readAllLines(june);
        List<String> duplicated = new java.util.ArrayList<>(lines);
        duplicated.addAll(lines.subList(1, 41));
        Files.write(june, duplicated);
        try (DuckDbAnalyticsStore store = FixtureStores.at(dir)) {
            assertThat(store.catalog().profile(DatasetFile.RIDES).rawRows()).isEqualTo(915);
            assertThat(store.catalog().profile(DatasetFile.RIDES).normalizedRows()).isEqualTo(875);
            assertThat(store.qualityReport().finding("Q0-DUPLICATE-TRIPS")).isEqualTo(40);
            assertThat(query(store, MetricId.M01_DELAYED_TRIP_RATE).value()).isEqualByComparingTo("25.00");
        }
    }

    @Test
    void v5BlankSeverityKeepsAlertCountsButDisablesSeverityMetrics(@TempDir Path dir) throws IOException {
        copyFixture(dir);
        Path alerts = dir.resolve("alerts_data.csv");
        List<String> lines = Files.readAllLines(alerts);
        for (int i = 1; i < lines.size(); i++) {
            lines.set(i, lines.get(i).replaceAll(",(Sev-[123]|False|NA),", ",,"));
        }
        Files.write(alerts, lines);
        try (DuckDbAnalyticsStore store = FixtureStores.at(dir)) {
            assertThat(query(store, MetricId.M13_ALERT_RATE).value()).isEqualByComparingTo("164.29");
            assertThat(query(store, MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90).status()).isEqualTo(MetricStatus.UNSUPPORTED);
            MetricResult severe = query(store, MetricId.M14_SEVERE_ALERT_RATE);
            assertThat(severe.value()).isEqualByComparingTo("0.00");
            assertThat(severe.caveats()).anyMatch(c -> c.contains("No Sev-1/2"));
        }
    }
}
