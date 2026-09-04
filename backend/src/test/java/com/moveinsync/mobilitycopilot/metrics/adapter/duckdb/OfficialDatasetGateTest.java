package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.application.DeterministicAnomalyDetectionService;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyClassification;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyDetectionResult;
import com.moveinsync.mobilitycopilot.anomaly.tools.WorkerContributionTools;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.DuckDbAnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.FixtureStores;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetChecksums;
import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityReport;
import com.moveinsync.mobilitycopilot.ingestion.domain.FileProfile;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * G1/G2/G3 and the ten deterministic fixtures reproduced from the immutable official files.
 * Skipped automatically when the organizer directory is not present (CI has no dataset).
 */
class OfficialDatasetGateTest {

    private static final TenantContext PINNACLE = new TenantContext("pinnacle-Slc");
    private static final TenantContext VANTA_AUS = new TenantContext("vanta-Aus");
    private static final MetricWindow G1 = new MetricWindow(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"));
    private static final MetricWindow G1_BASE = new MetricWindow(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-31"));
    private static DuckDbAnalyticsStore store;
    private static DuckDbMetricService metrics;
    private static DuckDbContributionService contributions;
    private static DuckDbCapabilityMatrixService capabilities;

    @BeforeAll
    static void load() {
        assumeTrue(FixtureStores.officialDirectory().isPresent(), "official dataset not available");
        store = FixtureStores.official();
        capabilities = new DuckDbCapabilityMatrixService(store);
        metrics = new DuckDbMetricService(store, capabilities);
        contributions = new DuckDbContributionService(store);
    }

    private static MetricResult query(TenantContext tenant, MetricId id, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        return metrics.query(new MetricQuery(tenant, id, current.start(), current.end(), baseline.start(), baseline.end(), filters));
    }

    @Test
    void officialChecksumsAreUnchanged() {
        for (FileProfile profile : store.catalog().files()) {
            for (int i = 0; i < profile.paths().size(); i++) {
                String name = Path.of(profile.paths().get(i)).getFileName().toString();
                assertThat(DatasetChecksums.matchesOfficial(name, profile.checksums().get(i))).as(name).isTrue();
            }
        }
    }

    @Test
    void fixture1And2HeadlineM01AndBaseline() {
        MetricResult m01 = query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, G1, G1_BASE, Map.of());
        assertThat(m01.numerator()).isEqualByComparingTo("4357");
        assertThat(m01.denominator()).isEqualByComparingTo("19913");
        assertThat(m01.value()).isEqualByComparingTo("21.88");
        assertThat(m01.baselineValue()).isEqualByComparingTo("12.28");
        assertThat(m01.caveats()).noneMatch(c -> c.startsWith("Below minimum volume"));
    }

    @Test
    void g1ConcentrationSiteShiftDirectionAndVendors() {
        assertThat(query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, G1, G1_BASE, Map.of("direction", "LOGIN")).value()).isEqualByComparingTo("23.91");
        assertThat(query(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, G1, G1_BASE, Map.of("direction", "LOGIN")).baselineValue()).isEqualByComparingTo("10.98");
        ContributionRanking bySite = contributions.rankContributors(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Dimension.SITE_ID, G1, G1_BASE, Map.of());
        assertThat(bySite.rows().getFirst().member()).isEqualTo("Clearwater Campus");
        assertThat(bySite.rows().getFirst().currentValue()).isEqualByComparingTo("24.07");
        assertThat(bySite.rows().getFirst().baselineValue()).isEqualByComparingTo("12.86");
        assertThat(bySite.rows().getFirst().shareOfCurrentNumerator()).isEqualByComparingTo("51.1");
        ContributionRanking byShift = contributions.rankContributors(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Dimension.SHIFT_ID, G1, G1_BASE, Map.of());
        assertThat(byShift.rows().stream().filter(r -> r.member().equals("10:30")).findFirst().orElseThrow().currentValue()).isEqualByComparingTo("47.38");
        ContributionRanking byVendor = contributions.rankContributors(PINNACLE, MetricId.M01_DELAYED_TRIP_RATE, Dimension.VENDOR_ID, G1, G1_BASE, Map.of());
        assertThat(byVendor.qualifiedRows()).hasSize(14);
        assertThat(byVendor.allQualifiedIncreased()).as("every vendor with 500 trips in both windows rose").isTrue();
        assertThat(byVendor.qualifiedRows().stream().map(r -> r.currentValue()).min(BigDecimal::compareTo).orElseThrow()).isEqualByComparingTo("17.15");
        assertThat(byVendor.qualifiedRows().stream().map(r -> r.currentValue()).max(BigDecimal::compareTo).orElseThrow()).isEqualByComparingTo("28.37");
        Distribution reasons = contributions.delayReasonMix(PINNACLE, G1, G1_BASE, Map.of());
        assertThat(reasons.rows().stream().filter(r -> r.category().equals("DRIVER")).findFirst().orElseThrow().share()).isEqualByComparingTo("39.3");
        assertThat(reasons.rows().stream().filter(r -> r.category().equals("DRIVER")).findFirst().orElseThrow().baselineShare()).isEqualByComparingTo("33.4");
        assertThat(contributions.delayedRiderLegs(PINNACLE, G1, Map.of()).delayedRiderLegs()).isEqualTo(7780);
        assertThat(contributions.crossTenantDelayedTripRate(G1).getFirst().businessUnit()).isEqualTo("pinnacle-Slc");
    }

