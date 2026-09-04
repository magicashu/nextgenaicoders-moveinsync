package com.moveinsync.mobilitycopilot.workflow.adapter.inmemory;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalStore;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boots the workflow with in-memory control-plane ports until the composition root selects the
 * PostgreSQL adapters by setting {@code mobility.workflow.control-plane=postgres}. Likewise the
 * language model stays absent until {@code mobility.workflow.language-model} names a provider.
 */
@Configuration
public class InMemoryControlPlaneBeans {

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public WorkflowCheckpointStore inMemoryCheckpointStore() {
        return new InMemoryControlPlane.CheckpointStore();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public ApprovalStore inMemoryApprovalStore() {
        return new InMemoryControlPlane.Approvals();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public AuditSink inMemoryAuditSink() {
        return new InMemoryControlPlane.Audit();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public AccessAuthorizer inMemoryAuthorizer() {
        return new InMemoryControlPlane.Authorizer();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    /** Implements both ActionRevalidator and ActionExecutor; a single bean keeps injection unambiguous. */
    public InMemoryControlPlane.Actions inMemoryActions() {
        return new InMemoryControlPlane.Actions();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.language-model", havingValue = "none", matchIfMissing = true)
    public LanguageModelPort noLanguageModel() {
        return new LanguageModelPort.Unavailable();
    }
}
