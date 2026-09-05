package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@ConditionalOnProperty(prefix = "mobility.agent", name = "role", havingValue = "briefing-action")
@RequestMapping("/api/v1/agent")
public class BriefingActionAgentController {
    private final WorkflowCoordinator coordinator;
    public BriefingActionAgentController(WorkflowCoordinator coordinator) { this.coordinator = coordinator; }
    @PostMapping("/briefing-action")
    public DecisionBrief run(@Valid @RequestBody AgentApiModels.VerificationRequest request) {
        return coordinator.briefing(request.businessUnit(), request.asOf(), request.verification());
    }
}