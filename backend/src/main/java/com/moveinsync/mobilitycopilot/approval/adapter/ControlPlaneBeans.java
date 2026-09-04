package com.moveinsync.mobilitycopilot.approval.adapter;

import com.moveinsync.mobilitycopilot.action.adapter.inmemory.InMemoryActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.adapter.mock.MockAdapters;
import com.moveinsync.mobilitycopilot.action.adapter.postgres.JdbcActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.application.ActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.application.MockActionAdapter;
import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryWorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.approval.adapter.postgres.JdbcApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.adapter.postgres.JdbcWorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalRepository;
import com.moveinsync.mobilitycopilot.audit.adapter.inmemory.InMemoryAuditLedger;
import com.moveinsync.mobilitycopilot.audit.adapter.postgres.JdbcAuditLedger;
import com.moveinsync.mobilitycopilot.audit.application.AuditLedger;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Control-plane adapter selection. {@code mobility.workflow.control-plane=postgres} (set by the
 * postgres profile) wires the JDBC adapters over the Flyway-managed tables; anything else keeps the
 * in-memory demo adapters, which is the explicitly approved fallback when PostgreSQL is unavailable.
 * Implementations expose ApprovalRepository / AuditLedger, which extend the frozen ApprovalStore /
 * AuditSink ports, so consumers of either type resolve to the same bean.
 */
@Configuration
public class ControlPlaneBeans {

    @Bean
    public List<MockActionAdapter> mockActionAdapters() {
        return MockAdapters.all();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "postgres")
    public WorkflowCheckpointStore jdbcCheckpointStore(DataSource dataSource) {
        return new JdbcWorkflowCheckpointStore(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "postgres")
    public ApprovalRepository jdbcApprovalRepository(DataSource dataSource) {
        return new JdbcApprovalRepository(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "postgres")
    public ActionExecutionRepository jdbcActionExecutionRepository(DataSource dataSource) {
        return new JdbcActionExecutionRepository(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "postgres")
    public AuditLedger jdbcAuditLedger(DataSource dataSource) {
        return new JdbcAuditLedger(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public WorkflowCheckpointStore inMemoryCheckpointStore() {
        return new InMemoryWorkflowCheckpointStore();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public ApprovalRepository inMemoryApprovalRepository() {
        return new InMemoryApprovalRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public ActionExecutionRepository inMemoryActionExecutionRepository() {
        return new InMemoryActionExecutionRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "mobility.workflow.control-plane", havingValue = "in-memory", matchIfMissing = true)
    public AuditLedger inMemoryAuditLedger() {
        return new InMemoryAuditLedger();
    }
}
