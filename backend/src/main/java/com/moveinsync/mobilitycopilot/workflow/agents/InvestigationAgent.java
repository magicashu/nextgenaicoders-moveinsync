package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class InvestigationAgent {

    private final MetricService metricService;

    public InvestigationAgent(MetricService metricService) {
        this.metricService = metricService;
    }

    public InvestigationResult investigate(WorkflowState state, List<InvestigationTask> tasks) {
        if (tasks.size() > state.maxInvestigationSteps()) {
            throw new IllegalStateException("Investigation plan exceeds the configured step budget");
        }
        MetricResult headlineMetric = metricService.delayedTripRate(state.tenant(), state.asOfDate());
        return new InvestigationResult(headlineMetric, tasks);
    }

    public record InvestigationResult(MetricResult headlineMetric, List<InvestigationTask> completedTasks) {
    }
}
