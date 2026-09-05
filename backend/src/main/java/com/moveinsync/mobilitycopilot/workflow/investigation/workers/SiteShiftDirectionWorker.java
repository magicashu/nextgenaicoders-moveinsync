package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class SiteShiftDirectionWorker extends GovernedWorker {
    public SiteShiftDirectionWorker(GovernedMetricService metrics) { super(metrics,WorkerType.SITE_SHIFT_DIRECTION); }
}
