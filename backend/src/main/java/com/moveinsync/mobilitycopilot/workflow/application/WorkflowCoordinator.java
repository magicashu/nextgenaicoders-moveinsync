package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowCheckpoint;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/** WS4: add authorized API-to-job/workflow coordination using shared contracts. */
@Service
public final class WorkflowCoordinator {
    private final WorkflowEngine workflowEngine;

    public WorkflowCoordinator(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    public WorkflowCheckpoint start(RunContext context) {
        return workflowEngine.start(context);
    }

    /** Legacy sample endpoint is disabled; implement the governed product API instead. */
    public DecisionBrief createDemoBrief(String businessUnit, LocalDate asOfDate) {
        throw new UnsupportedOperationException("Scaffold only: the team must implement the governed API");
    }
}
