package com.moveinsync.mobilitycopilot.approval.adapter.inmemory;

import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.domain.VersionedWorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optimistic-version checkpoint store for tests and the no-database demo profile. */
public class InMemoryWorkflowCheckpointStore implements WorkflowCheckpointStore {

    private final Map<UUID, VersionedWorkflowState> states = new ConcurrentHashMap<>();

    @Override
    public synchronized VersionedWorkflowState save(WorkflowState state, long expectedVersion) {
        VersionedWorkflowState current = states.get(state.runId());
        long currentVersion = current == null ? NEW_CHECKPOINT : current.version();
        if (currentVersion != expectedVersion) {
            throw new CheckpointConflictException(state.runId(), expectedVersion, currentVersion);
        }
        VersionedWorkflowState saved = new VersionedWorkflowState(state, currentVersion + 1, Instant.now());
        states.put(state.runId(), saved);
        return saved;
    }

    @Override
    public Optional<VersionedWorkflowState> find(UUID runId) {
        return Optional.ofNullable(states.get(runId));
    }

    /** Raised when two writers race on the same run. */
    public static class CheckpointConflictException extends IllegalStateException {
        public CheckpointConflictException(UUID runId, long expected, long actual) {
            super("Checkpoint version conflict for run " + runId + ": expected " + expected + " but found " + actual);
        }
    }
}
