package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class TrackingSafetyWorker extends GovernedWorker {
    public TrackingSafetyWorker(GovernedMetricService metrics) { super(metrics,WorkerType.TRACKING_SAFETY); }
}
