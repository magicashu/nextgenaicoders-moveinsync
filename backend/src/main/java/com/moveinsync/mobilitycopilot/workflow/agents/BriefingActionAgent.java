package com.moveinsync.mobilitycopilot.workflow.agents;

import tools.jackson.databind.JsonNode;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyFinding;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.domain.BriefingOutput;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A4 Briefing and Action-Drafting: composes the operations brief, the leadership narrative and one
 * bounded action proposal from verified claims only. The deterministic template is the guaranteed
 * path; model prose is accepted only when every sentence restates a verified claim id.
 */
@Component
public final class BriefingActionAgent {

    private final WorkflowProperties properties;
    private final ModelAssist assist;

    public BriefingActionAgent(WorkflowProperties properties, LanguageModelPort model) {
        this.properties = properties;
        this.assist = new ModelAssist(model, properties.modelTimeout(), 900);
    }

    public BriefingOutput compose(WorkflowRun run, List<WorkerEvidenceDto.Ranking> vendorRankings) {
        EvidencePackage evidence = run.evidence();
        DetectionSnapshot.IssueCandidate issue = run.selectedIssue();
        List<Claim> verified = evidence.claims().stream().filter(c -> run.verification() == null || !run.verification().removedClaimIds().contains(c.claimId())).toList();
        ActionProposal action = proposeAction(run, issue, vendorRankings, evidence);
        List<String> operations = new ArrayList<>();
        List<String> leadership = new ArrayList<>();
        MetricResult metric = issue.metric();
        String headline = "%s: %s reached %s%s in the week to %s, up from %s%s in the prior four weeks (configured target %s)".formatted(
                run.state().tenant().businessUnit(), metric.metricName().toLowerCase(Locale.ROOT), fmt(metric.value()), unit(metric),
                metric.periodEnd(), fmt(metric.baselineValue()), unit(metric), issue.configuredTarget() == null ? "not set" : fmt(issue.configuredTarget()) + unit(metric));
        for (Claim claim : verified) {
            if (claim.kind() == Claim.Kind.DIRECT || claim.kind() == Claim.Kind.INFERRED) {
                operations.add(claim.text() + " [" + String.join(", ", claim.evidenceIds()) + "]");
            }
        }
        for (Claim claim : verified) {
            if (claim.kind() == Claim.Kind.CAVEAT) {
                operations.add("Caveat: " + claim.text());
            }
        }
        leadership.add(headline + ".");
        long excess = issue.excessEvents();
        if (excess > 0) {
            leadership.add("About %s excess %s affected roughly %s rider legs; confidence %s."
                    .formatted(fmtRounded(excess), metricNoun(issue.metricId()), fmtRounded(issue.excessRiderLegs()), run.verification() == null ? fmt(issue.confidence()) : fmt(run.verification().confidence())));
        }
        verified.stream().filter(c -> c.kind() == Claim.Kind.DIRECT).limit(3).forEach(c -> leadership.add(c.text()));
        verified.stream().filter(c -> c.kind() == Claim.Kind.CAVEAT).limit(3).forEach(c -> leadership.add("Note: " + c.text()));
        leadership.add("Recommended: " + action.title() + " (awaiting approval).");

        List<String> narrative = leadership;
        boolean modelAssisted = false;
        Optional<JsonNode> prose = assist.ask("briefing-action", Map.of(
                "verifiedClaims", verified.stream().map(c -> Map.of("id", c.claimId(), "text", c.text(), "kind", c.kind().name())).toList(),
                "anomaly", Map.of("metric", metric.metricName(), "current", String.valueOf(metric.value()), "baseline", String.valueOf(metric.baselineValue()),
                        "impact", excess, "confidence", String.valueOf(issue.confidence())),
                "allowedActions", List.of(ActionType.values())), run);
        if (prose.isPresent()) {
            List<String> modelLeadership = new ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (JsonNode id : prose.get().path("leadershipClaimIds")) {
                verified.stream().filter(c -> c.claimId().equals(id.asText()) && seen.add(c.claimId()))
                        .findFirst().ifPresent(c -> modelLeadership.add(c.text() + " [" + String.join(", ", c.evidenceIds()) + "]"));
            }
            if (!modelLeadership.isEmpty()) {
                modelLeadership.addFirst(headline + ".");
                modelLeadership.add("Recommended: " + action.title() + " (awaiting approval).");
                narrative = modelLeadership;
                modelAssisted = true;
            }
        }
        List<String> findings = new ArrayList<>(operations);
        DecisionBrief brief = new DecisionBrief(run.state().runId(), run.state().tenant().businessUnit(), run.state().asOfDate(), headline,
                metric, findings, action, evidence.bundle(), "AWAITING_APPROVAL");
        return new BriefingOutput(brief, operations, narrative, action, rationale(issue, vendorRankings), modelAssisted);
    }

