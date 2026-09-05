package com.moveinsync.mobilitycopilot.config;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.action.application.ActionExecutor;
import com.moveinsync.mobilitycopilot.action.application.ActionRevalidator;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalStore;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("mobilityCapabilitiesHealthIndicator")
public final class MobilityCapabilitiesHealthIndicator implements HealthIndicator {

    private final ObjectProvider<AccessAuthorizer> accessAuthorizer;
    private final ObjectProvider<MetricService> metricService;
    private final ObjectProvider<WorkflowEngine> workflowEngine;
    private final ObjectProvider<WorkflowCheckpointStore> checkpointStore;
    private final ObjectProvider<ApprovalStore> approvalStore;
    private final ObjectProvider<ActionRevalidator> actionRevalidator;
    private final ObjectProvider<ActionExecutor> actionExecutor;
    private final ObjectProvider<AuditSink> auditSink;

    public MobilityCapabilitiesHealthIndicator(
            ObjectProvider<AccessAuthorizer> accessAuthorizer,
            ObjectProvider<MetricService> metricService,
            ObjectProvider<WorkflowEngine> workflowEngine,
            ObjectProvider<WorkflowCheckpointStore> checkpointStore,
            ObjectProvider<ApprovalStore> approvalStore,
            ObjectProvider<ActionRevalidator> actionRevalidator,
            ObjectProvider<ActionExecutor> actionExecutor,
            ObjectProvider<AuditSink> auditSink) {
        this.accessAuthorizer = accessAuthorizer;
        this.metricService = metricService;
        this.workflowEngine = workflowEngine;
        this.checkpointStore = checkpointStore;
        this.approvalStore = approvalStore;
        this.actionRevalidator = actionRevalidator;
        this.actionExecutor = actionExecutor;
        this.auditSink = auditSink;
    }

    @Override
    public Health health() {
        Object authorization = accessAuthorizer.getIfAvailable();
        Object metrics = metricService.getIfAvailable();
        Object engine = workflowEngine.getIfAvailable();
        Object checkpoints = checkpointStore.getIfAvailable();
        Object approvals = approvalStore.getIfAvailable();
        Object revalidator = actionRevalidator.getIfAvailable();
        Object executor = actionExecutor.getIfAvailable();
        Object audit = auditSink.getIfAvailable();

        boolean investigationReady = authorization != null && metrics != null && engine != null;
        boolean governedActionsReady = checkpoints != null
                && approvals != null
                && revalidator != null
                && executor != null
                && audit != null;

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("authorization", capability(authorization));
        capabilities.put("governedMetrics", capability(metrics));
        capabilities.put("workflowEngine", capability(engine));
        capabilities.put("checkpointPersistence", capability(checkpoints));
        capabilities.put("approvalStore", capability(approvals));
        capabilities.put("actionRevalidation", capability(revalidator));
        capabilities.put("idempotentExecutor", capability(executor));
        capabilities.put("businessAudit", capability(audit));

        return Health.up()
                .withDetail("releaseReady", investigationReady && governedActionsReady)
                .withDetail("investigationReady", investigationReady)
                .withDetail("governedActionsReady", governedActionsReady)
                .withDetail("operatingMode", operatingMode(investigationReady, governedActionsReady))
                .withDetail("capabilities", capabilities)
                .build();
    }

    private Map<String, String> capability(Object implementation) {
        if (implementation == null) {
            return Map.of("status", "UNAVAILABLE");
        }
        return Map.of(
                "status", "AVAILABLE",
                "implementation", implementation.getClass().getName());
    }

    private String operatingMode(boolean investigationReady, boolean governedActionsReady) {
        if (investigationReady && governedActionsReady) {
            return "FULL";
        }
        if (investigationReady) {
            return "READ_ONLY";
        }
        return "SCAFFOLD";
    }
}
