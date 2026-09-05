package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyIssue;
import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.OfficialDuckDbGovernedMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.agents.*;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Callable backend use case. The actor and tenant come from a trusted server boundary. */
@Service
public final class AgentWorkflowService {
    private final GovernedSupervisorAgent supervisor;
    private final InvestigationAgent investigator;
    private final EvidenceCriticAgent critic;
    private final BriefingActionAgent briefing;
    private final OfficialDuckDbGovernedMetricService metrics;
    public AgentWorkflowService(GovernedSupervisorAgent supervisor,InvestigationAgent investigator,EvidenceCriticAgent critic,
            BriefingActionAgent briefing,OfficialDuckDbGovernedMetricService metrics) {
        this.supervisor=supervisor;this.investigator=investigator;this.critic=critic;this.briefing=briefing;this.metrics=metrics;
    }
    public Result investigate(RunContext context,MetricRequest request) {
        return investigate(context, request, "");
    }
    public Result investigate(RunContext context,MetricRequest request,String question) {
        RunGuards.requireRequest(context,request);RunGuards.requireTime(context);
        InvestigationPlan plan=plan(context, request, question);
        return execute(context, plan);
    }
    public InvestigationPlan plan(RunContext context, MetricRequest request, String question) {
        RunGuards.requireRequest(context,request);RunGuards.requireTime(context);
        MetricRequest primaryRequest=request.metricId()==MetricId.M03_DELAY_REASON_MIX&&request.measure()==MetricRequest.Measure.VALUE
                ?new MetricRequest(request.tenant(),request.metricId(),MetricRequest.Measure.REASON_EMPLOYEE,request.window(),request.filters(),request.dataVersion()):request;
        MetricEvidence primary=metrics.compute(primaryRequest);
        if(primary.status()==MetricStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("Requested metric is unavailable; no investigation plan can be created");
        }
        Map<MetricId,CapabilityMatrix.Capability> capabilities=new EnumMap<>(MetricId.class);
        // Implementation availability does not imply a usable population in every requested window.
        for(var id:MetricId.values())capabilities.put(id,new CapabilityMatrix.Capability(
                CapabilityMatrix.Status.DERIVABLE_WITH_CAVEAT,"Implemented contract; query-specific eligibility and coverage are validated by the store."));
        if(Set.of("vanta-Aus","vanta-Sea").contains(context.tenant().businessUnit())) {
            capabilities.put(MetricId.M10_COST_PER_BILLED_KM,new CapabilityMatrix.Capability(
                    CapabilityMatrix.Status.UNAVAILABLE,"Q2: billed-km coverage is unreliable for this tenant."));
        }
        var issue=new AnomalyIssue("request-"+context.runId(),context.tenant(),context.versions().data(),
                "Requested governed investigation","UNASSESSED","REQUESTED_ANALYSIS",List.of(primary),Map.of(),primary.warnings());
        var input=new SupervisorPlanningRequest(context,issue,new CapabilityMatrix(context.tenant(),context.versions().data(),Map.copyOf(capabilities)),"");
        InvestigationPlan plan=supervisor.plan(input, question);
        // Preserve explicit caller filters and family statistic on primary-metric tasks.
        List<InvestigationTask> scoped=plan.tasks().stream().map(t->new InvestigationTask(t.taskId(),t.worker(),t.question(),
                t.requests().stream().map(r->new MetricRequest(r.tenant(),r.metricId(),r.metricId()==request.metricId()?request.measure():r.measure(),
                        r.window(),request.filters(),r.dataVersion())).toList(),t.dependencies())).toList();
        plan=new InvestigationPlan(plan.planId(),plan.issueId(),scoped,plan.requiredEvidence(),plan.stopConditions());
        return plan;
    }
    public InvestigationResult investigatePlan(RunContext context, InvestigationPlan plan) {
        RunGuards.requireAuthorized(context); return investigator.investigate(context, plan);
    }
    public VerificationResult critique(RunContext context, InvestigationResult investigation) {
        RunGuards.requireAuthorized(context); return critic.review(context, investigation);
    }
    public DecisionBrief brief(RunContext context, VerificationResult verification) {
        RunGuards.requireAuthorized(context); return briefing.draft(context, verification);
    }
    /** Explicit typed plans support multi-domain DS-20 and reviewed dynamic compositions. */
    public Result execute(RunContext context,InvestigationPlan plan) {
        RunGuards.requireAuthorized(context);RunGuards.requireTime(context);
        InvestigationResult investigation=investigator.investigate(context,plan);
        VerificationResult verification=critic.review(context,investigation);
        return new Result(plan,investigation,briefing.draft(context,verification));
    }
    public record Result(InvestigationPlan plan,InvestigationResult investigation,DecisionBrief brief){}
}
