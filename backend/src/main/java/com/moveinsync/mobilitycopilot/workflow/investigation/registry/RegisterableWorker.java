package com.moveinsync.mobilitycopilot.workflow.investigation.registry;

import com.moveinsync.mobilitycopilot.workflow.investigation.InvestigationTool;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;

/**
 * Marker interface for workers that self-declare their WorkerType for registry discovery.
 * All seven worker implementations must implement this interface.
 */
public interface RegisterableWorker<T> extends InvestigationTool<T> {
    WorkerType workerType();
}
