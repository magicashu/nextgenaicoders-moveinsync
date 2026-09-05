package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.workflow.application.*;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;

/** Deterministic report-only default. Explicit metric requests use AgentWorkflowService. */
@Service
public final class DeterministicWorkflowEngine implements WorkflowEngine {
    private final AgentWorkflowService agents;
    public DeterministicWorkflowEngine(AgentWorkflowService agents){this.agents=agents;}
    @Override public WorkflowCheckpoint start(RunContext context) {
        RunGuards.requireAuthorized(context);
        var end=context.asOfDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
        var request=new MetricRequest(context.tenant(),MetricId.M01_DELAYED_TRIP_RATE,MetricRequest.Measure.VALUE,
                new MetricWindow(end.minusDays(6),end),Map.of(),context.versions().data());
        var result=agents.investigate(context,request);
        var verification=result.brief().verification();
        boolean partial=!result.investigation().pendingTasks().isEmpty()||verification.claims().isEmpty();
        return new WorkflowCheckpoint(context,1,WorkflowNode.COMPOSE_DECISION_BRIEF,partial?WorkflowCheckpoint.Status.PARTIAL:WorkflowCheckpoint.Status.COMPLETED,
                result.investigation().evidence(),verification.claims(),null,result.brief().caveats());
    }
    @Override public WorkflowCheckpoint resume(RunContext context,ApprovalDecision decision) {
        RunGuards.requireAuthorized(context);
        throw new IllegalStateException("This workflow creates reports/drafts only; approval resume requires the separate durable action service.");
    }
}