    /** Healthy or report-only brief: same shape, no action pending. */
    public BriefingOutput composeHealthy(WorkflowRun run, MetricResult headlineMetric, EvidenceBundle bundle, String status, List<String> notes) {
        String headline = "%s: no material operational anomaly as of %s; %s is %s%s versus %s%s in the prior four weeks".formatted(
                run.state().tenant().businessUnit(), run.state().asOfDate(), headlineMetric.metricName().toLowerCase(Locale.ROOT),
                fmt(headlineMetric.value()), unit(headlineMetric), fmt(headlineMetric.baselineValue()), unit(headlineMetric));
        Instant now = Instant.now();
        ActionProposal none = new ActionProposal(UUID.randomUUID(), run.state().runId(), ActionType.DRAFT_COMMUNICATION,
                "No action recommended", "All sensed metrics are within the materiality rule; no approval request is raised.",
                Map.of("businessUnit", run.state().tenant().businessUnit(), "recommendation", "none"),
                bundle.items().isEmpty() ? run.context().dataVersion() : bundle.items().getFirst().dataVersion(), now, now.plus(properties.approvalTtl()),
                ActionStatus.DRAFT_REQUIRES_APPROVAL);
        List<String> findings = new ArrayList<>(notes);
        DecisionBrief brief = new DecisionBrief(run.state().runId(), run.state().tenant().businessUnit(), run.state().asOfDate(), headline,
                headlineMetric, findings, none, bundle, status);
        return new BriefingOutput(brief, findings, List.of(headline + ".", "No intervention is proposed."), none, "healthy", false);
    }

    ActionProposal proposeAction(WorkflowRun run, DetectionSnapshot.IssueCandidate issue, List<WorkerEvidenceDto.Ranking> vendorRankings, EvidencePackage evidence) {
        Instant now = Instant.now();
        Map<String, String> scope = new LinkedHashMap<>();
        scope.put("businessUnit", run.state().tenant().businessUnit());
        scope.put("metricId", issue.metricId().name());
        scope.put("windowEnd", issue.metric().periodEnd().toString());
        ActionType type;
        String title;
        String rationale;
        Optional<WorkerEvidenceDto.Ranking.Row> topSite = topQualified(run, "site_id");
        boolean singleVendor = com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier.singleVendorBlameSupported(vendorRankings);
        if (issue.metricId() == MetricId.M14_SEVERE_ALERT_RATE) {
            type = ActionType.CREATE_INVESTIGATION_TICKET;
            title = "Open a safety investigation ticket for the Sev-1/2 alert increase";
            rationale = "Severe alert rate at least doubled against the prior four weeks; safety issues are ticketed, never auto-escalated.";
        } else if (singleVendor) {
            WorkerEvidenceDto.Ranking.Row vendor = vendorRankings.getFirst().qualifiedRows().stream().filter(r -> r.delta().signum() > 0).findFirst().orElseThrow();
            type = ActionType.DRAFT_VENDOR_ESCALATION;
            scope.put("vendor_id", vendor.member());
            title = "Draft a vendor escalation for " + vendor.member();
            rationale = "Exactly one qualified vendor deteriorated while the others held; the draft still requires approval.";
        } else {
            type = ActionType.CREATE_SITE_SHIFT_WATCHLIST;
            topSite.ifPresent(s -> scope.put("site_id", s.member()));
            List<String> shiftBand = topQualifiedBand(run, "shift_id", 3);
            if (!shiftBand.isEmpty()) {
                scope.put("shift_id", String.join(",", shiftBand));
            }
            scope.put("followUp", ActionType.CREATE_INVESTIGATION_TICKET.name());
            scope.put("watchDays", "7");
            String shiftText = shiftBand.isEmpty() ? "" : " " + joinBand(shiftBand) + " shifts";
            title = topSite.map(s -> "Place " + s.member() + shiftText + " on a one-week watchlist and open an investigation ticket")
                    .orElse("Place the affected site-shift combinations on a one-week watchlist and open an investigation ticket");
            rationale = vendorRankings.isEmpty() || !vendorRankings.getFirst().allQualifiedIncreased()
                    ? "The change is concentrated by site and shift; a watchlist with an investigation ticket is the conservative first step."
                    : "Every qualified vendor rose together, so this is a site and shift pattern rather than a single-vendor failure; watch and investigate before escalating.";
        }
        return new ActionProposal(UUID.randomUUID(), run.state().runId(), type, title, rationale, scope, evidence.evidenceVersion(), now,
                now.plus(properties.approvalTtl()), ActionStatus.DRAFT_REQUIRES_APPROVAL);
    }

