package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class DelayReasonWorker extends GovernedWorker {
    public DelayReasonWorker(GovernedMetricService metrics) { super(metrics,WorkerType.DELAY_REASON); }
}
