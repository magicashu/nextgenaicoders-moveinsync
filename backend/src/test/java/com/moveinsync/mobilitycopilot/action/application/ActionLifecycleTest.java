package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.access.application.RoleBasedAccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.adapter.inmemory.InMemoryActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.adapter.mock.MockAdapters;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalLifecycle;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.audit.adapter.inmemory.InMemoryAuditLedger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionLifecycleTest {

    private final RoleBasedAccessAuthorizer authorizer = new RoleBasedAccessAuthorizer();
    private final InMemoryApprovalRepository approvals = new InMemoryApprovalRepository();
    private final InMemoryActionExecutionRepository executions = new InMemoryActionExecutionRepository();
    private final InMemoryAuditLedger audit = new InMemoryAuditLedger();
    private final List<MockActionAdapter> adapters = MockAdapters.all();
    private final ApprovalLifecycle approvalLifecycle = new ApprovalLifecycle(approvals, authorizer, audit);
    private final DeterministicActionRevalidator revalidator = new DeterministicActionRevalidator(authorizer, approvals, executions);
    private final IdempotentActionExecutor executor = new IdempotentActionExecutor(executions, adapters, audit);
    private final ActorContext manager = new ActorContext("manager-1", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER"));
    private final TenantContext tenant = new TenantContext("pinnacle-Slc");

    private ActionProposal proposal(UUID runId, ActionType type) {
        Instant now = Instant.now();
        return new ActionProposal(UUID.randomUUID(), runId, type, "title", "rationale", Map.of("businessUnit", "pinnacle-Slc"), "evidence-abc", now,
                now.plus(Duration.ofMinutes(30)), ActionStatus.DRAFT_REQUIRES_APPROVAL);
    }

    private ApprovalRequest approved(ActionProposal proposal) {
        ApprovalRequest request = new ApprovalRequest(UUID.randomUUID(), proposal.runId(), "pinnacle-Slc", proposal, proposal.evidenceVersion(), proposal.createdAt(), proposal.expiresAt());
        approvalLifecycle.request(request, "trace");
        approvalLifecycle.decide(manager, request.approvalId(), ApprovalDecisionType.APPROVE, "ok", null, "trace");
        return request;
    }

    private ActionExecutionCommand command(ActionProposal proposal) {
        return new ActionExecutionCommand(manager, tenant, proposal, proposal.runId() + ":" + proposal.actionId(), proposal.evidenceVersion(), Instant.now());
    }

    @Test
    void approvedActionRevalidatesAndExecutesExactlyOnce() {
        ActionProposal proposal = proposal(UUID.randomUUID(), ActionType.CREATE_SITE_SHIFT_WATCHLIST);
        approved(proposal);
        ActionExecutionCommand command = command(proposal);
        RevalidationResult revalidation = revalidator.revalidate(command);
        assertThat(revalidation.valid()).as(revalidation.reasons().toString()).isTrue();
        ExecutionReceipt first = executor.execute(command, revalidation);
        assertThat(first.status()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(first.externalReference()).startsWith("WATCH-");
        ExecutionReceipt duplicate = executor.execute(command, revalidator.revalidate(command));
        assertThat(duplicate).isEqualTo(first);
        assertThat(((MockAdapters.Watchlist) adapters.getFirst()).effectCount()).isEqualTo(1);
        assertThat(audit.findByRunId(proposal.runId())).extracting("eventType").contains("APPROVAL_REQUESTED", "APPROVAL_APPROVE", "ACTION_EFFECT_RECORDED", "ACTION_DUPLICATE_SUPPRESSED");
        assertThat(audit.verifyChain(proposal.runId())).isTrue();
    }

    @Test
    void concurrentDuplicatesProduceOneEffectAndOneReceipt() throws Exception {
        ActionProposal proposal = proposal(UUID.randomUUID(), ActionType.CREATE_INVESTIGATION_TICKET);
        approved(proposal);
        ActionExecutionCommand command = command(proposal);
        RevalidationResult revalidation = revalidator.revalidate(command);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ExecutionReceipt>> calls = java.util.stream.IntStream.range(0, 16).<Callable<ExecutionReceipt>>mapToObj(i -> () -> executor.execute(command, revalidation)).toList();
            List<Future<ExecutionReceipt>> futures = pool.invokeAll(calls);
            java.util.Set<String> references = new java.util.HashSet<>();
            for (Future<ExecutionReceipt> f : futures) {
                references.add(f.get().externalReference());
                assertThat(f.get().status()).isEqualTo(ActionStatus.EXECUTED);
            }
            assertThat(references).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(((MockAdapters.InvestigationTicket) adapters.get(1)).effectCount()).isEqualTo(1);
    }

    @Test
    void rejectedExpiredStaleOrUnapprovedActionsCannotExecute() {
        ActionProposal rejected = proposal(UUID.randomUUID(), ActionType.DRAFT_COMMUNICATION);
        ApprovalRequest request = new ApprovalRequest(UUID.randomUUID(), rejected.runId(), "pinnacle-Slc", rejected, rejected.evidenceVersion(), rejected.createdAt(), rejected.expiresAt());
        approvalLifecycle.request(request, "trace");
        approvalLifecycle.decide(manager, request.approvalId(), ApprovalDecisionType.REJECT, "no", null, "trace");
        RevalidationResult r1 = revalidator.revalidate(command(rejected));
        assertThat(r1.valid()).isFalse();
        assertThat(r1.reasons()).anyMatch(s -> s.contains("REJECTED"));

        ActionProposal unapproved = proposal(UUID.randomUUID(), ActionType.DRAFT_COMMUNICATION);
        assertThat(revalidator.revalidate(command(unapproved)).reasons()).anyMatch(s -> s.contains("no approval request"));

        ActionProposal approvedProposal = proposal(UUID.randomUUID(), ActionType.DRAFT_COMMUNICATION);
        approved(approvedProposal);
        ActionExecutionCommand stale = new ActionExecutionCommand(manager, tenant,
                new ActionProposal(approvedProposal.actionId(), approvedProposal.runId(), approvedProposal.type(), approvedProposal.title(), approvedProposal.rationale(),
                        approvedProposal.scope(), "evidence-new", approvedProposal.createdAt(), approvedProposal.expiresAt(), approvedProposal.status()),
                approvedProposal.runId() + ":" + approvedProposal.actionId(), "evidence-new", Instant.now());
        RevalidationResult r3 = revalidator.revalidate(stale);
        assertThat(r3.valid()).isFalse();
        assertThat(r3.reasons()).anyMatch(s -> s.startsWith("evidence:"));

        Instant past = Instant.now().minus(Duration.ofHours(2));
        ActionProposal expiredProposal = new ActionProposal(UUID.randomUUID(), UUID.randomUUID(), ActionType.DRAFT_COMMUNICATION, "t", "r", Map.of("businessUnit", "pinnacle-Slc"),
                "evidence-abc", past, past.plus(Duration.ofMinutes(30)), ActionStatus.DRAFT_REQUIRES_APPROVAL);
        assertThat(revalidator.revalidate(command(expiredProposal)).reasons()).anyMatch(s -> s.startsWith("expiry:"));

        ActorContext stranger = new ActorContext("orbit-1", "orbit-Slc", Set.of("TRANSPORT_MANAGER"));
        assertThatThrownBy(() -> new ActionExecutionCommand(stranger, tenant, approvedProposal, "k", "evidence-abc", Instant.now())).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> executor.execute(command(rejected), r1)).isInstanceOf(IllegalStateException.class);
        assertThat(adapters.stream().mapToInt(a -> ((MockAdapters.Ledger) a).effectCount()).sum()).isZero();
    }

    @Test
    void adapterFailureLeavesApprovedNotExecutedThenRetriesSafely() {
        ActionProposal proposal = proposal(UUID.randomUUID(), ActionType.DRAFT_VENDOR_ESCALATION);
        approved(proposal);
        MockAdapters.VendorEscalationDraft adapter = (MockAdapters.VendorEscalationDraft) adapters.get(2);
        adapter.failNext();
        ActionExecutionCommand command = command(proposal);
        ExecutionReceipt failed = executor.execute(command, revalidator.revalidate(command));
        assertThat(failed.status()).isEqualTo(ActionStatus.APPROVED_NOT_EXECUTED);
        assertThat(failed.message()).contains("Adapter failure");
        assertThat(adapter.effectCount()).isZero();
        assertThat(revalidator.revalidate(command).valid()).as("retry allowed: no effect was produced").isTrue();
        ExecutionReceipt retried = executor.execute(command, revalidator.revalidate(command));
        assertThat(retried.status()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(adapter.effectCount()).isEqualTo(1);
        assertThat(executions.find(command.idempotencyKey())).get().extracting("attempts").isEqualTo(2);
    }
}
