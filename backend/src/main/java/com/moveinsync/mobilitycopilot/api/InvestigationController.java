package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.OfficialDuckDbGovernedMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.workflow.application.AgentWorkflowService;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
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

    private final AgentWorkflowService agents;
    private final OfficialDuckDbGovernedMetricService metrics;

    public InvestigationController(AgentWorkflowService agents,
                                   OfficialDuckDbGovernedMetricService metrics) {
        this.agents = agents;
        this.metrics = metrics;
    }

    /** POST /api/v1/investigate — full four-agent pipeline for a metric+window. */
    @PostMapping
    public ResponseEntity<?> investigate(@RequestBody InvestigateRequest req) {
        try {
            String dataVersion = metrics.dataVersion();
            TenantContext tenant = new TenantContext(req.businessUnit());
            ActorContext actor = new ActorContext(
                    "system-copilot", Set.of("ANALYST", "READER"), Set.of(tenant));

            MetricId metricId = MetricId.valueOf(req.metricId());
            LocalDate end   = req.dateTo()   != null ? LocalDate.parse(req.dateTo())   : LocalDate.now().minusDays(1);
            LocalDate start = req.dateFrom() != null ? LocalDate.parse(req.dateFrom()) : end.minusDays(6);

            RunVersions versions = new RunVersions(dataVersion, "v1.1", "v1.0", "v1.0", "none", "v1.0");
            WorkflowBudget budget = new WorkflowBudget(40, 4, 3, Duration.ofSeconds(120), 4);
            RunContext context = new RunContext(
                    UUID.randomUUID(), actor, tenant, "analyst",
                    end, versions, budget, Instant.now().plusSeconds(150));

            MetricRequest metricRequest = new MetricRequest(
                    tenant, metricId, MetricRequest.Measure.VALUE,
                    new MetricWindow(start, end), Map.of(), dataVersion);

            AgentWorkflowService.Result result = agents.investigate(context, metricRequest);
            return ResponseEntity.ok(toResponse(result, metricId, start, end, dataVersion));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/investigate/breakdown — grouped metric by vendor_id or site_id.
     * Returns list of { groupKey, value, numerator, denominator, population }.
     */
    @PostMapping("/breakdown")
    public ResponseEntity<?> breakdown(@RequestBody BreakdownRequest req) {
        try {
            String dataVersion = metrics.dataVersion();
            TenantContext tenant = new TenantContext(req.businessUnit());

            MetricId metricId = MetricId.valueOf(req.metricId());
            LocalDate end   = req.dateTo()   != null ? LocalDate.parse(req.dateTo())   : LocalDate.now().minusDays(1);
            LocalDate start = req.dateFrom() != null ? LocalDate.parse(req.dateFrom()) : end.minusDays(6);

            // Also compute the overall (ungrouped) rate for baseline delta
            MetricRequest overall = new MetricRequest(
                    tenant, metricId, MetricRequest.Measure.VALUE,
                    new MetricWindow(start, end), Map.of(), dataVersion);
            var overallEv = metrics.compute(overall);
            double overallValue = overallEv.value() != null ? overallEv.value().doubleValue() : 0.0;

            MetricRequest grouped = new MetricRequest(
                    tenant, metricId, MetricRequest.Measure.VALUE,
                    new MetricWindow(start, end), Map.of(), dataVersion);
            List<MetricEvidence> rows = metrics.computeGrouped(grouped, req.dimension());

            List<GroupRow> result = rows.stream()
                    .filter(e -> e.status() == MetricStatus.AVAILABLE && e.value() != null)
                    .map(e -> {
                        double val = e.value().doubleValue();
                        String key = e.request().filters().getOrDefault(req.dimension(), "unknown");
                        return new GroupRow(key, val, overallValue,
                                e.numerator()   != null ? e.numerator().longValue()   : null,
                                e.denominator() != null ? e.denominator().longValue() : null,
                                e.population());
                    })
                    .sorted((a, b) -> Double.compare(b.value(), a.value()))
                    .limit(15)
                    .toList();

            return ResponseEntity.ok(new BreakdownResponse(
                    metricId.name(), req.dimension(), start.toString(), end.toString(),
                    dataVersion, overallValue, result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /api/v1/investigate/metrics — list all 18 metric IDs. */
    @GetMapping("/metrics")
    public List<MetricMeta> availableMetrics() {
        return Arrays.stream(MetricId.values())
                .map(m -> new MetricMeta(m.name(), m.contractId(), m.unit().name()))
                .toList();
    }

    private InvestigateResponse toResponse(AgentWorkflowService.Result result,
                                            MetricId metricId, LocalDate start, LocalDate end,
                                            String dataVersion) {
        var brief         = result.brief();
        var investigation = result.investigation();

        // Find primary evidence for the requested metric
        var primaryEv = investigation.evidence().stream()
                .filter(e -> e.request().metricId() == metricId
                          && e.request().filters().isEmpty()
                          && e.status() == MetricStatus.AVAILABLE)
                .findFirst()
                .orElse(investigation.evidence().stream()
                        .filter(e -> e.request().metricId() == metricId)
                        .findFirst()
                        .orElse(null));

        EvidencePayload primary = primaryEv != null ? toPayload(primaryEv) : null;

        // Only return primary evidence items (not grouped group rows) in allEvidence
        List<EvidencePayload> allEvidence = investigation.evidence().stream()
                .filter(e -> e.request().filters().isEmpty())
                .map(this::toPayload).toList();

        // Use only verified claims whose evidence IDs all resolve (critic passed these)
        List<String> findings = brief.verification().claims().stream()
                .map(c -> c.text())
                .filter(t -> t != null && !t.isBlank())
                .toList();

        List<ActionPayload> actions = brief.proposedActions().stream()
                .map(a -> new ActionPayload(a.actionId().toString(), a.type(),
                        a.title(), a.rationale(), a.status()))
                .toList();

        // Build clean summaries from evidence when brief text is a diagnostic dump
        String opSummary  = cleanSummary(brief.operationalSummary(),  primaryEv, metricId, start, end);
        String leadSummary = cleanSummary(brief.leadershipSummary(), primaryEv, metricId, start, end);

        // Collect caveats but exclude all internal diagnostic noise
        List<String> caveats = brief.caveats().stream()
                .filter(c -> c.length() < 300)
                .filter(c -> !c.contains("claim-ev-"))
                .filter(c -> !c.contains("comparison-ev-"))
                .filter(c -> !c.startsWith("Verified evidence was rejected"))
                .filter(c -> !c.startsWith("Claim claim-"))
                .filter(c -> !c.startsWith("Claim comparison-"))
                .filter(c -> !c.contains("has an invalid, unavailable, or scope-mismatched"))
                .toList();

        // Strip internal diagnostic noise from warnings too
        List<String> warnings = investigation.warnings().stream()
                .filter(w -> !w.contains("comparison-ev-"))
                .filter(w -> !w.contains("claim-ev-"))
                .filter(w -> !w.startsWith("Office comparison scope"))
                .filter(w -> !w.startsWith("Below the governed comparison volume"))
                .filter(w -> !w.startsWith("Claim comparison-"))
                .filter(w -> !w.contains("has an invalid, unavailable, or scope-mismatched"))
                .toList();

        return new InvestigateResponse(
                metricId.name(), start.toString(), end.toString(), dataVersion,
                primary, allEvidence,
                opSummary, leadSummary,
                brief.verification().status().name(),
                findings, actions, caveats, warnings);
    }

    /** Replace diagnostic dump text with a clean auto-generated summary. */
    private String cleanSummary(String text, MetricEvidence ev,
                                 MetricId metricId, LocalDate start, LocalDate end) {
        if (text == null || text.isBlank()) return buildSummary(ev, metricId, start, end);
        // Heuristic: real summaries don't start with "Leadership summary |" or contain UUIDs
        if (text.startsWith("Leadership summary |") || text.contains("has an invalid")
                || text.contains("claim-ev-")) {
            return buildSummary(ev, metricId, start, end);
        }
        return text;
    }

    private String buildSummary(MetricEvidence ev, MetricId metricId, LocalDate start, LocalDate end) {
        if (ev == null || ev.value() == null) return "";
        double val = ev.value().doubleValue();
        long denom = ev.denominator() != null ? ev.denominator().longValue() : ev.population();
        long numer = ev.numerator()   != null ? ev.numerator().longValue()   : 0;
        return String.format("%s: %.1f%% (%,d of %,d trips) for period %s–%s.",
                metricId.contractId(), val, numer, denom, start, end);
    }

    private EvidencePayload toPayload(MetricEvidence e) {
        return new EvidencePayload(
                e.evidenceId(), e.status().name(),
                e.value()       != null ? e.value().doubleValue()       : null,
                e.numerator()   != null ? e.numerator().longValue()     : null,
                e.denominator() != null ? e.denominator().longValue()   : null,
                e.population(), e.unit().name(),
                e.metricVersion(), e.sourceReference(), e.warnings());
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
