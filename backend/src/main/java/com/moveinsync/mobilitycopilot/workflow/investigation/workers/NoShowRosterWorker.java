package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class NoShowRosterWorker extends GovernedWorker {
    public NoShowRosterWorker(GovernedMetricService metrics) { super(metrics,WorkerType.NO_SHOW_ROSTER); }
}
