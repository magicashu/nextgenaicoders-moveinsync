package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.InvestigationTool;
import com.moveinsync.mobilitycopilot.workflow.investigation.budget.BudgetTracker;
import com.moveinsync.mobilitycopilot.workflow.investigation.executor.TaskExecutor;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.WorkerRegistry;
import com.moveinsync.mobilitycopilot.workflow.investigation.validation.EvidenceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Agent 2 — Investigator.
 *
 * Runs the four-stage loop (choose → execute → validate → progress) for each
 * task in the Supervisor's plan, within the run's WorkflowBudget.
 *
 * Concurrency and timeouts are fully delegated to TaskExecutor.
 * Budget exhaustion or deadline expiry moves remaining tasks to pendingTasks.
 */
@Service
public final class InvestigationAgentImpl implements InvestigationAgent {

    private static final Logger log = LoggerFactory.getLogger(InvestigationAgentImpl.class);

    private final WorkerRegistry workerRegistry;
    private final EvidenceValidator evidenceValidator;

    public InvestigationAgentImpl(WorkerRegistry workerRegistry, EvidenceValidator evidenceValidator) {
        this.workerRegistry = workerRegistry;
        this.evidenceValidator = evidenceValidator;
    }

    @Override
    public InvestigationResult investigate(RunContext context, InvestigationPlan plan) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        BudgetTracker budget = new BudgetTracker(context.budget(), context.deadline());

        List<MetricEvidence> allEvidence = new ArrayList<>();
        List<InvestigationTask> completedTasks = new ArrayList<>();
        List<InvestigationTask> pendingTasks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<List<InvestigationTask>> batches = partitionByDependency(plan.tasks());

        try (TaskExecutor executor = new TaskExecutor(context.budget())) {
            for (List<InvestigationTask> batch : batches) {

                // Defer tasks that cannot start due to budget
                List<InvestigationTask> runnable = new ArrayList<>();
                for (InvestigationTask task : batch) {
                    if (budget.toolCallsExhausted() || budget.deadlineExceeded()) {
                        pendingTasks.add(task);
                        log.info("budget exhausted, deferring task={}", task.taskId());
                    } else {
                        runnable.add(task);
                    }
                }

                if (runnable.isEmpty()) continue;

                List<TaskExecutor.TaskOutcome> outcomes =
                        executor.executeBatch(runnable, task -> runTaskLoop(context, task, budget));

                for (TaskExecutor.TaskOutcome outcome : outcomes) {
                    allEvidence.addAll(outcome.evidence());
                    warnings.addAll(outcome.warnings());
                    if (outcome.isCompleted()) {
                        completedTasks.add(outcome.task());
                    } else {
                        pendingTasks.add(outcome.task());
                    }
                }
            }
        }

