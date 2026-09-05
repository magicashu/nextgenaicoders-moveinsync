package com.moveinsync.mobilitycopilot.workflow.application;

import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowCheckpoint;
import org.springframework.stereotype.Service;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.OfficialAnalyticsStore;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorQueryRoute;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorQuestionRouter;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** WS4: add authorized API-to-job/workflow coordination using shared contracts. */
@Service
public final class WorkflowCoordinator {
    private final WorkflowEngine workflowEngine;
    private final AgentWorkflowService agents;
    private final OfficialAnalyticsStore analytics;

    public WorkflowCoordinator(WorkflowEngine workflowEngine, AgentWorkflowService agents,
                               OfficialAnalyticsStore analytics) {
        this.workflowEngine = workflowEngine;
        this.agents = agents;
        this.analytics = analytics;
    }

    public WorkflowCheckpoint start(RunContext context) {
        return workflowEngine.start(context);
    }

    public DecisionBrief createDemoBrief(String businessUnit, LocalDate asOfDate) {
        throw new UnsupportedOperationException("Legacy demo endpoint disabled; use role APIs");
    }

    public com.moveinsync.mobilitycopilot.workflow.domain.InvestigationPlan supervisor(String businessUnit, LocalDate asOfDate, String prompt) {
        var tenant = new TenantContext(businessUnit);
        var route = new SupervisorQuestionRouter().route(prompt);
        if (route.status() != SupervisorQueryRoute.Status.SUPPORTED) throw new IllegalArgumentException(route.message());
        var context = context(tenant, asOfDate, analytics.dataVersion());
        LocalDate end = asOfDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
        var request = new MetricRequest(tenant, route.metric(), MetricRequest.Measure.VALUE,
                new MetricWindow(end.minusDays(6), end), Map.of(), context.versions().data());
        return agents.plan(context, request, prompt);
    }

    public com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult investigator(String businessUnit, LocalDate asOfDate, com.moveinsync.mobilitycopilot.workflow.domain.InvestigationPlan plan) {
        var request = plan.tasks().stream().flatMap(task -> task.requests().stream()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("plan has no metric request"));
        return agents.investigatePlan(context(new TenantContext(businessUnit), asOfDate, request.dataVersion()), plan);
    }

    public VerificationResult critic(String businessUnit, LocalDate asOfDate, com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult investigation) {
        var evidence = investigation.evidence().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("investigation has no evidence"));
        return agents.critique(context(evidence.request().tenant(), asOfDate, evidence.request().dataVersion()), investigation);
    }

    public DecisionBrief briefing(String businessUnit, LocalDate asOfDate, VerificationResult verification) {
        var claim = verification.claims().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("verification has no claims"));
        return agents.brief(context(new TenantContext(businessUnit), asOfDate, claim.dataVersion()), verification);
    }

    private RunContext context(TenantContext tenant, LocalDate asOfDate, String dataVersion) {
        return new RunContext(UUID.randomUUID(), new ActorContext("docker-local", Set.of("TRANSPORT_MANAGER"), Set.of(tenant)),
                tenant, "TRANSPORT_MANAGER", asOfDate,
                new com.moveinsync.mobilitycopilot.workflow.domain.RunVersions(dataVersion, OfficialAnalyticsStore.REGISTRY_VERSION,
                        "agents-v2", "v1", "sarvam", "v1"),
                new com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget(24, 2, 1, Duration.ofSeconds(30), 4),
                Instant.now().plusSeconds(90));
    }
}
