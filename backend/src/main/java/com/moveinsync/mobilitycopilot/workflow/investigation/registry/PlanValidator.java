package com.moveinsync.mobilitycopilot.workflow.investigation.registry;

import com.moveinsync.mobilitycopilot.workflow.domain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Node 9 — deterministic plan validation.
 *
 * Removes tasks whose WorkerType is not registered, whose dependencies reference
 * unknown tasks, or that would immediately exceed the run budget.
 * Returns a validated plan with only the allowed tasks, plus rejection reasons.
 */
@Component
public final class PlanValidator {

    private final WorkerRegistry workerRegistry;

    public PlanValidator(WorkerRegistry workerRegistry) {
        this.workerRegistry = workerRegistry;
    }

    public ValidationResult validate(InvestigationPlan plan, RunContext context) {
        List<InvestigationTask> allowed = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        if (plan.tasks() == null || plan.tasks().isEmpty()) {
            return new ValidationResult(plan, List.of(), List.of("plan contains no tasks"));
        }

        var allTaskIds = plan.tasks().stream().map(InvestigationTask::taskId).toList();
        int toolCallsNeeded = 0;

        for (InvestigationTask task : plan.tasks()) {

            // Worker must be registered
            if (!workerRegistry.isRegistered(task.worker())) {
                rejections.add("task=" + task.taskId() + " rejected: unregistered worker " + task.worker());
                continue;
            }

            // Dependencies must refer to tasks in the same plan
            if (task.dependencies() != null) {
                List<String> unknownDeps = task.dependencies().stream()
                        .filter(dep -> !allTaskIds.contains(dep))
                        .toList();
                if (!unknownDeps.isEmpty()) {
                    rejections.add("task=" + task.taskId() + " rejected: unknown dependencies " + unknownDeps);
                    continue;
                }
            }

            // Budget headroom: at minimum one tool call per task
            toolCallsNeeded++;
            if (toolCallsNeeded > context.budget().maxToolCalls()) {
                rejections.add("task=" + task.taskId() + " rejected: would exceed maxToolCalls="
                        + context.budget().maxToolCalls());
                continue;
            }

            allowed.add(task);
        }

        InvestigationPlan validatedPlan = new InvestigationPlan(
                plan.planId(), plan.issueId(), List.copyOf(allowed),
                plan.requiredEvidence(), plan.stopConditions());

        return new ValidationResult(validatedPlan, List.copyOf(allowed), List.copyOf(rejections));
    }

    public record ValidationResult(InvestigationPlan plan, List<InvestigationTask> allowedTasks,
                                    List<String> rejections) {
        public boolean hasAllowedTasks() { return !allowedTasks.isEmpty(); }
    }
}
