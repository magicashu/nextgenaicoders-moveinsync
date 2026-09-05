package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public final class WorkflowCoordinator {

    private final WorkflowEngine workflowEngine;

    public WorkflowCoordinator(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    public DecisionBrief createDemoBrief(String businessUnit, LocalDate asOfDate) {
        TenantContext tenant = new TenantContext(businessUnit);
        return workflowEngine.run(WorkflowState.start(tenant, asOfDate)).brief();
    }
}
