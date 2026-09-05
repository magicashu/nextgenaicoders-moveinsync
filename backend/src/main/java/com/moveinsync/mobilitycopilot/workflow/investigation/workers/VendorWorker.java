package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class VendorWorker extends GovernedWorker {
    public VendorWorker(GovernedMetricService metrics) { super(metrics,WorkerType.VENDOR); }
}
