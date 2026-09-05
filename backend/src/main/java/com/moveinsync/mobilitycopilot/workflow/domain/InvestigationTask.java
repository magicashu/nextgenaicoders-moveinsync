package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import java.util.List;

/** WS2: tasks refer to an allowlisted worker and validated governed requests. */
public record InvestigationTask(String taskId, WorkerType worker, String question,
                                List<MetricRequest> requests, List<String> dependencies) {}
