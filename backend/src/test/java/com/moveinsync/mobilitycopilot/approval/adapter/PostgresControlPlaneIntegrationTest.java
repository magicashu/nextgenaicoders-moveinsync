package com.moveinsync.mobilitycopilot.approval.adapter;

import com.moveinsync.mobilitycopilot.access.application.RoleBasedAccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.adapter.mock.MockAdapters;
import com.moveinsync.mobilitycopilot.action.adapter.postgres.JdbcActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.application.DeterministicActionRevalidator;
import com.moveinsync.mobilitycopilot.action.application.IdempotentActionExecutor;
import com.moveinsync.mobilitycopilot.action.application.MockActionAdapter;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryWorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.approval.adapter.postgres.JdbcApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.adapter.postgres.JdbcWorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalLifecycle;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalStatus;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalTransitionException;
import com.moveinsync.mobilitycopilot.audit.adapter.postgres.JdbcAuditLedger;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL integration (skipped unless MOBILITY_TEST_POSTGRES_URL is set; run with -Ppostgres so the
 * driver is on the classpath). Applies the Flyway migration SQL over plain JDBC in order, then proves
 * optimistic checkpoints, approval transitions, cross-process idempotency and database-enforced
 * append-only audit history.
 */
class PostgresControlPlaneIntegrationTest {

