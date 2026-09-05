package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class CostBillingWorker extends GovernedWorker {
    public CostBillingWorker(GovernedMetricService metrics) { super(metrics,WorkerType.COST_BILLING); }
}
