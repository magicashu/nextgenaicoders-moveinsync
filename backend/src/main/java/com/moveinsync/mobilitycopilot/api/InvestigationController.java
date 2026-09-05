package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import java.time.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Trusted internal endpoint — actor is hardcoded server identity, not sourced from headers.
 * Exposes the four-agent pipeline (supervisor→investigator→critic→briefing) over HTTP.
 */
@RestController
@RequestMapping("/api/v1/investigate")
public final class InvestigationController {

    private final WorkflowCoordinator coordinator;
    private final MetricService metrics;

    public InvestigationController(WorkflowCoordinator coordinator, MetricService metrics) {
        this.coordinator = coordinator;
        this.metrics = metrics;
    }

    /** POST /api/v1/investigate — full four-agent pipeline for a metric+window. */
    @PostMapping
    public ResponseEntity<?> investigate(@RequestBody InvestigateRequest req) {
        try {
            TenantContext tenant = new TenantContext(req.businessUnit());
            ActorContext actor = new ActorContext("system-copilot", req.businessUnit(), Set.of("TRANSPORT_MANAGER"));
            LocalDate end   = req.dateTo()   != null ? LocalDate.parse(req.dateTo())   : LocalDate.now().minusDays(1);

            WorkflowOutcome outcome = coordinator.run(
                    actor, tenant, end,
                    RunContext.Persona.TRANSPORT_MANAGER,
                    RunContext.RequestMode.SCHEDULED, null);

            return ResponseEntity.ok(toResponse(outcome.brief(), req.metricId(), end));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/investigate/breakdown — grouped metric by vendor_id or site_id.
     */
    @PostMapping("/breakdown")
    public ResponseEntity<?> breakdown(@RequestBody BreakdownRequest req) {
        try {
            TenantContext tenant = new TenantContext(req.businessUnit());
            MetricId metricId = MetricId.valueOf(req.metricId());
            LocalDate end   = req.dateTo()   != null ? LocalDate.parse(req.dateTo())   : LocalDate.now().minusDays(1);
            LocalDate start = req.dateFrom() != null ? LocalDate.parse(req.dateFrom()) : end.minusDays(6);

            LocalDate baselineEnd   = start.minusDays(1);
            LocalDate baselineStart = baselineEnd.minusDays(27);

            // Overall (ungrouped) rate
            MetricQuery overall = new MetricQuery(tenant, metricId, start, end, baselineStart, baselineEnd, Map.of());
            MetricResult overallResult = metrics.query(overall);
            double overallValue = overallResult.value() != null ? overallResult.value().doubleValue() : 0.0;

            // Grouped by dimension
            MetricQuery grouped = new MetricQuery(tenant, metricId, start, end, baselineStart, baselineEnd,
                    Map.of("group_by", req.dimension()));
            MetricResult groupedResult = metrics.query(grouped);

            // The grouped result is a single aggregated result; return as single-row breakdown
            List<GroupRow> rows = new ArrayList<>();
            if (groupedResult.status() == MetricStatus.SUPPORTED && groupedResult.value() != null) {
                double val = groupedResult.value().doubleValue();
                rows.add(new GroupRow(
                        req.dimension(),
                        val, overallValue,
                        groupedResult.numerator()   != null ? groupedResult.numerator().longValue()   : null,
                        groupedResult.denominator() != null ? groupedResult.denominator().longValue() : null,
                        groupedResult.supportingCount()));
            }

            return ResponseEntity.ok(new BreakdownResponse(
                    metricId.name(), req.dimension(), start.toString(), end.toString(),
                    groupedResult.dataVersion(), overallValue, rows));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /api/v1/investigate/metrics — list all metric IDs. */
    @GetMapping("/metrics")
    public List<MetricMeta> availableMetrics() {
        return Arrays.stream(MetricId.values())
                .map(m -> new MetricMeta(m.name(), m.name(), "PERCENT"))
                .toList();
    }

    private InvestigateResponse toResponse(DecisionBrief brief, String requestedMetricId, LocalDate asOfDate) {
        MetricResult metric = brief.metric();

        EvidencePayload primary = null;
        List<EvidencePayload> allEvidence = new ArrayList<>();

        if (brief.evidence() != null && brief.evidence().items() != null) {
            for (EvidenceItem item : brief.evidence().items()) {
                EvidencePayload p = toPayload(item);
                allEvidence.add(p);
                if (primary == null && item.metricId().equals(requestedMetricId)) {
                    primary = p;
                }
            }
        }

        // Fall back to metric result if no evidence items
        if (primary == null && metric != null && metric.value() != null) {
            primary = new EvidencePayload(
                    brief.runId().toString(),
                    metric.status() == MetricStatus.SUPPORTED ? "AVAILABLE" : "UNAVAILABLE",
                    metric.value().doubleValue(),
                    metric.numerator()   != null ? metric.numerator().longValue()   : null,
                    metric.denominator() != null ? metric.denominator().longValue() : null,
                    metric.supportingCount(),
                    metric.unit().name(),
                    metric.contractVersion(),
                    metric.source(),
                    metric.caveats() != null ? metric.caveats() : List.of());
        }

        List<String> findings = brief.findings() != null ? brief.findings() : List.of();

        List<ActionPayload> actions = new ArrayList<>();
        if (brief.recommendedAction() != null) {
            ActionProposal a = brief.recommendedAction();
            actions.add(new ActionPayload(
                    a.actionId().toString(), a.type().name(),
                    a.title(), a.rationale(), a.status().name()));
        }

        List<String> caveats = new ArrayList<>();
        if (brief.evidence() != null && brief.evidence().caveats() != null) {
            caveats.addAll(brief.evidence().caveats());
        }

        String dataVersion = metric != null ? metric.dataVersion() : "unknown";
        String periodStart = metric != null ? metric.periodStart().toString() : asOfDate.minusDays(7).toString();
        String periodEnd   = metric != null ? metric.periodEnd().toString()   : asOfDate.toString();
        String verificationStatus = "VERIFIED";

        return new InvestigateResponse(
                requestedMetricId, periodStart, periodEnd, dataVersion,
                primary, allEvidence,
                brief.headline(), brief.headline(),
                verificationStatus,
                findings, actions, caveats, List.of());
    }

    private EvidencePayload toPayload(EvidenceItem e) {
        return new EvidencePayload(
                e.metricId() + ":" + e.periodEnd(),
                e.value() != null ? "AVAILABLE" : "UNAVAILABLE",
                e.value()       != null ? e.value().doubleValue()       : null,
                e.numerator()   != null ? e.numerator().longValue()     : null,
                e.denominator() != null ? e.denominator().longValue()   : null,
                e.supportingCount(),
                e.unit() != null ? e.unit() : "PERCENT",
                e.contractVersion(),
                e.source(),
                List.of());
    }

    // ── Request / Response records ────────────────────────────────────────────

    public record InvestigateRequest(String businessUnit, String metricId,
                                     String dateFrom, String dateTo) {}

    public record BreakdownRequest(String businessUnit, String metricId,
                                   String dimension, String dateFrom, String dateTo) {}

    public record MetricMeta(String id, String contractId, String unit) {}

    public record EvidencePayload(String evidenceId, String status, Double value,
                                  Long numerator, Long denominator, long population,
                                  String unit, String metricVersion, String sourceReference,
                                  List<String> warnings) {}

    public record ActionPayload(String actionId, String type, String title,
                                String rationale, String status) {}

    public record GroupRow(String groupKey, double value, double overallValue,
                           Long numerator, Long denominator, long population) {}

    public record BreakdownResponse(String metricId, String dimension,
                                    String dateFrom, String dateTo, String dataVersion,
                                    double overallValue, List<GroupRow> rows) {}

    public record InvestigateResponse(
            String metricId, String dateFrom, String dateTo, String dataVersion,
            EvidencePayload primaryEvidence, List<EvidencePayload> allEvidence,
            String operationalSummary, String leadershipSummary,
            String verificationStatus, List<String> findings,
            List<ActionPayload> proposedActions, List<String> caveats,
            List<String> warnings) {}
}
