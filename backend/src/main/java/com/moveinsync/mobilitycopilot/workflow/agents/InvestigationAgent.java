package com.moveinsync.mobilitycopilot.workflow.agents;

import tools.jackson.databind.JsonNode;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationNode;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.investigation.AllowedDimensions;
import com.moveinsync.mobilitycopilot.workflow.investigation.InvestigationTool;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerToolRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A2 Investigator: runs the four-node subgraph (choose_analysis, execute_analysis,
 * validate_tool_result, progress_gate) per task with hard step, tool-call and latency bounds.
 * Tasks fan out in parallel with isolated state; only typed evidence envelopes merge back.
 */
@Component
public final class InvestigationAgent {

    private final WorkerToolRegistry tools;
    private final WorkflowProperties properties;
    private final ModelAssist assist;

    public InvestigationAgent(WorkerToolRegistry tools, LanguageModelPort model, WorkflowProperties properties) {
        this.tools = tools;
        this.properties = properties;
        this.assist = new ModelAssist(model, properties.toolTimeout(), 600);
    }

    public List<InvestigationResult> investigate(WorkflowRun run, TransitionListener listener) {
        List<InvestigationTask> tasks = run.plan().tasks();
        TenantContext tenant = run.state().tenant();
        AnalyticsGateway.WindowDto current = AnalyticsGateway.WindowDto.trailingWeek(run.state().asOfDate());
        AnalyticsGateway.WindowDto baseline = current.priorFourWeeks();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(tasks.size(), 4)));
        List<Future<InvestigationResult>> futures = new ArrayList<>();
        try {
            for (InvestigationTask task : tasks) {
                futures.add(pool.submit(() -> runTask(run, task, tenant, current, baseline, listener)));
            }
            List<InvestigationResult> results = new ArrayList<>();
            long deadline = System.currentTimeMillis() + properties.toolTimeout().toMillis() * Math.max(2, run.state().maxInvestigationSteps());
            for (int i = 0; i < futures.size(); i++) {
                InvestigationTask task = tasks.get(i);
                try {
                    long remaining = Math.max(50, deadline - System.currentTimeMillis());
                    results.add(futures.get(i).get(remaining, TimeUnit.MILLISECONDS));
                } catch (TimeoutException e) {
                    futures.get(i).cancel(true);
                    results.add(failed(task, "TIMEOUT", "Task exceeded the investigation deadline"));
                } catch (ExecutionException e) {
                    results.add(failed(task, "TOOL_FAILURE", String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(failed(task, "INTERRUPTED", "Investigation interrupted"));
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    InvestigationResult runTask(WorkflowRun run, InvestigationTask task, TenantContext tenant,
                                AnalyticsGateway.WindowDto current, AnalyticsGateway.WindowDto baseline, TransitionListener listener) {
        long started = System.currentTimeMillis();
        Optional<InvestigationTool> tool = tools.tool(task.worker());
        if (tool.isEmpty()) {
            return failed(task, "UNKNOWN_WORKER", "Worker is not registered: " + task.worker());
        }
        List<WorkerEvidenceDto> evidence = new ArrayList<>();
        List<String> findings = new ArrayList<>();
        List<String> inferences = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> filters = initialFilters(task);
        int steps = 0;
        int toolCalls = 0;
        InvestigationResult.Status status = InvestigationResult.Status.COMPLETE;
        String failure = null;
        while (steps < run.state().maxInvestigationSteps()) {
            steps++;
            // choose_analysis: deterministic default is the worker's primary analysis; a model may narrow filters once.
            Decision decision = chooseAnalysis(run, task, evidence, filters, steps);
            emit(run, listener, task, InvestigationNode.CHOOSE_ANALYSIS, decision.action());
            if (decision.action().equals("FINISH")) {
                break;
            }
            synchronized (run) {
                if (!run.tryConsumeToolCall()) {
                    warnings.add("Tool-call budget exhausted before " + task.worker() + " could run step " + steps);
                    status = evidence.isEmpty() ? InvestigationResult.Status.SKIPPED : InvestigationResult.Status.PARTIAL;
                    break;
                }
            }
            toolCalls++;
            // execute_analysis
            WorkerEvidenceDto result;
            try {
                result = tool.get().execute(tenant, current, baseline, decision.filters());
                emit(run, listener, task, InvestigationNode.EXECUTE_ANALYSIS, "ok");
            } catch (RuntimeException e) {
                emit(run, listener, task, InvestigationNode.EXECUTE_ANALYSIS, "error");
                status = evidence.isEmpty() ? InvestigationResult.Status.FAILED : InvestigationResult.Status.PARTIAL;
                failure = e.getMessage();
                break;
            }
            // validate_tool_result: schema, tenant, provenance, coverage
            List<String> validation = validate(result, tenant);
            emit(run, listener, task, InvestigationNode.VALIDATE_TOOL_RESULT, validation.isEmpty() ? "valid" : "rejected");
            if (!validation.isEmpty()) {
                warnings.addAll(validation);
                status = evidence.isEmpty() ? InvestigationResult.Status.FAILED : InvestigationResult.Status.PARTIAL;
                failure = String.join("; ", validation);
                break;
            }
            evidence.add(result);
            findings.addAll(result.directFindings());
            warnings.addAll(result.caveats());
            if (!result.supported()) {
                unresolved.add(task.question() + " (analysis unsupported for this tenant)");
            }
            // progress_gate: continue only when the deterministic rule says another step adds evidence.
            boolean more = progressGate(task, result, steps, run);
            emit(run, listener, task, InvestigationNode.PROGRESS_GATE, more ? "continue" : "complete");
            if (!more) {
                break;
            }
            filters = narrow(result, filters);
            if (filters.isEmpty()) {
                break;
            }
        }
        if (steps >= run.state().maxInvestigationSteps() && status == InvestigationResult.Status.COMPLETE && evidence.isEmpty()) {
            status = InvestigationResult.Status.SKIPPED;
        }
        synchronized (run) {
            run.recordInvestigationDepth(steps);
        }
        return new InvestigationResult(task.worker(), status, evidence, findings, inferences, unresolved, warnings, steps, toolCalls,
                System.currentTimeMillis() - started, failure);
    }

    private Decision chooseAnalysis(WorkflowRun run, InvestigationTask task, List<WorkerEvidenceDto> evidence, Map<String, String> filters, int step) {
        if (step == 1) {
            return new Decision("CALL_TOOL", filters);
        }
        Optional<JsonNode> choice = assist.ask("investigator", Map.of(
                "task", Map.of("worker", task.worker(), "question", task.question(), "filters", filters),
                "evidenceSoFar", summarise(evidence),
                "budget", Map.of("remainingSteps", run.state().maxInvestigationSteps() - step + 1,
                        "remainingToolCalls", run.state().maxToolCalls() - run.state().toolCalls())), run::addModelUsage);
        if (choice.isEmpty()) {
            // deterministic drill-down: the progress gate already narrowed the filters, so run one more bounded call
            return filters.isEmpty() ? new Decision("FINISH", Map.of()) : new Decision("CALL_TOOL", filters);
        }
        if (!"CALL_TOOL".equals(choice.get().path("action").asText(""))) {
            return new Decision("FINISH", Map.of());
        }
        Map<String, String> proposed = new LinkedHashMap<>();
        for (var entry : choice.get().path("filters").properties()) {
            AllowedDimensions.normalise(entry.getKey()).ifPresent(key -> proposed.put(key, entry.getValue().asText()));
        }
        return proposed.isEmpty() ? new Decision("FINISH", Map.of()) : new Decision("CALL_TOOL", proposed);
    }

    static Map<String, String> initialFilters(InvestigationTask task) {
        Map<String, String> filters = new LinkedHashMap<>();
        task.parameters().forEach((key, value) -> {
            if (key.startsWith("filter.")) {
                AllowedDimensions.normalise(key.substring("filter.".length())).ifPresent(k -> filters.put(k, value));
            }
        });
        return filters;
    }

    static List<String> validate(WorkerEvidenceDto result, TenantContext tenant) {
        List<String> problems = new ArrayList<>();
        if (!tenant.businessUnit().equals(result.businessUnit())) {
            problems.add("Evidence business unit mismatch");
        }
        for (MetricResult metric : result.metrics()) {
            if (metric.status() == MetricStatus.SUPPORTED && metric.value() == null) {
                problems.add("Supported metric without value: " + metric.metricId());
            }
            if (!"metrics-v1.1".equals(metric.contractVersion())) {
                problems.add("Unexpected contract version " + metric.contractVersion());
            }
            if (metric.dataVersion() == null || metric.dataVersion().isBlank()) {
                problems.add("Missing data version on " + metric.metricId());
            }
        }
        for (WorkerEvidenceDto.Ranking ranking : result.rankings()) {
            if (ranking.evidenceId() == null || ranking.dataVersion() == null) {
                problems.add("Ranking without provenance for " + ranking.metricId());
            }
            if (ranking.rows().size() > 500) {
                problems.add("Ranking exceeds the row limit");
            }
        }
        return problems;
    }

    /** Deterministic second step: drill into the top qualified site once, for the site/shift worker only. */
    static boolean progressGate(InvestigationTask task, WorkerEvidenceDto result, int step, WorkflowRun run) {
        if (step >= run.state().maxInvestigationSteps()) {
            return false;
        }
        if (!task.worker().equals("site_shift_direction") || step > 1) {
            return false;
        }
        return result.rankings().stream().anyMatch(r -> r.dimension().equals("site_id") && !r.qualifiedRows().isEmpty());
    }

    static Map<String, String> narrow(WorkerEvidenceDto result, Map<String, String> filters) {
        Map<String, String> next = new LinkedHashMap<>(filters);
        result.rankings().stream().filter(r -> r.dimension().equals("site_id")).findFirst()
                .flatMap(r -> r.qualifiedRows().stream().findFirst())
                .ifPresent(top -> next.put("site_id", top.member()));
        return next.equals(filters) ? Map.of() : next;
    }

    private static List<Map<String, Object>> summarise(List<WorkerEvidenceDto> evidence) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (WorkerEvidenceDto dto : evidence) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("worker", dto.worker());
            entry.put("findings", dto.directFindings());
            entry.put("caveats", dto.caveats());
            entry.put("evidenceIds", dto.rankings().stream().map(WorkerEvidenceDto.Ranking::evidenceId).toList());
            summary.add(entry);
        }
        return summary;
    }

    private static void emit(WorkflowRun run, TransitionListener listener, InvestigationTask task, InvestigationNode node, String outcome) {
        TransitionEvent event = new TransitionEvent(run.state().runId(), run.context().traceId(), WorkflowNode.RUN_INVESTIGATIONS,
                "investigation." + task.worker() + "." + node.name().toLowerCase(java.util.Locale.ROOT), run.state().step(), run.state().step(),
                Instant.now(), 0, outcome, Map.of("worker", task.worker()));
        synchronized (run) {
            run.addTransition(event);
        }
        listener.onTransition(event);
    }

    private static InvestigationResult failed(InvestigationTask task, String code, String message) {
        return new InvestigationResult(task.worker(), InvestigationResult.Status.FAILED, List.of(), List.of(), List.of(),
                List.of(task.question()), List.of(code + ": " + message), 0, 0, 0, message);
    }

    record Decision(String action, Map<String, String> filters) {
    }
}
