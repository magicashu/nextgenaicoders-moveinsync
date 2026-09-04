package com.moveinsync.mobilitycopilot.approval.adapter;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryWorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.approval.adapter.postgres.WorkflowStateJson;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.VersionedWorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryWorkflowCheckpointStoreTest {

    @Test
    void optimisticVersionsPreventLostUpdates() {
        InMemoryWorkflowCheckpointStore store = new InMemoryWorkflowCheckpointStore();
        WorkflowState state = WorkflowState.start(new TenantContext("pinnacle-Slc"), LocalDate.parse("2026-06-08"));
        VersionedWorkflowState v0 = store.save(state, WorkflowCheckpointStore.NEW_CHECKPOINT);
        assertThat(v0.version()).isZero();
        assertThatThrownBy(() -> store.save(state, WorkflowCheckpointStore.NEW_CHECKPOINT)).isInstanceOf(InMemoryWorkflowCheckpointStore.CheckpointConflictException.class);
        WorkflowState paused = new WorkflowState(state.runId(), state.tenant(), state.asOfDate(), WorkflowStep.AWAITING_APPROVAL, state.tasks(), 0, 4, 0, 1, 7, 12);
        VersionedWorkflowState v1 = store.save(paused, 0);
        assertThat(v1.version()).isEqualTo(1);
        assertThatThrownBy(() -> store.save(paused, 0)).isInstanceOf(InMemoryWorkflowCheckpointStore.CheckpointConflictException.class);
        assertThat(store.find(state.runId())).get().extracting(v -> v.state().step()).isEqualTo(WorkflowStep.AWAITING_APPROVAL);
    }

    @Test
    void frozenWorkflowStateRoundTripsThroughJson() {
        WorkflowState state = new WorkflowState(java.util.UUID.randomUUID(), new TenantContext("vanta-Aus"), LocalDate.parse("2026-08-01"), WorkflowStep.AWAITING_APPROVAL,
                List.of(new InvestigationTask("vendor", "q", Map.of("businessUnit", "vanta-Aus"))), 2, 4, 1, 1, 7, 12);
        String json = WorkflowStateJson.write(state);
        assertThat(json).contains("\"businessUnit\":\"vanta-Aus\"").contains("AWAITING_APPROVAL");
        assertThat(WorkflowStateJson.readState(json)).isEqualTo(state);
    }
}