    /** Top deteriorating qualified members by share of the numerator, returned in natural order (e.g. shift times). */
    private static List<String> topQualifiedBand(WorkflowRun run, String dimension, int limit) {
        return run.investigations().stream().flatMap(i -> i.evidence().stream()).flatMap(e -> e.rankings().stream())
                .filter(r -> r.dimension().equals(dimension)).flatMap(r -> r.qualifiedRows().stream())
                .filter(r -> r.delta() != null && r.delta().signum() > 0)
                .sorted(java.util.Comparator.comparing((WorkerEvidenceDto.Ranking.Row r) -> r.shareOfCurrentNumerator() == null ? BigDecimal.ZERO : r.shareOfCurrentNumerator()).reversed())
                .map(WorkerEvidenceDto.Ranking.Row::member).distinct().limit(limit).sorted().toList();
    }

    private static String joinBand(List<String> members) {
        if (members.size() == 1) {
            return members.getFirst();
        }
        return String.join(", ", members.subList(0, members.size() - 1)) + " and " + members.getLast();
    }

    private static Optional<WorkerEvidenceDto.Ranking.Row> topQualified(WorkflowRun run, String dimension) {
        return run.investigations().stream().flatMap(i -> i.evidence().stream()).flatMap(e -> e.rankings().stream())
                .filter(r -> r.dimension().equals(dimension)).flatMap(r -> r.qualifiedRows().stream())
                .filter(r -> r.delta() != null && r.delta().signum() > 0)
                .max(java.util.Comparator.comparing(r -> r.shareOfCurrentNumerator() == null ? BigDecimal.ZERO : r.shareOfCurrentNumerator()));
    }

    private static String rationale(DetectionSnapshot.IssueCandidate issue, List<WorkerEvidenceDto.Ranking> vendorRankings) {
        return "Selected issue %s (%s, priority %s); vendor dispersion %s".formatted(issue.metricId(), issue.severity(), issue.priorityScore(),
                vendorRankings.isEmpty() ? "not measured" : vendorRankings.getFirst().allQualifiedIncreased() ? "all qualified vendors rose" : "mixed");
    }

    static boolean restatesVerifiedClaim(String text, List<Claim> verified) {
        return text != null && verified.stream().anyMatch(c -> c.text().equals(text));
    }

    private static String fmt(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(value.scale() > 2 ? 2 : Math.max(value.scale(), 0), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String fmtRounded(long value) {
        return String.format(Locale.ROOT, "%,d", value >= 1000 ? Math.round(value / 100.0) * 100 : value);
    }

    private static String unit(MetricResult metric) {
        return switch (metric.unit()) {
            case PERCENT -> "%";
            case MINUTES -> " min";
            case PER_1000_TRIPS -> " per 1,000 trips";
            default -> "";
        };
    }

    private static String metricNoun(MetricId id) {
        return switch (id) {
            case M01_DELAYED_TRIP_RATE -> "delayed trips";
            case M04_ON_TIME_PICKUP_RATE -> "late pickups";
            case M05_ON_TIME_DROP_RATE -> "late drops";
            case M06_NO_SHOW_RATE -> "no-shows";
            case M11_LOW_DRIVER_RATING_RATE -> "low ratings";
            default -> "events";
        };
    }
}
