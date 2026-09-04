package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkflowState(
        UUID runId,
        TenantContext tenant,
        LocalDate asOfDate,
        WorkflowStep step,
        List<InvestigationTask> tasks,
        int investigationSteps,
        int maxInvestigationSteps) {

    public static WorkflowState start(TenantContext tenant, LocalDate asOfDate) {
        return new WorkflowState(
                UUID.randomUUID(), tenant, asOfDate, WorkflowStep.INITIALIZED, List.of(), 0, 4);
    }
}
