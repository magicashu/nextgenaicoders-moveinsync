package com.moveinsync.mobilitycopilot.workflow.adapter.inmemory;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.application.ActionExecutor;
import com.moveinsync.mobilitycopilot.action.application.ActionRevalidator;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalStore;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.domain.VersionedWorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process implementations of the frozen control-plane ports so the workflow can boot and be tested
 * before the PostgreSQL adapters from the governance workstream are wired. They honour the same
 * contracts: optimistic checkpoint versions, append-only audit, exactly-one effect per idempotency key.
 * Never a production substitute; the composition root replaces them.
 */
public final class InMemoryControlPlane {

    private InMemoryControlPlane() {
    }

    public static final class CheckpointStore implements WorkflowCheckpointStore {
        private final Map<UUID, VersionedWorkflowState> states = new ConcurrentHashMap<>();

        @Override
        public synchronized VersionedWorkflowState save(WorkflowState state, long expectedVersion) {
            VersionedWorkflowState current = states.get(state.runId());
            long currentVersion = current == null ? NEW_CHECKPOINT : current.version();
            if (currentVersion != expectedVersion) {
                throw new IllegalStateException("Checkpoint version conflict for " + state.runId() + ": expected " + expectedVersion + " but was " + currentVersion);
            }
            VersionedWorkflowState saved = new VersionedWorkflowState(state, currentVersion + 1, Instant.now());
            states.put(state.runId(), saved);
            return saved;
        }

        @Override
        public Optional<VersionedWorkflowState> find(UUID runId) {
            return Optional.ofNullable(states.get(runId));
        }
    }

    public static final class Approvals implements ApprovalStore {
        private final Map<UUID, ApprovalRequest> requests = new ConcurrentHashMap<>();
        private final Map<UUID, ApprovalDecision> decisions = new ConcurrentHashMap<>();

        @Override
        public ApprovalRequest create(ApprovalRequest request) {
            requests.put(request.approvalId(), request);
            return request;
        }

        @Override
        public ApprovalDecision decide(ApprovalDecision decision) {
            if (!requests.containsKey(decision.approvalId())) {
                throw new IllegalStateException("Unknown approval " + decision.approvalId());
            }
            decisions.putIfAbsent(decision.approvalId(), decision);
            return decisions.get(decision.approvalId());
        }

        @Override
        public Optional<ApprovalRequest> findRequest(UUID approvalId) {
            return Optional.ofNullable(requests.get(approvalId));
        }

        @Override
        public Optional<ApprovalDecision> findDecision(UUID approvalId) {
            return Optional.ofNullable(decisions.get(approvalId));
        }
    }

    public static final class Audit implements AuditSink {
        private final List<AuditEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public AuditEvent append(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public List<AuditEvent> findByRunId(UUID runId) {
            return events.stream().filter(e -> e.runId().equals(runId)).toList();
        }

        public List<AuditEvent> all() {
            return List.copyOf(events);
        }
    }

    /** Actor must belong to the tenant; roles map to permissions deterministically. */
    public static final class Authorizer implements AccessAuthorizer {
        private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = Map.of(
                "TRANSPORT_MANAGER", Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.PROPOSE_ACTION, Permission.APPROVE_ACTION, Permission.VIEW_AUDIT),
                "FACILITIES_HEAD", Set.of(Permission.READ_TENANT_METRICS, Permission.INVESTIGATE_TENANT, Permission.VIEW_CROSS_TENANT_PEERS, Permission.PROPOSE_ACTION, Permission.APPROVE_ACTION, Permission.VIEW_AUDIT),
                "LINE_MANAGER", Set.of(Permission.READ_TENANT_METRICS, Permission.VIEW_AUDIT));

        @Override
        public void require(ActorContext actor, TenantContext tenant, Permission permission) {
            if (!actor.businessUnit().equals(tenant.businessUnit())) {
                throw new SecurityException("Actor " + actor.actorId() + " is not authorized for business unit " + tenant.businessUnit());
            }
            boolean granted = actor.roles().stream().anyMatch(r -> ROLE_PERMISSIONS.getOrDefault(r, Set.of()).contains(permission));
            if (!granted) {
                throw new SecurityException("Actor " + actor.actorId() + " lacks " + permission);
            }
        }
    }

    /** Mock executor with idempotency: the same key always returns the first receipt. */
    public static final class Actions implements ActionRevalidator, ActionExecutor {
        private final Map<String, ExecutionReceipt> receipts = new ConcurrentHashMap<>();
        private final List<String> effects = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile boolean failNext;

        @Override
        public RevalidationResult revalidate(ActionExecutionCommand command) {
            List<String> reasons = new ArrayList<>();
            if (!command.proposal().expiresAt().isAfter(command.requestedAt())) {
                reasons.add("proposal expired");
            }
            if (command.proposal().status() != ActionStatus.DRAFT_REQUIRES_APPROVAL) {
                reasons.add("proposal is not a draft");
            }
            return new RevalidationResult(reasons.isEmpty(), command.expectedEvidenceVersion(), command.requestedAt(), reasons);
        }

        @Override
        public synchronized ExecutionReceipt execute(ActionExecutionCommand command, RevalidationResult revalidation) {
            if (!revalidation.valid()) {
                throw new IllegalStateException("Refusing to execute without a valid revalidation");
            }
            ExecutionReceipt existing = receipts.get(command.idempotencyKey());
            if (existing != null) {
                return existing;
            }
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated adapter outage");
            }
            Instant now = Instant.now();
            effects.add(command.idempotencyKey());
            ExecutionReceipt receipt = new ExecutionReceipt(command.proposal().actionId(), command.proposal().runId(), command.idempotencyKey(),
                    ActionStatus.EXECUTED, now, now, "mock-" + command.proposal().type().name().toLowerCase(java.util.Locale.ROOT) + "-" + effects.size(),
                    "Mock " + command.proposal().type() + " created");
            receipts.put(command.idempotencyKey(), receipt);
            return receipt;
        }

        public int effectCount() {
            return effects.size();
        }

        public void failNextExecution() {
            this.failNext = true;
        }
    }
}
