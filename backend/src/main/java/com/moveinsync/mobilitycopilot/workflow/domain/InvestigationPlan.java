package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Typed plan from the Supervisor: registered tasks only, bounded, with explicit stop conditions. */
public record InvestigationPlan(
        String anomalyId,
        List<InvestigationTask> tasks,
        Set<MetricId> requiredMetrics,
        Set<String> allowedDimensions,
        List<String> stopConditions,
        String rationale,
        boolean modelGenerated,
        List<String> validationNotes) {

    public InvestigationPlan {
        Objects.requireNonNull(anomalyId);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        requiredMetrics = requiredMetrics == null ? Set.of() : Set.copyOf(requiredMetrics);
        allowedDimensions = allowedDimensions == null ? Set.of() : Set.copyOf(allowedDimensions);
        stopConditions = stopConditions == null ? List.of() : List.copyOf(stopConditions);
        validationNotes = validationNotes == null ? List.of() : List.copyOf(validationNotes);
        rationale = rationale == null ? "" : rationale;
    }

    public List<String> workers() {
        return tasks.stream().map(InvestigationTask::worker).toList();
    }
}
