package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationPlan;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@ConditionalOnProperty(prefix = "mobility.agent", name = "role", havingValue = "supervisor")
@RequestMapping("/api/v1/agent")
public class SupervisorAgentController {
    private final WorkflowCoordinator coordinator;
    public SupervisorAgentController(WorkflowCoordinator coordinator) { this.coordinator = coordinator; }
    @PostMapping("/supervisor")
    public InvestigationPlan run(@Valid @RequestBody AgentApiModels.PromptRequest request) {
        return coordinator.supervisor(request.businessUnit(), request.asOf(), request.prompt());
    }
}