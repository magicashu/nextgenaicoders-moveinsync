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
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.state.AgentState;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;

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
        this.assist = new ModelAssist(model, properties.modelTimeout(), 600);
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
            long deadline = System.currentTimeMillis() + (properties.toolTimeout().toMillis() + 2 * properties.modelTimeout().toMillis())
                    * Math.max(2, run.state().maxInvestigationSteps());
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
        // Invocation-local evidence never leaks across parallel workers. LangGraph4j controls the loop.
        class Branch {
            final List<WorkerEvidenceDto> evidence = new ArrayList<>();
            final List<String> findings = new ArrayList<>(), unresolved = new ArrayList<>(), warnings = new ArrayList<>();
            Map<String, String> filters = initialFilters(task);
            Decision decision;
            WorkerEvidenceDto result;
            int steps, toolCalls;
            InvestigationResult.Status status = InvestigationResult.Status.COMPLETE;
            String failure;

            Map<String, Object> choose() {
                Instant start = Instant.now();
                if (Thread.currentThread().isInterrupted() || steps >= run.state().maxInvestigationSteps()) return Map.of("next", END);
                decision = chooseAnalysis(run, task, evidence, filters, ++steps);
                emit(run, listener, task, InvestigationNode.CHOOSE_ANALYSIS, decision.action(), start,
                        Map.of("filters", decision.filters().toString(), "step", String.valueOf(steps)));
                return Map.of("next", decision.action().equals("FINISH") ? END : "execute_analysis");
            }

            Map<String, Object> execute() {
                Instant start = Instant.now();
                synchronized (run) {
                    if (Thread.currentThread().isInterrupted() || !run.tryConsumeToolCall()) {
                        warnings.add("Tool-call budget exhausted or branch cancelled");
                        status = evidence.isEmpty() ? InvestigationResult.Status.SKIPPED : InvestigationResult.Status.PARTIAL;
                        return Map.of("next", END);
                    }
                }
                toolCalls++;
                try {
                    result = tool.get().execute(tenant, current, baseline, decision.filters());
                    emit(run, listener, task, InvestigationNode.EXECUTE_ANALYSIS, "ok", start,
                            Map.of("filters", decision.filters().toString(), "evidenceIds", result.rankings().stream().map(WorkerEvidenceDto.Ranking::evidenceId).toList().toString()));
                    return Map.of("next", "validate_tool_result");
                } catch (RuntimeException e) {
                    failure = e.getMessage();
                    status = evidence.isEmpty() ? InvestigationResult.Status.FAILED : InvestigationResult.Status.PARTIAL;
                    emit(run, listener, task, InvestigationNode.EXECUTE_ANALYSIS, "error", start, Map.of("error.type", e.getClass().getSimpleName()));
                    return Map.of("next", END);
                }
            }

            Map<String, Object> verify() {
                Instant start = Instant.now();
                List<String> validation = validate(result, tenant);
                emit(run, listener, task, InvestigationNode.VALIDATE_TOOL_RESULT, validation.isEmpty() ? "valid" : "rejected", start, Map.of("violations", validation.toString()));
                if (!validation.isEmpty()) {
                    warnings.addAll(validation);
                    status = evidence.isEmpty() ? InvestigationResult.Status.FAILED : InvestigationResult.Status.PARTIAL;
                    failure = String.join("; ", validation);
                    return Map.of("next", END);
                }
                evidence.add(result);
                findings.addAll(result.directFindings());
                warnings.addAll(result.caveats());
                if (!result.supported()) unresolved.add(task.question() + " (analysis unsupported for this tenant)");
                return Map.of("next", "progress_gate");
            }

            Map<String, Object> progress() {
                Instant start = Instant.now();
                boolean more = progressGate(task, result, steps, run);
                if (more) filters = narrow(result, filters);
                more = more && !filters.isEmpty() && steps < run.state().maxInvestigationSteps();
                emit(run, listener, task, InvestigationNode.PROGRESS_GATE, more ? "continue" : "complete", start,
                        Map.of("step", String.valueOf(steps), "remainingToolCalls", String.valueOf(run.state().maxToolCalls() - run.state().toolCalls())));
                return Map.of("next", more ? "choose_analysis" : END);
            }
        }
        Branch branch = new Branch();
        try {
            var graph = new StateGraph<AgentState>(AgentState::new)
                    .addNode("choose_analysis", node_async(state -> branch.choose()))
                    .addNode("execute_analysis", node_async(state -> branch.execute()))
                    .addNode("validate_tool_result", node_async(state -> branch.verify()))
                    .addNode("progress_gate", node_async(state -> branch.progress()))
                    .addEdge(START, "choose_analysis");
            Map<String, String> targets = Map.of("choose_analysis", "choose_analysis", "execute_analysis", "execute_analysis",
                    "validate_tool_result", "validate_tool_result", "progress_gate", "progress_gate", END, END);
            for (String name : List.of("choose_analysis", "execute_analysis", "validate_tool_result", "progress_gate")) {
                graph.addConditionalEdges(name, edge_async(state -> state.<String>value("next").orElse(END)), targets);
            }
            graph.compile(CompileConfig.builder().recursionLimit(4 * run.state().maxInvestigationSteps() + 4).build()).invoke(Map.of());
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("Cannot compile investigator graph", e);
        }
        synchronized (run) {
            run.recordInvestigationDepth(branch.steps);
        }
        return new InvestigationResult(task.worker(), branch.status, branch.evidence, branch.findings, List.of(), branch.unresolved,
                branch.warnings, branch.steps, branch.toolCalls, System.currentTimeMillis() - started, branch.failure);
    }

    private Decision chooseAnalysis(WorkflowRun run, InvestigationTask task, List<WorkerEvidenceDto> evidence, Map<String, String> filters, int step) {
        Optional<JsonNode> choice = assist.ask("investigator", Map.of(
                "task", Map.of("worker", task.worker(), "question", task.question(), "filters", filters),
                "evidenceSoFar", summarise(evidence),
                "budget", Map.of("remainingSteps", run.state().maxInvestigationSteps() - step + 1,
                        "remainingToolCalls", run.state().maxToolCalls() - run.state().toolCalls())), run);
        if (choice.isEmpty()) {
            // deterministic drill-down: the progress gate already narrowed the filters, so run one more bounded call
            return step == 1 || !filters.isEmpty() ? new Decision("CALL_TOOL", filters) : new Decision("FINISH", Map.of());
        }
        if (!"CALL_TOOL".equals(choice.get().path("action").asText(""))) {
            return new Decision("FINISH", Map.of());
        }
        Map<String, String> proposed = new LinkedHashMap<>();
        for (var entry : choice.get().path("filters").properties()) {
            AllowedDimensions.normalise(entry.getKey()).ifPresent(key -> proposed.put(key, entry.getValue().asText()));
        }
        return new Decision("CALL_TOOL", proposed.isEmpty() ? filters : proposed);
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

    private static void emit(WorkflowRun run, TransitionListener listener, InvestigationTask task, InvestigationNode node, String outcome,
                             Instant started, Map<String, String> details) {
        Map<String, String> attributes = new LinkedHashMap<>(details);
        attributes.put("worker", task.worker());
        attributes.put("businessUnit", run.state().tenant().businessUnit());
        TransitionEvent event = new TransitionEvent(run.state().runId(), run.context().traceId(), WorkflowNode.RUN_INVESTIGATIONS,
                "investigation." + task.worker() + "." + node.name().toLowerCase(java.util.Locale.ROOT), run.state().step(), run.state().step(),
                started, java.time.Duration.between(started, Instant.now()).toMillis(), outcome,
                com.moveinsync.mobilitycopilot.observability.Redaction.attributes(attributes));
        synchronized (run) {
            run.addTransition(event);
        }
        try { listener.onTransition(event); }
        catch (RuntimeException ignored) { /* Observability must not fail the analysis. */ }
    }

    private static InvestigationResult failed(InvestigationTask task, String code, String message) {
        return new InvestigationResult(task.worker(), InvestigationResult.Status.FAILED, List.of(), List.of(), List.of(),
                List.of(task.question()), List.of(code + ": " + message), 0, 0, 0, message);
    }

    record Decision(String action, Map<String, String> filters) {
    }
}
