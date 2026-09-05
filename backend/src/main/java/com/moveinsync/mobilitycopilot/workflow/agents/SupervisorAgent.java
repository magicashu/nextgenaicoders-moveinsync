package com.moveinsync.mobilitycopilot.workflow.agents;

import tools.jackson.databind.JsonNode;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.domain.Critique;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationPlan;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.investigation.AllowedDimensions;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A1 Supervisor: turns the selected issue into a bounded typed plan of registered tasks. A model may
 * propose the plan; the deterministic validator decides what survives, and the deterministic planner
 * is the guaranteed path.
 */
@Component
public final class SupervisorAgent {

    private final ModelAssist assist;

    public SupervisorAgent(LanguageModelPort model, WorkflowProperties properties) {
        this.assist = new ModelAssist(model, properties.toolTimeout(), 800);
    }

    /** Backwards-compatible scaffold entry point: plan for the headline delayed-trip issue. */
    public List<InvestigationTask> plan(com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState state) {
        Map<String, String> scope = Map.of("businessUnit", state.tenant().businessUnit(), "asOfDate", state.asOfDate().toString());
        return deterministicTasks(MetricId.M01_DELAYED_TRIP_RATE, Set.of(), scope);
    }

    public InvestigationPlan plan(WorkflowRun run) {
        DetectionSnapshot.IssueCandidate issue = run.selectedIssue();
        Set<String> disabled = new LinkedHashSet<>();
        for (AnalyticsGateway.CapabilityGap gap : run.capabilities()) {
            if (gap.unsupported()) {
                disabled.add(gap.analysis());
            }
        }
        Map<String, String> scope = Map.of("businessUnit", run.state().tenant().businessUnit(), "asOfDate", run.state().asOfDate().toString());
        List<InvestigationTask> deterministic = deterministicTasks(issue.metricId(), disabled, scope);
        List<String> notes = new ArrayList<>();
        if (run.planFeedback() != null) {
            notes.add("Correction cycle: " + String.join("; ", run.planFeedback().notes()));
        }
        Optional<JsonNode> proposal = assist.ask("supervisor", Map.of(
                "anomaly", Map.of("metricId", issue.metricId().name(), "value", issue.metric().value(), "baseline", issue.metric().baselineValue(),
                        "deltaPoints", issue.deltaPoints(), "severity", issue.severity(), "reasons", issue.reasons()),
                "capabilities", run.capabilities(),
                "workers", WorkerType.allowlist(),
                "budget", Map.of("maxTasks", WorkerType.values().length, "maxToolCalls", run.state().maxToolCalls() - run.state().toolCalls()),
                "feedback", run.planFeedback() == null ? List.of() : run.planFeedback().notes()), run::addModelUsage);
        if (proposal.isPresent()) {
            List<InvestigationTask> proposed = parseTasks(proposal.get(), scope, disabled, notes);
            if (!proposed.isEmpty()) {
                return new InvestigationPlan(issue.anomalyId(), proposed, Set.of(issue.metricId()), AllowedDimensions.KEYS,
                        stopConditions(run), proposal.get().path("rationale").asText("model plan"), true, notes);
            }
            notes.add("Model plan rejected; deterministic plan used");
        }
        return new InvestigationPlan(issue.anomalyId(), deterministic, Set.of(issue.metricId()), AllowedDimensions.KEYS,
                stopConditions(run), "Deterministic plan for " + issue.metricId(), false, notes);
    }

    static List<InvestigationTask> deterministicTasks(MetricId metricId, Set<String> disabledAnalyses, Map<String, String> scope) {
        List<WorkerType> order = switch (metricId) {
            case M01_DELAYED_TRIP_RATE, M02_DELAY_MINUTES, M03_DELAY_REASON_MIX, M04_ON_TIME_PICKUP_RATE, M05_ON_TIME_DROP_RATE ->
                    List.of(WorkerType.VENDOR, WorkerType.SITE_SHIFT_DIRECTION, WorkerType.DELAY_REASON, WorkerType.FEEDBACK, WorkerType.COST_BILLING, WorkerType.NO_SHOW_ROSTER);
            case M06_NO_SHOW_RATE, M07_DASHBOARD_CANCELLATION_RATE ->
                    List.of(WorkerType.NO_SHOW_ROSTER, WorkerType.SITE_SHIFT_DIRECTION, WorkerType.VENDOR, WorkerType.FEEDBACK);
            case M11_LOW_DRIVER_RATING_RATE, M12_MEAN_DRIVER_SAFETY_RATING ->
                    List.of(WorkerType.FEEDBACK, WorkerType.VENDOR, WorkerType.TRACKING_SAFETY, WorkerType.SITE_SHIFT_DIRECTION);
            case M13_ALERT_RATE, M14_SEVERE_ALERT_RATE, M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90, M16_TRACKING_GAP_RATE, M18_ESCORT_PRESENT_RATE ->
                    List.of(WorkerType.TRACKING_SAFETY, WorkerType.VENDOR, WorkerType.SITE_SHIFT_DIRECTION, WorkerType.FEEDBACK);
            default -> List.of(WorkerType.VENDOR, WorkerType.SITE_SHIFT_DIRECTION, WorkerType.COST_BILLING);
        };
        List<InvestigationTask> tasks = new ArrayList<>();
        for (WorkerType worker : order) {
            if (disabledAnalyses.contains(worker.capabilityAnalysis())) {
                continue;
            }
            tasks.add(new InvestigationTask(worker.id(), worker.question(), scope));
        }
        return tasks;
    }

    private static List<InvestigationTask> parseTasks(JsonNode node, Map<String, String> scope, Set<String> disabled, List<String> notes) {
        List<InvestigationTask> tasks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode task : node.path("tasks")) {
            String worker = task.path("worker").asText("");
            Optional<WorkerType> type = WorkerType.fromId(worker);
            if (type.isEmpty()) {
                notes.add("Rejected unknown worker '" + worker + "'");
                continue;
            }
            if (disabled.contains(type.get().capabilityAnalysis())) {
                notes.add("Removed task for unsupported analysis: " + worker);
                continue;
            }
            if (!seen.add(worker)) {
                continue;
            }
            Map<String, String> parameters = new LinkedHashMap<>(scope);
            JsonNode filters = task.path("filters");
            boolean valid = true;
            for (var entry : filters.properties()) {
                Optional<String> dimension = AllowedDimensions.normalise(entry.getKey());
                if (dimension.isEmpty()) {
                    notes.add("Rejected filter '" + entry.getKey() + "' for " + worker);
                    valid = false;
                    break;
                }
                parameters.put("filter." + dimension.get(), entry.getValue().asText());
            }
            if (!valid) {
                continue;
            }
            tasks.add(new InvestigationTask(worker, task.path("question").asText(type.get().question()), parameters));
            if (tasks.size() >= WorkerType.values().length) {
                break;
            }
        }
        return tasks;
    }

    private static List<String> stopConditions(WorkflowRun run) {
        return List.of(
                "tool calls <= " + run.state().maxToolCalls(),
                "investigation depth <= " + run.state().maxInvestigationSteps(),
                "correction cycles <= " + run.state().maxCorrectionCycles(),
                "stop when the site/shift concentration and vendor dispersion are measured");
    }

    static Critique noFeedback() {
        return new Critique(Critique.Verdict.PASS, List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }
}
