package com.moveinsync.mobilitycopilot.reporting.application;

import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps one RunView (one evidence bundle) into the operational and leadership sections. Both sections
 * are derived from the same claims, so their facts cannot diverge; {@link #assertNoDivergence} is the
 * guard the API tests run.
 */
@Component
public class BriefRenderer {

    private static final Map<MetricId, String> TARGETS = Map.of(
            MetricId.M01_DELAYED_TRIP_RATE, "≤ 10%", MetricId.M04_ON_TIME_PICKUP_RATE, "≥ 90%", MetricId.M06_NO_SHOW_RATE, "≤ 10%",
            MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90, "≤ 5 min at P90");
    static final String TARGET_LABEL = "Configured target, editable per tenant";
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");

    public ApiDtos.MorningBriefResponse render(RunView run, List<String> suggestedQuestions) {
        MetricResult metric = run.brief().metric();
        ApiDtos.Kpi headline = kpi(metric, run.brief().evidence().items().isEmpty() ? null : run.brief().evidence().items().getFirst().evidenceId(), "prior four complete weeks");
        List<ApiDtos.Kpi> supporting = new ArrayList<>();
        for (EvidenceItem item : run.brief().evidence().items()) {
            if (item.filters().isEmpty() && !item.evidenceId().equals(headline.evidenceId()) && !item.evidenceId().contains(":impact")
                    && !item.evidenceId().startsWith("peer:") && item.metricId().startsWith("M") && item.baselineValue() != null && supporting.size() < 6) {
                supporting.add(new ApiDtos.Kpi(item.metricId(), metricFromItem(item, metric), item.evidenceId(), "prior four complete weeks",
                        TARGETS.getOrDefault(MetricId.valueOf(item.metricId()), null), TARGETS.containsKey(MetricId.valueOf(item.metricId())) ? TARGET_LABEL : null, false));
            }
        }
        List<ApiDtos.Finding> findings = new ArrayList<>();
        List<ApiDtos.Finding> caveats = new ArrayList<>();
        for (RunView.Claim claim : run.claims()) {
            ApiDtos.Finding finding = new ApiDtos.Finding(claim.claimId(), claim.text(), claim.kind(), claim.evidenceIds(), claim.worker());
            if ("CAVEAT".equals(claim.kind())) {
                caveats.add(finding);
            } else {
                findings.add(finding);
            }
        }
        if (run.claims().isEmpty()) {
            for (int i = 0; i < run.brief().findings().size(); i++) {
                findings.add(new ApiDtos.Finding("f" + (i + 1), run.brief().findings().get(i), "DIRECT", List.of(headline.evidenceId()), "workflow"));
            }
            for (String caveat : run.brief().evidence().caveats()) {
                caveats.add(new ApiDtos.Finding("caveat", caveat, "CAVEAT", List.of(headline.evidenceId()), "system"));
            }
        }
        ApiDtos.ApprovalView approval = approvalView(run);
        ApiDtos.OperationsSection operations = new ApiDtos.OperationsSection(run.brief().headline(), run.brief().status(), headline, supporting, findings, caveats,
                run.recommendedAction() == null ? run.brief().recommendedAction() : run.recommendedAction(), approval, run.receipt());
        List<String> narrative = run.leadershipNarrative().isEmpty() ? List.of(run.brief().headline() + ".") : run.leadershipNarrative();
        String recommendation = run.recommendedAction() == null ? run.brief().recommendedAction().title() : run.recommendedAction().title();
        ApiDtos.LeadershipSection leadership = new ApiDtos.LeadershipSection(
                "%s — %s summary as of %s".formatted(run.businessUnit(), metric.metricName(), run.brief().asOfDate()),
                narrative, recommendation, String.join("\n", narrative));
        return new ApiDtos.MorningBriefResponse(run.runId(), run.runId(), run.businessUnit(), run.brief().asOfDate(), run.persona(), run.brief().status(),
                operations, leadership, run.brief().evidence(), trust(run), suggestedQuestions, run.errors());
    }

    public ApiDtos.TrustPanel trust(RunView run) {
        List<ApiDtos.TransitionView> transitions = run.transitions().stream()
                .map(t -> new ApiDtos.TransitionView(t.node(), t.subNode(), t.outcome(), t.durationMs(), t.startedAt(),
                        com.moveinsync.mobilitycopilot.observability.Redaction.attributes(t.attributes()))).toList();
        RunView.ModelUsageSummary usage = run.modelUsage() == null ? new RunView.ModelUsageSummary(0, 0, 0, 0, 0, "none") : run.modelUsage();
        RunView.Versions versions = run.versions() == null ? new RunView.Versions("workflow-v1", "prompts-v1", "metrics-v1.1", run.brief().metric().dataVersion(), "anomaly-rules-v1", "targets-v1") : run.versions();
        int toolCalls = (int) run.transitions().stream().filter(t -> t.subNode() != null && t.subNode().endsWith("execute_analysis") && "ok".equals(t.outcome())).count();
        return new ApiDtos.TrustPanel(run.runId(), run.traceId(), run.finalStep(), versions.dataVersion(), versions.contractVersion(), versions.workflowVersion(),
                versions.promptVersion(), usage.modelId(), versions.ruleVersion(), versions.targetVersion(), run.elapsedMs(), usage.modelCalls(), usage.fallbackCalls(),
                usage.inputTokens(), usage.outputTokens(), toolCalls, run.verification() == null ? null : run.verification().confidence(),
                run.verification() == null ? List.of() : run.verification().components(), run.capabilityGaps(), run.dataQualityNotes(), run.branchStatus(), transitions);
    }

    public ApiDtos.ApprovalView approvalView(RunView run) {
        if (run.approvalRequest() == null) {
            return null;
        }
        var request = run.approvalRequest();
        var proposal = "EDITED".equals(run.approvalStatus()) && run.recommendedAction() != null
                ? run.recommendedAction() : request.proposal();
        String consequence = switch (proposal.type()) {
            case CREATE_SITE_SHIFT_WATCHLIST -> "Creates a mock watchlist entry for the listed site and shifts (" + proposal.scope().getOrDefault("watchDays", "7") + " days) and opens a mock investigation ticket. No message is sent to any vendor.";
            case CREATE_INVESTIGATION_TICKET -> "Opens a mock investigation ticket in the tracking system. No external communication.";
            case DRAFT_VENDOR_ESCALATION -> "Drafts (does not send) a vendor escalation for review.";
            case DRAFT_COMMUNICATION -> "Drafts (does not send) a communication for review.";
        };
        return new ApiDtos.ApprovalView(request.approvalId(), proposal.actionId(), run.approvalStatus() == null ? "PENDING" : run.approvalStatus(), proposal.type().name(),
                proposal.title(), proposal.rationale(), proposal.scope(), request.evidenceVersion(), evidenceTimestamp(run), request.createdAt(), request.expiresAt(), consequence);
    }

    static Instant evidenceTimestamp(RunView run) {
        return run.startedAt() == null ? Instant.now() : run.startedAt();
    }

    private static ApiDtos.Kpi kpi(MetricResult metric, String evidenceId, String comparison) {
        String target = TARGETS.get(metric.metricId());
        boolean meets = target != null && metric.value() != null && meetsTarget(metric.metricId(), metric.value());
        return new ApiDtos.Kpi(metric.metricName(), metric, evidenceId, comparison, target, target == null ? null : TARGET_LABEL, meets);
    }

    private static boolean meetsTarget(MetricId id, BigDecimal value) {
        return switch (id) {
            case M01_DELAYED_TRIP_RATE, M06_NO_SHOW_RATE -> value.compareTo(BigDecimal.TEN) <= 0;
            case M04_ON_TIME_PICKUP_RATE -> value.compareTo(BigDecimal.valueOf(90)) >= 0;
            case M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90 -> value.compareTo(BigDecimal.valueOf(5)) <= 0;
            default -> false;
        };
    }

    private static MetricResult metricFromItem(EvidenceItem item, MetricResult template) {
        MetricId id = MetricId.valueOf(item.metricId());
        return new MetricResult(id, humanName(id), com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.valueOf(item.unit()), MetricStatus.SUPPORTED, item.value(),
                item.baselineValue(), item.delta(), item.numerator(), item.denominator(), item.supportingCount(), item.periodStart(), item.periodEnd(), item.filters(),
                item.contractVersion(), item.dataVersion(), item.source(), List.of());
    }

    static String humanName(MetricId id) {
        String[] parts = id.name().substring(4).toLowerCase().split("_");
        return Character.toUpperCase(parts[0].charAt(0)) + String.join(" ", parts).substring(1);
    }

    /** Every numeric token in the leadership narrative must appear in the evidence bundle or the operations findings. */
    public static List<String> assertNoDivergence(ApiDtos.MorningBriefResponse response) {
        List<String> unsupported = new ArrayList<>();
        StringBuilder corpus = new StringBuilder();
        corpus.append(' ').append(response.evidence().confidence()).append(' ').append(BigDecimal.valueOf(response.evidence().confidence()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        if (response.trust() != null && response.trust().confidence() != null) {
            corpus.append(' ').append(response.trust().confidence().toPlainString());
        }
        response.evidence().items().forEach(i -> {
            for (BigDecimal v : new BigDecimal[] {i.value(), i.baselineValue(), i.delta(), i.numerator(), i.denominator(), BigDecimal.valueOf(i.supportingCount())}) {
                if (v == null) {
                    continue;
                }
                corpus.append(' ').append(v.toPlainString()).append(' ').append(v.abs().stripTrailingZeros().toPlainString());
                if (v.abs().compareTo(BigDecimal.valueOf(1000)) >= 0) {
                    corpus.append(' ').append(v.abs().setScale(-2, java.math.RoundingMode.HALF_UP).toPlainString());
                }
            }
        });
        response.operations().findings().forEach(f -> corpus.append(' ').append(f.text()));
        response.operations().caveats().forEach(f -> corpus.append(' ').append(f.text()));
        corpus.append(' ').append(response.operations().headline());
        String haystack = corpus.toString().replace(",", "");
        for (String line : response.leadership().narrative()) {
            Matcher m = NUMBER.matcher(line);
            while (m.find()) {
                String token = m.group().replace(",", "");
                if (token.length() <= 1 || line.contains("2026-") && line.indexOf(m.group()) >= line.indexOf("2026-") && line.indexOf(m.group()) <= line.indexOf("2026-") + 10) {
                    continue;
                }
                if (!haystack.contains(token) && !haystack.contains(stripZeros(token))) {
                    unsupported.add(token + " in: " + line);
                }
            }
        }
        return unsupported;
    }

    private static String stripZeros(String token) {
        try {
            return new BigDecimal(token).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return token;
        }
    }
}
