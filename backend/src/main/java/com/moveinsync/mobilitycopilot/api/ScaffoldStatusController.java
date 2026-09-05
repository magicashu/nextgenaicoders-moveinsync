package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationNode;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort.AgentRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Implementation metadata; tenant/window eligibility is returned with each metric result. */
@RestController
public final class ScaffoldStatusController {
    @GetMapping("/api/v1/capabilities")
    public ScaffoldStatus capabilities() {
        return new ScaffoldStatus("PARTIAL", false, AgentRole.values().length,
                WorkflowNode.values().length, InvestigationNode.values().length,
                WorkerType.values().length, MetricId.values().length,
                java.util.Arrays.stream(MetricId.values()).map(Enum::name).toList(),
                "Four report agents and M01-M18 are implemented. Dataset eligibility remains query-specific. Durable action execution, authenticated serving and production load gates remain incomplete.");
    }

    public record ScaffoldStatus(String mode, boolean governedRuntimeReady, int agentRoles,
                                 int workflowNodes, int investigationStages, int workers,
                                 int declaredMetrics, List<String> implementedGovernedCapabilities,
                                 String message) {}
}
