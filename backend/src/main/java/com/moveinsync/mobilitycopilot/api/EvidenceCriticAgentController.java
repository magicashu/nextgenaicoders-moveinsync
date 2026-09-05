package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@ConditionalOnProperty(prefix = "mobility.agent", name = "role", havingValue = "evidence-critic")
@RequestMapping("/api/v1/agent")
public class EvidenceCriticAgentController {
    private final WorkflowCoordinator coordinator;
    public EvidenceCriticAgentController(WorkflowCoordinator coordinator) { this.coordinator = coordinator; }
    @PostMapping("/evidence-critic")
    public VerificationResult run(@Valid @RequestBody AgentApiModels.InvestigationRequest request) {
        return coordinator.critic(request.businessUnit(), request.asOf(), request.investigation());
    }
}