        return new InvestigationResult(
                List.copyOf(allEvidence),
                List.copyOf(completedTasks),
                List.copyOf(pendingTasks),
                List.copyOf(warnings)
        );
    }

    // -------------------------------------------------------------------------
    // Four-stage loop
    // -------------------------------------------------------------------------

    /**
     * Stage 1 — CHOOSE   : resolve worker from registry
     * Stage 2 — EXECUTE  : call tool with budget-guarded timeout
     * Stage 3 — VALIDATE : check scope, version, tenant
     * Stage 4 — PROGRESS : decide whether a bounded follow-up is justified
     */
    private TaskExecutor.TaskOutcome runTaskLoop(RunContext context, InvestigationTask task,
                                                  BudgetTracker budget) {
        List<MetricEvidence> taskEvidence = new ArrayList<>();
        List<String> taskWarnings = new ArrayList<>();
        int depth = 0;
        InvestigationTask currentTask = task;

        while (true) {
            // Stage 1: CHOOSE
            Optional<InvestigationTool<MetricEvidence>> toolOpt =
                    workerRegistry.resolve(currentTask.worker());
            if (toolOpt.isEmpty()) {
                taskWarnings.add("no worker registered for type=" + currentTask.worker()
                        + " taskId=" + currentTask.taskId());
                return TaskExecutor.TaskOutcome.failed(task, "unregistered worker " + currentTask.worker());
            }
            InvestigationTool<MetricEvidence> tool = toolOpt.get();

            // Stage 2: EXECUTE
            if (!budget.tryConsumeToolCall()) {
                taskWarnings.add("tool call budget exhausted before executing task=" + currentTask.taskId());
                return partialOrFailed(task, taskEvidence, taskWarnings);
            }

            MetricEvidence raw;
            try {
                raw = executeWithTimeout(tool, context, currentTask,
                        context.budget().investigationTimeout().toSeconds());
            } catch (TimeoutException e) {
                taskWarnings.add("tool timeout worker=" + currentTask.worker()
                        + " taskId=" + currentTask.taskId());
                return partialOrFailed(task, taskEvidence, taskWarnings);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                taskWarnings.add("tool interrupted taskId=" + currentTask.taskId());
                return partialOrFailed(task, taskEvidence, taskWarnings);
            } catch (Exception e) {
                taskWarnings.add("tool error worker=" + currentTask.worker()
                        + " taskId=" + currentTask.taskId() + ": " + e.getMessage());
                return partialOrFailed(task, taskEvidence, taskWarnings);
            }

            // Stage 3: VALIDATE
            List<String> validationErrors = evidenceValidator.validate(raw, context);
            if (!validationErrors.isEmpty()) {
                taskWarnings.add("evidence rejected taskId=" + currentTask.taskId()
                        + " reasons=" + validationErrors);
                return partialOrFailed(task, taskEvidence, taskWarnings);
            }
            taskEvidence.add(raw);
            taskWarnings.addAll(raw.warnings());

            // Stage 4: PROGRESS
            if (isSufficient(taskEvidence) || !budget.canFollowUp(depth)) {
                break;
            }

            Optional<InvestigationTask> followUp = buildFollowUp(currentTask, raw, depth);
            if (followUp.isEmpty()) break;

            currentTask = followUp.get();
            depth++;
        }

        return new TaskExecutor.TaskOutcome(
                task, List.copyOf(taskEvidence), List.copyOf(taskWarnings),
                TaskExecutor.TaskOutcome.Status.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MetricEvidence executeWithTimeout(InvestigationTool<MetricEvidence> tool,
                                               RunContext context, InvestigationTask task,
                                               long timeoutSeconds)
            throws TimeoutException, InterruptedException, ExecutionException {
        ExecutorService single = Executors.newSingleThreadExecutor();
        Future<MetricEvidence> future = single.submit(() -> tool.execute(context, task));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            single.shutdownNow();
        }
    }

    private boolean isSufficient(List<MetricEvidence> evidence) {
        return evidence.stream().anyMatch(e -> MetricStatus.AVAILABLE.equals(e.status()));
    }

    private Optional<InvestigationTask> buildFollowUp(InvestigationTask original,
                                                       MetricEvidence evidence, int depth) {
        if (!MetricStatus.PARTIAL.equals(evidence.status())) return Optional.empty();
        if (original.requests().isEmpty()) return Optional.empty();

        return Optional.of(new InvestigationTask(
                original.taskId() + "-followup-" + (depth + 1),
                original.worker(),
                original.question() + " [follow-up depth=" + (depth + 1) + "]",
                original.requests(),
                original.dependencies()
        ));
    }

    private TaskExecutor.TaskOutcome partialOrFailed(InvestigationTask task,
                                                      List<MetricEvidence> evidence,
                                                      List<String> warnings) {
        TaskExecutor.TaskOutcome.Status status = evidence.isEmpty()
                ? TaskExecutor.TaskOutcome.Status.FAILED
                : TaskExecutor.TaskOutcome.Status.PARTIAL;
        return new TaskExecutor.TaskOutcome(task, List.copyOf(evidence), List.copyOf(warnings), status);
    }

    /**
     * Topological sort into dependency-ordered batches.
     * Tasks with no unmet dependencies go into the first batch; their dependents follow.
     * Circular/unresolvable dependencies are broken by putting all remaining into one batch.
     */
    private List<List<InvestigationTask>> partitionByDependency(List<InvestigationTask> tasks) {
        Map<String, InvestigationTask> byId = new LinkedHashMap<>();
        for (InvestigationTask t : tasks) byId.put(t.taskId(), t);

        Set<String> done = new HashSet<>();
        Set<String> remaining = new LinkedHashSet<>(byId.keySet());
        List<List<InvestigationTask>> batches = new ArrayList<>();
        int safetyLimit = tasks.size() + 1;

        while (!remaining.isEmpty() && safetyLimit-- > 0) {
            List<InvestigationTask> batch = new ArrayList<>();
            for (String id : remaining) {
                InvestigationTask t = byId.get(id);
                if (t.dependencies() == null || done.containsAll(t.dependencies())) {
                    batch.add(t);
                }
            }
            if (batch.isEmpty()) {
                remaining.stream().map(byId::get).forEach(batch::add);
                batches.add(batch);
                break;
            }
            batch.forEach(t -> remaining.remove(t.taskId()));
            batch.forEach(t -> done.add(t.taskId()));
            batches.add(batch);
        }
        return batches;
    }
}
