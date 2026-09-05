package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.workflow.domain.VersionedWorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowCheckpointStore {

    long NEW_CHECKPOINT = -1;

    VersionedWorkflowState save(WorkflowState state, long expectedVersion);

    Optional<VersionedWorkflowState> find(UUID runId);
}
