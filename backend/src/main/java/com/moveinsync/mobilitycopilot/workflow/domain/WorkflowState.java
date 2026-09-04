package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WorkflowState(
        UUID runId,
        TenantContext tenant,
        LocalDate asOfDate,
        WorkflowStep step,
        List<InvestigationTask> tasks,
        int investigationSteps,
        int maxInvestigationSteps,
        int correctionCycles,
        int maxCorrectionCycles,
        int toolCalls,
        int maxToolCalls) {

    public WorkflowState {
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(tenant, "tenant is required");
        Objects.requireNonNull(asOfDate, "asOfDate is required");
        Objects.requireNonNull(step, "step is required");
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        if (maxInvestigationSteps < 1 || maxCorrectionCycles < 0 || maxToolCalls < 1) {
            throw new IllegalArgumentException("workflow limits are invalid");
        }
        if (investigationSteps < 0 || correctionCycles < 0 || toolCalls < 0) {
            throw new IllegalArgumentException("workflow counters must be non-negative");
        }
        if (investigationSteps > maxInvestigationSteps
                || correctionCycles > maxCorrectionCycles
                || toolCalls > maxToolCalls) {
            throw new IllegalArgumentException("workflow counter exceeds configured limit");
        }
    }

    public static WorkflowState start(TenantContext tenant, LocalDate asOfDate) {
        return start(tenant, asOfDate, 4, 1, 12);
    }

    public static WorkflowState start(
            TenantContext tenant,
            LocalDate asOfDate,
            int maxInvestigationSteps,
            int maxCorrectionCycles,
            int maxToolCalls) {
        return new WorkflowState(
                UUID.randomUUID(),
                tenant,
                asOfDate,
                WorkflowStep.INITIALIZED,
                List.of(),
                0,
                maxInvestigationSteps,
                0,
                maxCorrectionCycles,
                0,
                maxToolCalls);
    }
}
