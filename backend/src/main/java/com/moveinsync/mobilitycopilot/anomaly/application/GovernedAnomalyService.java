package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyIssue;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.List;

/** WS1: G1 site/shift explanation, G2 caveats, G3 regime-change exclusion; no invented thresholds. */
public interface GovernedAnomalyService {
    List<AnomalyIssue> detectAndPrioritize(RunContext context, List<MetricEvidence> snapshot);
}
