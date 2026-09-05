package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorAgent;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic fallback Supervisor — active when no AI-backed SupervisorAgent bean is present.
 *
 * Always produces the same safe plan: run all seven workers with the run window.
 * This ensures nodes 8–11 can execute end-to-end while WS2 builds the real planner.
 *
 * Replace with the AI-backed impl by registering a @Primary or @ConditionalOnProperty bean.
 */
@Service
@ConditionalOnMissingBean(name = "aiSupervisorAgent")
public final class FallbackSupervisorAgent implements SupervisorAgent {

    @Override
    public InvestigationPlan plan(RunContext context, String issueId) {
        MetricWindow window = new MetricWindow(
                context.asOfDate().minusWeeks(1), context.asOfDate());

        List<InvestigationTask> tasks = List.of(
                task("t-vendor", WorkerType.VENDOR,
                        "Did one vendor worsen or did all vendors worsen?", context, window, MetricId.M01_DELAYED_TRIP_RATE),
                task("t-site", WorkerType.SITE_SHIFT_DIRECTION,
                        "Where and when is the delay increase concentrated?", context, window, MetricId.M01_DELAYED_TRIP_RATE),
                task("t-reason", WorkerType.DELAY_REASON,
                        "Which reason categories contribute to delays?", context, window, MetricId.M03_DELAY_REASON_MIX),
                task("t-noshow", WorkerType.NO_SHOW_ROSTER,
                        "What do eligible employee-leg no-show rates show?", context, window, MetricId.M06_NO_SHOW_RATE),
                task("t-feedback", WorkerType.FEEDBACK,
                        "What do driver and safety ratings show?", context, window, MetricId.M12_MEAN_DRIVER_SAFETY_RATING),
                task("t-alerts", WorkerType.TRACKING_SAFETY,
                        "Are alert rates changing operationally or due to a recording regime change?",
                        context, window, MetricId.M13_ALERT_RATE),
                task("t-cost", WorkerType.COST_BILLING,
                        "What do billed-cost measures show?", context, window, MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP)
        );

        return new InvestigationPlan(
                UUID.randomUUID().toString(),
                issueId,
                tasks,
                Set.of("vendor-evidence", "site-evidence"),
                List.of("all workers executed", "budget exhausted")
        );
    }

    private InvestigationTask task(String taskId, WorkerType worker, String question,
                                    RunContext context, MetricWindow window, MetricId metricId) {
        MetricRequest request = new MetricRequest(
                context.tenant(), metricId,
                MetricRequest.Measure.VALUE,
                window, Map.of(), context.versions().data()
        );
        return new InvestigationTask(taskId, worker, question, List.of(request), List.of());
    }
}