    private static DataSource dataSource;
    private static final ActorContext MANAGER = new ActorContext("manager-1", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER"));
    private static final TenantContext TENANT = new TenantContext("pinnacle-Slc");

    @BeforeAll
    static void connect() throws Exception {
        String url = System.getenv("MOBILITY_TEST_POSTGRES_URL");
        assumeTrue(url != null && !url.isBlank(), "MOBILITY_TEST_POSTGRES_URL not set");
        String user = System.getenv().getOrDefault("MOBILITY_TEST_POSTGRES_USER", "mobility");
        String password = System.getenv().getOrDefault("MOBILITY_TEST_POSTGRES_PASSWORD", "");
        dataSource = new SimpleDataSource(url, user, password);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
            for (String migration : List.of("db/migration/V1__workflow_control_tables.sql", "db/migration/V2__approval_action_audit_lifecycle.sql")) {
                try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(migration)) {
                    statement.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static WorkflowState checkpointFor(UUID runId) {
        return new WorkflowState(runId, TENANT, LocalDate.parse("2026-06-08"), WorkflowStep.AWAITING_APPROVAL, List.of(), 2, 4, 0, 1, 7, 12);
    }

    private static ActionProposal proposal(UUID runId, ActionType type) {
        Instant now = Instant.now();
        return new ActionProposal(UUID.randomUUID(), runId, type, "Watch Clearwater", "rationale", Map.of("businessUnit", "pinnacle-Slc", "site_id", "Clearwater Campus"),
                "evidence-abc", now, now.plus(Duration.ofMinutes(30)), ActionStatus.DRAFT_REQUIRES_APPROVAL);
    }

    @Test
    void checkpointsUseOptimisticVersionsAndSurviveRestart() {
        JdbcWorkflowCheckpointStore store = new JdbcWorkflowCheckpointStore(dataSource);
        UUID runId = UUID.randomUUID();
        WorkflowState initial = WorkflowState.start(TENANT, LocalDate.parse("2026-06-08"));
        WorkflowState state = new WorkflowState(runId, TENANT, initial.asOfDate(), WorkflowStep.INITIALIZED, List.of(), 0, 4, 0, 1, 0, 12);
        assertThat(store.save(state, WorkflowCheckpointStore.NEW_CHECKPOINT).version()).isZero();
        assertThatThrownBy(() -> store.save(state, WorkflowCheckpointStore.NEW_CHECKPOINT)).isInstanceOf(InMemoryWorkflowCheckpointStore.CheckpointConflictException.class);
        assertThat(store.save(checkpointFor(runId), 0).version()).isEqualTo(1);
        assertThatThrownBy(() -> store.save(checkpointFor(runId), 0)).isInstanceOf(InMemoryWorkflowCheckpointStore.CheckpointConflictException.class);
        // "restart": a brand-new store instance sees the paused state
        JdbcWorkflowCheckpointStore restarted = new JdbcWorkflowCheckpointStore(dataSource);
        var found = restarted.find(runId).orElseThrow();
        assertThat(found.version()).isEqualTo(1);
        assertThat(found.state().step()).isEqualTo(WorkflowStep.AWAITING_APPROVAL);
        assertThat(found.state().toolCalls()).isEqualTo(7);
    }

    @Test
    void approvalTransitionsArePersistedAndRaceSafe() throws Exception {
        JdbcWorkflowCheckpointStore checkpoints = new JdbcWorkflowCheckpointStore(dataSource);
        JdbcApprovalRepository approvals = new JdbcApprovalRepository(dataSource);
        JdbcAuditLedger audit = new JdbcAuditLedger(dataSource);
        ApprovalLifecycle lifecycle = new ApprovalLifecycle(approvals, new RoleBasedAccessAuthorizer(), audit);
        UUID runId = UUID.randomUUID();
        checkpoints.save(checkpointFor(runId), WorkflowCheckpointStore.NEW_CHECKPOINT);
        ActionProposal proposal = proposal(runId, ActionType.CREATE_SITE_SHIFT_WATCHLIST);
        ApprovalRequest request = new ApprovalRequest(UUID.randomUUID(), runId, "pinnacle-Slc", proposal, "evidence-abc", proposal.createdAt(), proposal.expiresAt());
        lifecycle.request(request, "trace-pg");
        assertThatThrownBy(() -> approvals.create(new ApprovalRequest(UUID.randomUUID(), runId, "pinnacle-Slc", proposal, "evidence-abc", proposal.createdAt(), proposal.expiresAt())))
                .isInstanceOf(ApprovalTransitionException.class);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> race = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                ApprovalDecisionType type = i % 2 == 0 ? ApprovalDecisionType.APPROVE : ApprovalDecisionType.REJECT;
                race.add(() -> {
                    try {
                        return lifecycle.decide(MANAGER, request.approvalId(), type, "race", null, "trace-pg").status().name();
                    } catch (ApprovalTransitionException e) {
                        return "REFUSED:" + e.code();
                    }
                });
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : pool.invokeAll(race)) {
                outcomes.add(f.get());
            }
            assertThat(outcomes.stream().filter(o -> !o.startsWith("REFUSED")).count()).as(outcomes.toString()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        var record = approvals.findRecord(request.approvalId()).orElseThrow();
        assertThat(record.status()).isIn(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED);
        assertThat(record.decision()).isNotNull();
        assertThat(new JdbcApprovalRepository(dataSource).findByActionId(proposal.actionId())).isPresent();
        assertThat(audit.findByRunId(runId)).extracting("eventType").contains("APPROVAL_REQUESTED");
        assertThat(audit.verifyChain(runId)).isTrue();
    }

    @Test
    void idempotentExecutionAcrossProcessesProducesOneEffect() throws Exception {
        JdbcWorkflowCheckpointStore checkpoints = new JdbcWorkflowCheckpointStore(dataSource);
        JdbcApprovalRepository approvals = new JdbcApprovalRepository(dataSource);
        JdbcAuditLedger audit = new JdbcAuditLedger(dataSource);
        ApprovalLifecycle lifecycle = new ApprovalLifecycle(approvals, new RoleBasedAccessAuthorizer(), audit);
        UUID runId = UUID.randomUUID();
        checkpoints.save(checkpointFor(runId), WorkflowCheckpointStore.NEW_CHECKPOINT);
        ActionProposal proposal = proposal(runId, ActionType.CREATE_INVESTIGATION_TICKET);
        ApprovalRequest request = new ApprovalRequest(UUID.randomUUID(), runId, "pinnacle-Slc", proposal, "evidence-abc", proposal.createdAt(), proposal.expiresAt());
        lifecycle.request(request, "trace-pg");
        lifecycle.decide(MANAGER, request.approvalId(), ApprovalDecisionType.APPROVE, "ok", null, "trace-pg");
        ActionExecutionCommand command = new ActionExecutionCommand(MANAGER, TENANT, proposal, runId + ":" + proposal.actionId(), "evidence-abc", Instant.now());

        // two "processes": separate repository, adapter and executor instances sharing only the database
        List<MockActionAdapter> adaptersA = MockAdapters.all();
        List<MockActionAdapter> adaptersB = MockAdapters.all();
        IdempotentActionExecutor executorA = new IdempotentActionExecutor(new JdbcActionExecutionRepository(dataSource), adaptersA, audit);
        IdempotentActionExecutor executorB = new IdempotentActionExecutor(new JdbcActionExecutionRepository(dataSource), adaptersB, audit);
        DeterministicActionRevalidator revalidator = new DeterministicActionRevalidator(new RoleBasedAccessAuthorizer(), approvals, new JdbcActionExecutionRepository(dataSource));
        RevalidationResult revalidation = revalidator.revalidate(command);
        assertThat(revalidation.valid()).as(revalidation.reasons().toString()).isTrue();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ExecutionReceipt>> calls = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                IdempotentActionExecutor executor = i % 2 == 0 ? executorA : executorB;
                calls.add(() -> executor.execute(command, revalidation));
            }
            java.util.Set<String> references = new java.util.HashSet<>();
            for (Future<ExecutionReceipt> f : pool.invokeAll(calls)) {
                ExecutionReceipt receipt = f.get();
                assertThat(receipt.status()).isEqualTo(ActionStatus.EXECUTED);
                references.add(receipt.externalReference());
            }
            assertThat(references).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        int effects = ((MockAdapters.Ledger) adaptersA.get(1)).effectCount() + ((MockAdapters.Ledger) adaptersB.get(1)).effectCount();
        assertThat(effects).isEqualTo(1);
        assertThat(new JdbcActionExecutionRepository(dataSource).find(command.idempotencyKey())).get().extracting("status").isEqualTo(ActionStatus.EXECUTED);
        assertThat(revalidator.revalidate(command).valid()).as("same key may return the same receipt").isTrue();
    }

    @Test
    void auditHistoryIsAppendOnlyAtTheDatabase() throws Exception {
        JdbcAuditLedger ledger = new JdbcAuditLedger(dataSource);
        UUID runId = UUID.randomUUID();
        AuditEvent event = ledger.append(new AuditEvent(UUID.randomUUID(), runId, "pinnacle-Slc", "RUN_SUMMARY", Map.of("finalStep", "EXECUTED"), Instant.now(), "trace-pg"));
        ledger.append(new AuditEvent(UUID.randomUUID(), runId, "pinnacle-Slc", "ACTION_EXECUTED", Map.of("actionId", "a"), Instant.now(), "trace-pg"));
        assertThat(ledger.verifyChain(runId)).isTrue();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE audit_event SET event_type = 'FORGED' WHERE event_id = '" + event.eventId() + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit_event WHERE event_id = '" + event.eventId() + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
        }
        assertThat(ledger.chainForRun(runId)).hasSize(2);
        assertThat(ledger.recentForBusinessUnit("pinnacle-Slc", 1)).singleElement().extracting(c -> c.event().eventType()).isEqualTo("ACTION_EXECUTED");
    }

    /** Minimal javax.sql.DataSource over DriverManager so the test needs no Spring JDBC module. */
    static final class SimpleDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, password); }
        @Override public Connection getConnection(String username, String pw) throws SQLException { return DriverManager.getConnection(url, username, pw); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
