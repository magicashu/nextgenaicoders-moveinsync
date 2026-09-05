package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowCheckpoint;
import java.util.Optional;
import java.util.UUID;

/** WS3: updates must atomically compare version and tenant before writing. */
public interface WorkflowCheckpointStore {
    Optional<WorkflowCheckpoint> find(TenantContext tenant, UUID runId);
    boolean create(WorkflowCheckpoint initial);
    boolean compareAndSet(WorkflowCheckpoint next, long expectedVersion);
}
