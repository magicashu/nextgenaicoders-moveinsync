package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyCandidate;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyClassification;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyDetectionResult;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyThresholds;
import com.moveinsync.mobilitycopilot.anomaly.tools.WorkerContributionTools;
import com.moveinsync.mobilitycopilot.anomaly.tools.WorkerEvidence;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.DuckDbAnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb.FixtureStores;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbCapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbContributionService;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.DuckDbMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SevenFileFixtureAnomalyTest {

    private static final TenantContext PINNACLE = new TenantContext("pinnacle-Slc");
    private static final LocalDate AS_OF = LocalDate.parse("2026-06-08");

    private final DuckDbAnalyticsStore store = FixtureStores.sevenFileFixture();
    private final DuckDbCapabilityMatrixService capabilities = new DuckDbCapabilityMatrixService(store);
    private final DuckDbMetricService metrics = new DuckDbMetricService(store, capabilities);
    private final DuckDbContributionService contributions = new DuckDbContributionService(store);
    /** The fixture has 140 trips a week, so the volume floor is lowered to exercise the rule paths. */
    private final AnomalyThresholds thresholds = new AnomalyThresholds(100, new BigDecimal("3.00"), new BigDecimal("0.25"),
            new BigDecimal("2.0"), new BigDecimal("0.50"), "anomaly-rules-v1-fixture");
    private final DeterministicAnomalyDetectionService detection =
            new DeterministicAnomalyDetectionService(metrics, contributions, capabilities, store, thresholds);
    private final WorkerContributionTools tools = new WorkerContributionTools(metrics, contributions, capabilities);

    @Test
    void detectsRanksAndEstimatesImpactDeterministically() {
        AnomalyDetectionResult result = detection.detect(PINNACLE, AS_OF);

        assertThat(result.healthy()).isFalse();
        AnomalyCandidate selected = result.selectedIssue().orElseThrow();
        assertThat(selected.metricId()).isIn(MetricId.M01_DELAYED_TRIP_RATE, MetricId.M04_ON_TIME_PICKUP_RATE, MetricId.M16_TRACKING_GAP_RATE);
        AnomalyCandidate m01 = result.candidates().stream().filter(c -> c.metricId() == MetricId.M01_DELAYED_TRIP_RATE).findFirst().orElseThrow();
        assertThat(m01.classification()).isEqualTo(AnomalyClassification.OPERATIONAL_ANOMALY);
        assertThat(m01.severity()).isEqualTo("HIGH");
        assertThat(m01.deltaPoints()).isEqualByComparingTo("15.00");
        assertThat(m01.relativeChange()).isEqualByComparingTo("1.5000");
        assertThat(m01.meetsConfiguredTarget()).isFalse();
        assertThat(m01.impact().excessEvents()).isEqualTo(21);
        assertThat(m01.impact().affectedRiderLegs()).isEqualTo(70);
        assertThat(m01.impact().excessRiderLegs()).isEqualTo(42);
        assertThat(m01.confidence().overall()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(m01.priorityScore()).isPositive();

        AnomalyCandidate m04 = result.candidates().stream().filter(c -> c.metricId() == MetricId.M04_ON_TIME_PICKUP_RATE).findFirst().orElseThrow();
        assertThat(m04.classification()).isEqualTo(AnomalyClassification.OPERATIONAL_ANOMALY);
        assertThat(m04.deltaPoints()).as("adverse delta for a higher-is-better metric").isEqualByComparingTo("23.26");
        AnomalyCandidate m16 = result.candidates().stream().filter(c -> c.metricId() == MetricId.M16_TRACKING_GAP_RATE).findFirst().orElseThrow();
        assertThat(m16.classification()).as("doubling rule").isEqualTo(AnomalyClassification.OPERATIONAL_ANOMALY);
        AnomalyCandidate m06 = result.candidates().stream().filter(c -> c.metricId() == MetricId.M06_NO_SHOW_RATE).findFirst().orElseThrow();
        assertThat(m06.classification()).isEqualTo(AnomalyClassification.HEALTHY);
        assertThat(m06.priorityScore()).isEqualByComparingTo("0");
        assertThat(result.candidates().stream().map(AnomalyCandidate::metricId)).doesNotContain(MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90);
    }

    @Test
    void healthyTenantProducesNoForcedAnomaly() {
        AnomalyDetectionResult orbit = detection.detect(new TenantContext("orbit-Slc"), AS_OF);
        assertThat(orbit.healthy()).isTrue();
        assertThat(orbit.selectedIssue()).isEmpty();
        assertThat(orbit.candidates()).allMatch(c -> c.classification() != AnomalyClassification.OPERATIONAL_ANOMALY);
    }

    @Test
    void classifiesTheSignOffStepAsDataRegimeChangeNotAnomaly() {
        AnomalyDetectionResult result = detection.detect(PINNACLE, AS_OF);
        assertThat(result.dataQualityNotes()).hasSize(1);
        var note = result.dataQualityNotes().getFirst();
        assertThat(note.eventType()).isEqualTo("EMPLOYEE_SIGN_OFF_TIME_VIOLATION");
        assertThat(note.afterWeekStart()).isEqualTo(LocalDate.parse("2026-05-18"));
        assertThat(result.candidates()).noneMatch(c -> c.metricId() == MetricId.M13_ALERT_RATE && c.classification() == AnomalyClassification.OPERATIONAL_ANOMALY);
    }

    @Test
    void sevenWorkerToolsReturnTypedEvidence() {
        MetricWindow current = MetricWindow.trailingWeek(AS_OF);
        MetricWindow baseline = MetricWindow.priorFourWeeks(current);
        for (String worker : WorkerContributionTools.WORKERS) {
            WorkerEvidence evidence = tools.run(worker, PINNACLE, current, baseline, Map.of());
            assertThat(evidence.worker()).isEqualTo(worker);
            assertThat(evidence.businessUnit()).isEqualTo("pinnacle-Slc");
            assertThat(evidence.metrics().size() + evidence.rankings().size() + evidence.distributions().size()).isPositive();
        }
        WorkerEvidence site = tools.siteShiftDirection(PINNACLE, current, baseline, Map.of());
        assertThat(site.rankings()).hasSize(3);
        WorkerEvidence cost = tools.costBilling(PINNACLE, current, baseline, Map.of());
        assertThat(cost.directFindings().getFirst()).contains("did not rise");
        WorkerEvidence feedback = tools.feedback(PINNACLE, current, baseline, Map.of());
        assertThat(feedback.directFindings().getFirst()).contains("flat");
        WorkerEvidence tracking = tools.trackingSafetyAlerts(PINNACLE, current, baseline, Map.of());
        assertThat(tracking.directFindings()).anyMatch(f -> f.contains("doubled"));
        assertThat(tracking.caveats()).anyMatch(c -> c.contains("Sev-1/2"));
        WorkerEvidence orbitCost = tools.costBilling(new TenantContext("orbit-Slc"), current, baseline, Map.of());
        assertThat(orbitCost.caveats()).anyMatch(c -> c.contains("zero km"));
    }
}
