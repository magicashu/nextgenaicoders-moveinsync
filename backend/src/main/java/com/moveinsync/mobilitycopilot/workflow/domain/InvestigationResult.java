package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import java.util.List;

/** WS2: completedTasks must describe tools actually executed, never merely the proposed plan. */
public record InvestigationResult(List<MetricEvidence> evidence, List<InvestigationTask> completedTasks,
                                  List<InvestigationTask> pendingTasks, List<String> warnings) {}
