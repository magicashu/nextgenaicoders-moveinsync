package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import java.util.List;

/** Deterministic interpretation of a user question before governed planning. */
public record SupervisorQueryRoute(Status status, MetricId metric, String question,
                                   List<WorkerType> workers, String message) {
    public enum Status { SUPPORTED, CLARIFICATION_REQUIRED, UNSUPPORTED }

    public SupervisorQueryRoute {
        workers = workers == null ? List.of() : List.copyOf(workers);
    }
}