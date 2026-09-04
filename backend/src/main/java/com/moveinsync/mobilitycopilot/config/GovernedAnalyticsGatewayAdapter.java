package com.moveinsync.mobilitycopilot.config;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.application.AnomalyDetectionService;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyCandidate;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyDetectionResult;
import com.moveinsync.mobilitycopilot.anomaly.tools.WorkerContributionTools;
import com.moveinsync.mobilitycopilot.anomaly.tools.WorkerEvidence;
import com.moveinsync.mobilitycopilot.metrics.application.CapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.application.ContributionService;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRow;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.DistributionRow;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Connects the workflow to the governed DuckDB analytics boundary without exposing SQL or rows. */
@Component
@ConditionalOnProperty(name = "mobility.workflow.analytics-gateway", havingValue = "governed", matchIfMissing = true)
public final class GovernedAnalyticsGatewayAdapter implements AnalyticsGateway {

    private final AnomalyDetectionService anomalyDetection;
    private final WorkerContributionTools workers;
    private final MetricService metrics;
    private final CapabilityMatrixService capabilities;
    private final ContributionService contributions;

    public GovernedAnalyticsGatewayAdapter(
            AnomalyDetectionService anomalyDetection,
            WorkerContributionTools workers,
            MetricService metrics,
            CapabilityMatrixService capabilities,
            ContributionService contributions) {
        this.anomalyDetection = anomalyDetection;
        this.workers = workers;
        this.metrics = metrics;
        this.capabilities = capabilities;
        this.contributions = contributions;
    }

    @Override
    public DetectionSnapshot detect(TenantContext tenant, LocalDate asOfDate) {
        AnomalyDetectionResult result = anomalyDetection.detect(tenant, asOfDate);
        return new DetectionSnapshot(
                result.businessUnit(),
                result.asOfDate(),
                result.dataVersion(),
                result.ruleVersion(),
                result.candidates().stream().map(GovernedAnalyticsGatewayAdapter::candidate).toList(),
                result.dataQualityNotes().stream()
                        .map(note -> new DetectionSnapshot.DataQualityNote(note.findingId(), note.eventType(), note.note()))
                        .toList());
    }

    @Override
    public WorkerEvidenceDto runWorker(
            String worker,
            TenantContext tenant,
            WindowDto current,
            WindowDto baseline,
            Map<String, String> filters) {
        WorkerEvidence evidence = workers.run(worker, tenant, window(current), window(baseline), filters);
        return new WorkerEvidenceDto(
                evidence.worker(),
                evidence.businessUnit(),
                evidence.metrics(),
                evidence.rankings().stream().map(GovernedAnalyticsGatewayAdapter::ranking).toList(),
                evidence.distributions().stream().map(GovernedAnalyticsGatewayAdapter::distribution).toList(),
                evidence.directFindings(),
                evidence.caveats(),
                evidence.supported());
    }

    @Override
    public MetricResult metric(MetricQuery query) {
        return metrics.query(query);
    }

    @Override
    public List<CapabilityGap> capabilities(TenantContext tenant) {
        return capabilities.matrix(tenant).statuses().stream()
                .map(status -> new CapabilityGap(status.analysis(), status.support().name(), status.reason(), status.metrics()))
                .toList();
    }

    @Override
    public List<PeerValueDto> crossTenantPeers(MetricId metricId, WindowDto window) {
        if (metricId != MetricId.M01_DELAYED_TRIP_RATE) {
            throw new IllegalArgumentException("Cross-tenant peers are governed only for M01_DELAYED_TRIP_RATE");
        }
        return contributions.crossTenantDelayedTripRate(window(window)).stream()
                .map(peer -> new PeerValueDto(peer.businessUnit(), peer.numerator(), peer.denominator(), peer.value()))
                .toList();
    }

    private static DetectionSnapshot.IssueCandidate candidate(AnomalyCandidate value) {
        return new DetectionSnapshot.IssueCandidate(
                value.anomalyId(),
                value.metricId(),
                value.metric(),
                value.classification().name(),
                value.severity(),
                value.deltaPoints(),
                value.relativeChange(),
                value.configuredTarget(),
                value.meetsConfiguredTarget(),
                value.impact().excessEvents(),
                value.impact().affectedRiderLegs(),
                value.impact().excessRiderLegs(),
                value.confidence().overall(),
                value.priorityScore(),
                value.reasons());
    }

    private static WorkerEvidenceDto.Ranking ranking(ContributionRanking value) {
        return new WorkerEvidenceDto.Ranking(
                value.evidenceId(),
                value.metricId(),
                value.dimension().name(),
                value.minimumVolume(),
                value.rows().stream().map(GovernedAnalyticsGatewayAdapter::row).toList(),
                value.allQualifiedIncreased(),
                value.source(),
                value.contractVersion(),
                value.dataVersion(),
                value.caveats());
    }

    private static WorkerEvidenceDto.Ranking.Row row(ContributionRow value) {
        return new WorkerEvidenceDto.Ranking.Row(
                value.member(), value.currentValue(), value.baselineValue(), value.delta(),
                value.currentNumerator(), value.currentDenominator(), value.shareOfCurrentNumerator(), value.qualified());
    }

    private static WorkerEvidenceDto.Distribution distribution(Distribution value) {
        return new WorkerEvidenceDto.Distribution(
                value.evidenceId(),
                value.metricId(),
                value.category(),
                value.currentTotal(),
                value.baselineTotal(),
                value.rows().stream().map(GovernedAnalyticsGatewayAdapter::row).toList(),
                value.source(),
                value.contractVersion(),
                value.dataVersion());
    }

    private static WorkerEvidenceDto.Distribution.Row row(DistributionRow value) {
        return new WorkerEvidenceDto.Distribution.Row(
                value.category(), value.count(), value.share(), value.baselineCount(), value.baselineShare());
    }

    private static MetricWindow window(WindowDto value) {
        return new MetricWindow(value.start(), value.end());
    }
}
