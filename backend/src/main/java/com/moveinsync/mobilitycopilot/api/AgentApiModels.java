package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationPlan;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

final class AgentApiModels {
    private AgentApiModels() {}
    record PromptRequest(@NotBlank String businessUnit, @NotBlank String prompt, @NotNull LocalDate asOf) {}
    record PlanRequest(@NotBlank String businessUnit, @NotNull LocalDate asOf, @NotNull InvestigationPlan plan) {}
    record InvestigationRequest(@NotBlank String businessUnit, @NotNull LocalDate asOf,
                                @NotNull InvestigationResult investigation) {}
    record VerificationRequest(@NotBlank String businessUnit, @NotNull LocalDate asOf,
                               @NotNull VerificationResult verification) {}
}