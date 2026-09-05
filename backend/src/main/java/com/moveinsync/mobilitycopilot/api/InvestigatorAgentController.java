package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@ConditionalOnProperty(prefix = "mobility.agent", name = "role", havingValue = "investigator")
@RequestMapping("/api/v1/agent")
public class InvestigatorAgentController {
    private final WorkflowCoordinator coordinator;
    public InvestigatorAgentController(WorkflowCoordinator coordinator) { this.coordinator = coordinator; }
    @PostMapping("/investigator")
    public InvestigationResult run(@Valid @RequestBody AgentApiModels.PlanRequest request) {
        return coordinator.investigator(request.businessUnit(), request.asOf(), request.plan());
    }
}