    @Test
    void fixture3M04VantaAusFinalWeekUnderTheFrozenDenominator() {
        // Contract v1.1 denominator: boarded valid-rider legs with both pickup epochs (11,794 legs).
        // The profile document's illustrative 9.9% used all legs including null epochs; see handoff.
        MetricResult m04 = query(VANTA_AUS, MetricId.M04_ON_TIME_PICKUP_RATE,
                new MetricWindow(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-07-31")),
                new MetricWindow(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")), Map.of());
        assertThat(m04.denominator()).isEqualByComparingTo("11794");
        assertThat(m04.value()).isEqualByComparingTo("88.18");
        assertThat(BigDecimal.valueOf(100).subtract(m04.value())).as("late share > 10 min").isEqualByComparingTo("11.82");
        assertThat(BigDecimal.valueOf(100).subtract(m04.baselineValue())).isEqualByComparingTo("4.09");
    }

    @Test
    void fixture4M09ExcludesNegativeLinesAndReturnsThePositiveMedian() {
        DataQualityReport report = store.qualityReport();
        assertThat(report.finding("Q3-NEGATIVE-BILLS")).isEqualTo(178);
        MetricResult m09 = query(new TenantContext("vanta-Sea"), MetricId.M09_MEDIAN_COST_PER_TRIP,
                new MetricWindow(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")),
                new MetricWindow(LocalDate.parse("2026-04-03"), LocalDate.parse("2026-04-30")), Map.of());
        assertThat(m09.value()).isEqualByComparingTo("1390.34");
        MetricResult pinnacleJune = query(PINNACLE, MetricId.M09_MEDIAN_COST_PER_TRIP,
                new MetricWindow(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30")),
                new MetricWindow(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")), Map.of());
        assertThat(pinnacleJune.value()).isEqualByComparingTo("1020.00");
        assertThat(pinnacleJune.baselineValue()).isEqualByComparingTo("1144.74");
    }

    @Test
    void fixture5M10UnsupportedForZeroKmTenants() {
        for (String tenant : new String[] {"vanta-Aus", "vanta-Sea"}) {
            MetricResult m10 = query(new TenantContext(tenant), MetricId.M10_COST_PER_BILLED_KM, G1, G1_BASE, Map.of());
            assertThat(m10.status()).as(tenant).isEqualTo(MetricStatus.UNSUPPORTED);
            assertThat(m10.caveats().getFirst()).contains("zero km");
        }
        assertThat(query(PINNACLE, MetricId.M10_COST_PER_BILLED_KM, G1, G1_BASE, Map.of()).status()).isEqualTo(MetricStatus.SUPPORTED);
    }

    @Test
    void fixture6M13ExcludesSignOffViolationsAndG3IsARegimeChange() {
        DataQualityReport report = store.qualityReport();
        assertThat(report.finding("Q5-SIGN-OFF-VIOLATIONS")).isEqualTo(7736);
        MetricResult may = query(PINNACLE, MetricId.M13_ALERT_RATE, new MetricWindow(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-17")),
                new MetricWindow(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-03")), Map.of());
        assertThat(may.numerator()).as("only 1,3xx non-sign-off alerts in the spike fortnight").isLessThan(new BigDecimal("2000"));
        var detection = new DeterministicAnomalyDetectionService(metrics, contributions, capabilities, store);
        var notes = detection.dataRegimeChanges(PINNACLE);
        assertThat(notes).hasSize(1);
        assertThat(notes.getFirst().eventType()).isEqualTo("EMPLOYEE_SIGN_OFF_TIME_VIOLATION");
        assertThat(notes.getFirst().afterWeekStart()).isEqualTo(LocalDate.parse("2026-05-18"));
        AnomalyDetectionResult asOfSpikeEnd = detection.detect(PINNACLE, LocalDate.parse("2026-05-25"));
        assertThat(asOfSpikeEnd.candidates()).noneMatch(c -> c.metricId() == MetricId.M13_ALERT_RATE && c.classification() == AnomalyClassification.OPERATIONAL_ANOMALY);
    }

    @Test
    void fixture7CompositeKeyDiffersByExactly6753Ids() {
        assertThat(store.qualityReport().finding("Q1-TRIP-ID-COLLISIONS")).isEqualTo(6753);
    }

    @Test
    void fixture8DedupeRemoves708LegsAnd72BillLines() {
        assertThat(store.qualityReport().finding("Q1-DUPLICATE-LEGS")).isEqualTo(708);
        assertThat(store.qualityReport().finding("Q1-DUPLICATE-BILLS")).isEqualTo(72);
    }

    @Test
    void fixture9DelayCapAndQuarantine() {
        assertThat(store.qualityReport().finding("Q4-CAPPED-DELAYS")).isEqualTo(136);
        assertThat(store.qualityReport().finding("Q4-QUARANTINED-DELAYS")).isEqualTo(20);
        MetricWindow all = new MetricWindow(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-07-31"));
        MetricResult mean = query(PINNACLE, MetricId.M02_DELAY_MINUTES, all, all, Map.of("vendor_id", "Pooja Mikhailov Travel"));
        assertThat(mean.supportingCount()).as("2,957 delayed trips minus 4 quarantined").isEqualTo(2953);
        assertThat(mean.value()).isLessThan(new BigDecimal("600"));
    }

    @Test
    void fixture10MarshalZeroExcluded() {
        assertThat(store.qualityReport().finding("Q12-MARSHAL-ZERO")).isEqualTo(473692);
    }

    @Test
    void g1DetectionSelectsDelayedTripsAndG2CarriesCaveats() {
        var detection = new DeterministicAnomalyDetectionService(metrics, contributions, capabilities, store);
        AnomalyDetectionResult g1 = detection.detect(PINNACLE, LocalDate.parse("2026-06-08"));
        var selected = g1.selectedIssue().orElseThrow();
        assertThat(selected.metricId()).isEqualTo(MetricId.M01_DELAYED_TRIP_RATE);
        assertThat(selected.severity()).isEqualTo("HIGH");
        assertThat(selected.impact().excessEvents()).isBetween(1900L, 1925L);
        assertThat(selected.impact().affectedRiderLegs()).isEqualTo(7780);
        assertThat(selected.impact().excessRiderLegs()).isBetween(3390L, 3440L);
        assertThat(selected.meetsConfiguredTarget()).isFalse();

        AnomalyDetectionResult healthy = detection.detect(new TenantContext("catalyst-Sac"), LocalDate.parse("2026-06-08"));
        assertThat(healthy.candidates().stream().filter(c -> c.metricId() == MetricId.M01_DELAYED_TRIP_RATE).findFirst().orElseThrow().classification())
                .isEqualTo(AnomalyClassification.HEALTHY);

        AnomalyDetectionResult g2 = detection.detect(VANTA_AUS, LocalDate.parse("2026-08-01"));
        assertThat(g2.selectedIssue()).isPresent();
        assertThat(g2.materialCandidates()).extracting("metricId").contains(MetricId.M01_DELAYED_TRIP_RATE);
        var matrix = capabilities.matrix(VANTA_AUS);
        assertThat(matrix.isUnsupported(MetricId.M10_COST_PER_BILLED_KM)).isTrue();
        assertThat(matrix.forMetric(MetricId.M11_LOW_DRIVER_RATING_RATE)).get().extracting(CapabilityStatus::support).isEqualTo(CapabilityStatus.Support.DERIVABLE);
        var tools = new WorkerContributionTools(metrics, contributions, capabilities);
        var g2Window = MetricWindow.trailingWeek(LocalDate.parse("2026-08-01"));
        var feedback = tools.feedback(VANTA_AUS, g2Window, MetricWindow.priorFourWeeks(g2Window), Map.of());
        assertThat(feedback.caveats()).anyMatch(c -> c.startsWith("Low feedback coverage: 3.9%"));
        var cost = tools.costBilling(VANTA_AUS, g2Window, MetricWindow.priorFourWeeks(g2Window), Map.of());
        assertThat(cost.caveats()).anyMatch(c -> c.contains("zero km"));
    }
}
