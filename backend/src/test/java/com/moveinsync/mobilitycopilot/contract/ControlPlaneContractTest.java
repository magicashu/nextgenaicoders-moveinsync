package com.moveinsync.mobilitycopilot.contract;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlPlaneContractTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-06-08T08:00:00Z");

    @Test
    void freezesBoundedWorkflowDefaults() {
        WorkflowState state = WorkflowState.start(
                new TenantContext("pinnacle-Slc"),
                LocalDate.parse("2026-06-08"),
                4,
                1,
                12);

        assertThat(state.maxInvestigationSteps()).isEqualTo(4);
        assertThat(state.maxCorrectionCycles()).isEqualTo(1);
        assertThat(state.maxToolCalls()).isEqualTo(12);
    }

    @Test
    void rejectsCrossTenantExecutionCommand() {
        ActorContext actor = new ActorContext("manager-1", "orbit-Slc", Set.of("TRANSPORT_MANAGER"));

        assertThatThrownBy(() -> new ActionExecutionCommand(
                actor,
                new TenantContext("pinnacle-Slc"),
                proposal(),
                "run-1:action-1",
                "evidence-v1",
                CREATED_AT.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("business units must match");
    }

    @Test
    void editDecisionRequiresAnEditedProposal() {
        assertThatThrownBy(() -> new ApprovalDecision(
                UUID.randomUUID(),
                ACTION_ID,
                RUN_ID,
                ApprovalDecisionType.EDIT,
                "manager-1",
                CREATED_AT.plusSeconds(60),
                "Narrow the scope",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EDIT requires");
    }

    @Test
    void invalidRevalidationRequiresAnAuditableReason() {
        assertThatThrownBy(() -> new RevalidationResult(
                false,
                "evidence-v1",
                CREATED_AT.plusSeconds(60),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a reason");
    }

    @Test
    void executedReceiptRequiresCompletionTimestamp() {
        assertThatThrownBy(() -> new ExecutionReceipt(
                ACTION_ID,
                RUN_ID,
                "run-1:action-1",
                ActionStatus.EXECUTED,
                CREATED_AT,
                null,
                "ticket-1",
                "created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completedAt");
    }

    private ActionProposal proposal() {
        return new ActionProposal(
                ACTION_ID,
                RUN_ID,
                ActionType.CREATE_SITE_SHIFT_WATCHLIST,
                "Create Clearwater morning watchlist",
                "Investigate the cross-vendor deterioration.",
                Map.of("businessUnit", "pinnacle-Slc", "site", "Clearwater Campus"),
                "evidence-v1",
                CREATED_AT,
                CREATED_AT.plusSeconds(1_800),
                ActionStatus.DRAFT_REQUIRES_APPROVAL);
    }
}
