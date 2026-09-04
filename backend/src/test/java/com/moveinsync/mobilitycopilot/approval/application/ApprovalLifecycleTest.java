package com.moveinsync.mobilitycopilot.approval.application;

import com.moveinsync.mobilitycopilot.access.application.RoleBasedAccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.AccessDeniedException;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalStatus;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalTransitionException;
import com.moveinsync.mobilitycopilot.audit.adapter.inmemory.InMemoryAuditLedger;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-06-08T08:00:00Z");
    private final InMemoryApprovalRepository repository = new InMemoryApprovalRepository();
    private final InMemoryAuditLedger audit = new InMemoryAuditLedger();
    private final MutableClock clock = new MutableClock(T0);
    private final ApprovalLifecycle lifecycle = new ApprovalLifecycle(repository, new RoleBasedAccessAuthorizer(), audit, clock);
    private final ActorContext manager = new ActorContext("manager-1", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER"));
    private final UUID runId = UUID.randomUUID();

    private ApprovalProposalPair pending() {
        Instant now = clock.instant();
        ActionProposal proposal = new ActionProposal(UUID.randomUUID(), runId, ActionType.CREATE_SITE_SHIFT_WATCHLIST, "Watch Clearwater", "rationale",
                Map.of("businessUnit", "pinnacle-Slc", "site_id", "Clearwater Campus"), "evidence-abc", now, now.plus(Duration.ofMinutes(30)), ActionStatus.DRAFT_REQUIRES_APPROVAL);
        ApprovalRequest request = new ApprovalRequest(UUID.randomUUID(), runId, "pinnacle-Slc", proposal, "evidence-abc", now, now.plus(Duration.ofMinutes(30)));
        lifecycle.request(request, "trace-1");
        return new ApprovalProposalPair(request, proposal);
    }

    record ApprovalProposalPair(ApprovalRequest request, ActionProposal proposal) {
    }

    @Test
    void approveRejectAndEditAreTerminalAndAudited() {
        var a = pending();
        ApprovalRecord approved = lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.APPROVE, "go", null, "trace-1");
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.decision().decidedBy()).isEqualTo("manager-1");
        assertThatThrownBy(() -> lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.REJECT, null, null, "trace-1"))
                .isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("ALREADY_DECIDED");

        var b = pending();
        assertThat(lifecycle.decide(manager, b.request().approvalId(), ApprovalDecisionType.REJECT, "no", null, "trace-1").status()).isEqualTo(ApprovalStatus.REJECTED);

        var c = pending();
        Map<String, String> scope = new LinkedHashMap<>(c.proposal().scope());
        scope.put("watchDays", "3");
        ActionProposal edited = new ActionProposal(c.proposal().actionId(), runId, c.proposal().type(), c.proposal().title(), c.proposal().rationale(), scope,
                c.proposal().evidenceVersion(), c.proposal().createdAt(), c.proposal().expiresAt(), c.proposal().status());
        ApprovalRecord editedRecord = lifecycle.decide(manager, c.request().approvalId(), ApprovalDecisionType.EDIT, "narrow", edited, "trace-1");
        assertThat(editedRecord.status()).isEqualTo(ApprovalStatus.EDITED);
        assertThat(editedRecord.decision().editedProposal().scope()).containsEntry("watchDays", "3");

        assertThat(audit.findByRunId(runId)).extracting("eventType").contains("APPROVAL_REQUESTED", "APPROVAL_APPROVE", "APPROVAL_REJECT", "APPROVAL_EDIT");
        assertThat(audit.verifyChain(runId)).isTrue();
    }

    @Test
    void expiredRequestsCannotBeApprovedAndBecomeExpired() {
        var a = pending();
        clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.APPROVE, null, null, "trace-1"))
                .isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("EXPIRED");
        assertThat(repository.findRecord(a.request().approvalId())).get().extracting(ApprovalRecord::status).isEqualTo(ApprovalStatus.EXPIRED);
        var b = pending();
        assertThat(lifecycle.expirePending("pinnacle-Slc", "trace-1")).isEqualTo(0);
        clock.advance(Duration.ofMinutes(31));
        assertThat(lifecycle.expirePending("pinnacle-Slc", "trace-1")).isEqualTo(1);
        assertThat(repository.findRecord(b.request().approvalId())).get().extracting(ApprovalRecord::status).isEqualTo(ApprovalStatus.EXPIRED);
    }

    @Test
    void crossTenantOrUnderprivilegedActorsCannotDecide() {
        var a = pending();
        ActorContext orbit = new ActorContext("orbit-1", "orbit-Slc", Set.of("TRANSPORT_MANAGER"));
        ActorContext line = new ActorContext("line-1", "pinnacle-Slc", Set.of("LINE_MANAGER"));
        assertThatThrownBy(() -> lifecycle.decide(orbit, a.request().approvalId(), ApprovalDecisionType.APPROVE, null, null, "t")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> lifecycle.decide(line, a.request().approvalId(), ApprovalDecisionType.APPROVE, null, null, "t")).isInstanceOf(AccessDeniedException.class);
        assertThat(repository.findRecord(a.request().approvalId())).get().extracting(ApprovalRecord::status).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void editsCannotWidenTenantEvidenceOrEscalate() {
        var a = pending();
        ActionProposal p = a.proposal();
        ActionProposal otherTenant = new ActionProposal(p.actionId(), runId, p.type(), p.title(), p.rationale(), Map.of("businessUnit", "orbit-Slc"), p.evidenceVersion(), p.createdAt(), p.expiresAt(), p.status());
        assertThatThrownBy(() -> lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.EDIT, null, otherTenant, "t"))
                .isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("EDIT_TENANT");
        ActionProposal otherEvidence = new ActionProposal(p.actionId(), runId, p.type(), p.title(), p.rationale(), p.scope(), "evidence-zzz", p.createdAt(), p.expiresAt(), p.status());
        assertThatThrownBy(() -> lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.EDIT, null, otherEvidence, "t"))
                .isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("EDIT_EVIDENCE");
        ActionProposal escalate = new ActionProposal(p.actionId(), runId, ActionType.DRAFT_VENDOR_ESCALATION, p.title(), p.rationale(), p.scope(), p.evidenceVersion(), p.createdAt(), p.expiresAt(), p.status());
        assertThatThrownBy(() -> lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.EDIT, null, escalate, "t"))
                .isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("EDIT_ESCALATION");
        assertThatThrownBy(() -> lifecycle.decide(manager, a.request().approvalId(), ApprovalDecisionType.EDIT, null, null, "t"))
                .isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("EDIT_REQUIRES_PROPOSAL");
        assertThat(repository.findRecord(a.request().approvalId())).get().extracting(ApprovalRecord::status).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void onlyOnePendingApprovalPerAction() {
        var a = pending();
        ApprovalRequest duplicate = new ApprovalRequest(UUID.randomUUID(), runId, "pinnacle-Slc", a.proposal(), "evidence-abc", T0, T0.plus(Duration.ofMinutes(30)));
        assertThatThrownBy(() -> repository.create(duplicate)).isInstanceOf(ApprovalTransitionException.class).extracting("code").isEqualTo("DUPLICATE_ACTION");
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
