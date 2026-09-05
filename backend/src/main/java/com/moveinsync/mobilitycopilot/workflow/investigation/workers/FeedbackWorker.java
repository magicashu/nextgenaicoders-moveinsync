package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import org.springframework.stereotype.Component;

@Component
public final class FeedbackWorker extends GovernedWorker {
    public FeedbackWorker(GovernedMetricService metrics) { super(metrics,WorkerType.FEEDBACK); }
}
