package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationNode;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort.AgentRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Structural metadata only. This endpoint never claims that declared contracts are implemented. */
@RestController
public final class ScaffoldStatusController {
    @GetMapping("/api/v1/capabilities")
    public ScaffoldStatus capabilities() {
        return new ScaffoldStatus("SCAFFOLD", false, AgentRole.values().length,
                WorkflowNode.values().length, InvestigationNode.values().length,
                WorkerType.values().length, MetricId.values().length, List.of(),
                "Interfaces and DTOs only; assigned team members implement and verify the runtime.");
    }

    public record ScaffoldStatus(String mode, boolean governedRuntimeReady, int agentRoles,
                                 int workflowNodes, int investigationStages, int workers,
                                 int declaredMetrics, List<String> implementedGovernedCapabilities,
                                 String message) {}
}
