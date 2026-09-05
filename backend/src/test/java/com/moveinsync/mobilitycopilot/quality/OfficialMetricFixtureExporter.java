package com.moveinsync.mobilitycopilot.quality;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.application.DeterministicAnomalyDetectionService;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.DuckDbAnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.FixtureStores;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbCapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbContributionService;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit release-only exporter; its class name intentionally does not match the default Surefire pattern. */
class OfficialMetricFixtureExporter {

    private static final TenantContext PINNACLE = new TenantContext("pinnacle-Slc");
    private static final MetricWindow G1 = window("2026-06-01", "2026-06-07");
    private static final MetricWindow G1_BASE = window("2026-05-04", "2026-05-31");

    @Test
    void writesLiveOfficialMetricResultsForTheEvaluationGate() throws Exception {
        Assumptions.assumeTrue(FixtureStores.officialDirectory().isPresent(), "official dataset not available");
        try (DuckDbAnalyticsStore store = FixtureStores.official()) {
            var capabilities = new DuckDbCapabilityMatrixService(store);
            var metrics = new DuckDbMetricService(store, capabilities);
            var contributions = new DuckDbContributionService(store);
            var detector = new DeterministicAnomalyDetectionService(metrics, contributions, capabilities, store);
            Map<String, Object> results = new LinkedHashMap<>();

            MetricResult f01 = query(metrics, PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, G1, G1_BASE);
            results.put("F01", Map.of("numerator", f01.numerator(), "denominator", f01.denominator(), "value", f01.value()));

            MetricResult f02 = query(metrics, PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, G1_BASE, G1_BASE);
            results.put("F02", Map.of("numerator", f02.numerator(), "denominator", f02.denominator(), "value", f02.value()));

            MetricResult f03 = query(metrics, new TenantContext("vanta-Aus"), MetricId.M04_ON_TIME_PICKUP_RATE,
                    window("2026-07-27", "2026-07-31"), window("2026-05-01", "2026-05-31"));
            results.put("F03", Map.of("denominator", f03.denominator(), "value", f03.value(),
                    "lateShare", BigDecimal.valueOf(100).subtract(f03.value())));

            MetricResult f04 = query(metrics, new TenantContext("vanta-Sea"), MetricId.M09_MEDIAN_COST_PER_TRIP,
                    window("2026-05-01", "2026-05-31"), window("2026-04-03", "2026-04-30"));
            results.put("F04", Map.of("value", f04.value(), "excludedNegativeLines",
                    scalar(store, "SELECT count(*) FROM bills WHERE business_unit='vanta-Sea' AND billed_cost < 0")));

            MetricResult f05Aus = query(metrics, new TenantContext("vanta-Aus"), MetricId.M10_COST_PER_BILLED_KM, G1, G1_BASE);
            MetricResult f05Sea = query(metrics, new TenantContext("vanta-Sea"), MetricId.M10_COST_PER_BILLED_KM, G1, G1_BASE);
            assertThat(List.of(f05Aus.status().name(), f05Sea.status().name())).containsOnly("UNSUPPORTED");
            assertThat(f05Aus.caveats().toString().toLowerCase()).contains("zero km");
            assertThat(f05Sea.caveats().toString().toLowerCase()).contains("zero km");
            results.put("F05", Map.of("status", "UNSUPPORTED", "reasonContains", "zero km"));

            boolean mayFlagMay = detector.detect(PINNACLE, LocalDate.parse("2026-05-25")).materialCandidates().stream()
                    .anyMatch(candidate -> candidate.metricId() == MetricId.M13_ALERT_RATE);
            results.put("F06", Map.of("excludedEventType", "EMPLOYEE_SIGN_OFF_TIME_VIOLATION",
                    "excludedCount", store.qualityReport().finding("Q5-SIGN-OFF-VIOLATIONS"), "mayFlagMay", mayFlagMay));
            results.put("F07", Map.of("count", store.qualityReport().finding("Q1-TRIP-ID-COLLISIONS"),
                    "tenants", List.of("orbit-Slc", "vanta-Aus")));
            results.put("F08", Map.of("duplicateLegsRemoved", store.qualityReport().finding("Q1-DUPLICATE-LEGS"),
                    "exactDuplicateBillLinesRemoved", store.qualityReport().finding("Q1-DUPLICATE-BILLS")));

            long capped = scalar(store, "SELECT count(*) FROM trips WHERE vendor_id='Pooja Mikhailov Travel' AND delay_minutes > 600");
            long quarantined = scalar(store, "SELECT count(*) FROM trips WHERE vendor_id='Pooja Mikhailov Travel' AND delay_minutes > 1440");
            MetricResult mean = query(metrics, PINNACLE, MetricId.M02_DELAY_MINUTES,
                    window("2026-05-01", "2026-07-31"), window("2026-05-01", "2026-07-31"));
            assertThat(mean.value()).isLessThan(new BigDecimal("600"));
            results.put("F09", Map.of("cappedAbove600", capped, "quarantinedAbove1440", quarantined, "meanBelow", 600));
            results.put("F10", Map.of("marshalZeroRows", store.qualityReport().finding("Q12-MARSHAL-ZERO"), "excludedFromM12", true));

            Path output = Corpus.path("evals/results/.gitkeep").getParent().resolve("metric-results.json");
            Files.createDirectories(output.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), results);
            assertThat(output).exists();
        }
    }

    private static MetricResult query(DuckDbMetricService metrics, TenantContext tenant, MetricId metric,
                                      MetricWindow current, MetricWindow baseline) {
        return metrics.query(new MetricQuery(tenant, metric, current.start(), current.end(), baseline.start(), baseline.end(), Map.of()));
    }

    private static long scalar(DuckDbAnalyticsStore store, String sql) throws Exception {
        try (var connection = store.borrow(); var statement = connection.createStatement(); var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static MetricWindow window(String start, String end) {
        return new MetricWindow(LocalDate.parse(start), LocalDate.parse(end));
    }
}
