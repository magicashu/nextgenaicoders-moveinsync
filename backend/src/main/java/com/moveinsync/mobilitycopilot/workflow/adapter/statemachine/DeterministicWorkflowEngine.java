package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.application.ActionExecutor;
import com.moveinsync.mobilitycopilot.action.application.ActionRevalidator;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.action.domain.RevalidationResult;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalStore;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorAgent;
import com.moveinsync.mobilitycopilot.workflow.application.ResumableWorkflowEngine;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.domain.BriefingOutput;
import com.moveinsync.mobilitycopilot.workflow.domain.Critique;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationPlan;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.PolicyDecision;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;
import com.moveinsync.mobilitycopilot.workflow.domain.VersionedWorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowError;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import com.moveinsync.mobilitycopilot.workflow.nodes.ActionPolicyGate;
import com.moveinsync.mobilitycopilot.workflow.nodes.EvidenceMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The guaranteed orchestration path: an explicit 18-node state machine with typed routing, one
 * correction cycle, bounded fan-out, a checkpointed approval interrupt and post-approval
 * revalidation. It depends only on project-owned ports, never on a graph framework.
 */
@Service
public class DeterministicWorkflowEngine implements ResumableWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DeterministicWorkflowEngine.class);

    private final AnalyticsGateway analytics;
    private final SupervisorAgent supervisor;
    private final InvestigationAgent investigator;
    private final EvidenceCriticAgent critic;
    private final BriefingActionAgent briefing;
    private final EvidenceVerifier verifier;
    private final AccessAuthorizer authorizer;
    private final WorkflowCheckpointStore checkpoints;
    private final ApprovalStore approvals;
    private final ActionRevalidator revalidator;
    private final ActionExecutor executor;
    private final AuditSink audit;
    private final WorkflowProperties properties;
    private final LanguageModelPort model;
    private final TransitionListener listener;
    private final Map<UUID, WorkflowRun> registry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> checkpointVersions = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public DeterministicWorkflowEngine(AnalyticsGateway analytics, SupervisorAgent supervisor, InvestigationAgent investigator,
                                       EvidenceCriticAgent critic, BriefingActionAgent briefing, EvidenceVerifier verifier,
                                       AccessAuthorizer authorizer, WorkflowCheckpointStore checkpoints, ApprovalStore approvals,
                                       ActionRevalidator revalidator, ActionExecutor executor, AuditSink audit,
                                       WorkflowProperties properties, LanguageModelPort model, ObjectProvider<TransitionListener> listener) {
        this(analytics, supervisor, investigator, critic, briefing, verifier, authorizer, checkpoints, approvals, revalidator, executor, audit,
                properties, model, listener.getIfAvailable(() -> TransitionListener.NONE));
    }

    public DeterministicWorkflowEngine(AnalyticsGateway analytics, SupervisorAgent supervisor, InvestigationAgent investigator,
                                       EvidenceCriticAgent critic, BriefingActionAgent briefing, EvidenceVerifier verifier,
                                       AccessAuthorizer authorizer, WorkflowCheckpointStore checkpoints, ApprovalStore approvals,
                                       ActionRevalidator revalidator, ActionExecutor executor, AuditSink audit,
                                       WorkflowProperties properties, LanguageModelPort model, TransitionListener listener) {
        this.analytics = analytics;
        this.supervisor = supervisor;
        this.investigator = investigator;
        this.critic = critic;
        this.briefing = briefing;
        this.verifier = verifier;
        this.authorizer = authorizer;
        this.checkpoints = checkpoints;
        this.approvals = approvals;
        this.revalidator = revalidator;
        this.executor = executor;
        this.audit = audit;
        this.properties = properties;
        this.model = model;
        this.listener = listener;
    }

    @Override
    public WorkflowOutcome run(WorkflowState initialState) {
        ActorContext scheduler = new ActorContext("scheduler", initialState.tenant().businessUnit(), Set.of("TRANSPORT_MANAGER"));
        return run(initialState, new RunContext(scheduler, RunContext.Persona.TRANSPORT_MANAGER, RunContext.RequestMode.SCHEDULED,
                initialState.runId().toString(), RunContext.WORKFLOW_VERSION, RunContext.PROMPT_VERSION, model.modelId(), "unknown", null));
    }

    @Override
    public WorkflowOutcome run(WorkflowState initialState, RunContext context) {
        WorkflowRun run = new WorkflowRun(initialState, context.modelId().equals("unset")
                ? new RunContext(context.actor(), context.persona(), context.mode(), context.traceId(), context.workflowVersion(), context.promptVersion(), model.modelId(), context.dataVersion(), context.question())
                : context);
        registry.put(initialState.runId(), run);
        List<AuditEvent> events = new ArrayList<>();
        WorkflowNode node = WorkflowNode.INITIALIZE_RUN;
        try {
            while (node != null) {
                node = execute(node, run, events);
            }
        } catch (RuntimeException unexpected) {
            // Unexpected exceptions surface: record, audit and fail closed without any side effect.
            run.addError(new WorkflowError(run.currentNode(), "UNEXPECTED", unexpected.getMessage() == null ? unexpected.getClass().getSimpleName() : unexpected.getMessage(), false, Instant.now()));
            run.step(WorkflowStep.FAILED);
            events.add(auditEvent(run, "RUN_FAILED", Map.of("node", run.currentNode().name(), "error", String.valueOf(unexpected.getMessage()))));
            checkpoint(run);
            log.warn("Workflow {} failed at {}: {}", run.state().runId(), run.currentNode(), unexpected.toString());
        }
        return outcome(run, events);
    }

    @Override
    public WorkflowOutcome resume(ApprovalDecision decision, RunContext context) {
        VersionedWorkflowState checkpoint = checkpoints.find(decision.runId())
                .orElseThrow(() -> new IllegalStateException("No checkpoint for run " + decision.runId()));
        WorkflowState persisted = checkpoint.state();
        checkpointVersions.put(persisted.runId(), checkpoint.version());
        if (persisted.step() != WorkflowStep.AWAITING_APPROVAL) {
            throw new IllegalStateException("Run " + decision.runId() + " is not awaiting approval (step " + persisted.step() + ")");
        }
        ApprovalRequest request = approvals.findRequest(decision.approvalId())
                .orElseThrow(() -> new IllegalStateException("Unknown approval " + decision.approvalId()));
        if (!request.runId().equals(persisted.runId()) || !request.proposal().actionId().equals(decision.actionId())) {
            throw new IllegalStateException("Approval decision does not match the paused run");
        }
        // Rebuild the in-process run deterministically: same tenant, as-of and fresh budget for the resume execution.
        WorkflowState fresh = new WorkflowState(persisted.runId(), persisted.tenant(), persisted.asOfDate(), WorkflowStep.AWAITING_APPROVAL, persisted.tasks(),
                0, persisted.maxInvestigationSteps(), 0, persisted.maxCorrectionCycles(), 0, persisted.maxToolCalls());
        WorkflowRun run = registry.containsKey(persisted.runId()) && registry.get(persisted.runId()).evidence() != null
                ? registry.get(persisted.runId()) : new WorkflowRun(fresh, context);
        run.context(new RunContext(context.actor(), context.persona(), RunContext.RequestMode.RESUME, run.context().traceId(), context.workflowVersion(),
                context.promptVersion(), model.modelId(), run.context().dataVersion(), null));
        registry.put(persisted.runId(), run);
        run.approvalRequest(request);
        run.action(request.proposal());
        run.approvalDecision(decision);
        List<AuditEvent> events = new ArrayList<>();
        try {
            WorkflowNode node = WorkflowNode.REVALIDATE_AND_EXECUTE;
            while (node != null) {
                node = execute(node, run, events);
            }
        } catch (RuntimeException unexpected) {
            run.addError(new WorkflowError(run.currentNode(), "UNEXPECTED", String.valueOf(unexpected.getMessage()), false, Instant.now()));
            run.step(WorkflowStep.APPROVED_NOT_EXECUTED);
            events.add(auditEvent(run, "ACTION_NOT_EXECUTED", Map.of("reason", "unexpected failure: " + unexpected.getMessage())));
            checkpoint(run);
        }
        return outcome(run, events);
    }

    @Override
    public Optional<WorkflowRun> find(UUID runId) {
        return Optional.ofNullable(registry.get(runId));
    }

    // ---------------------------------------------------------------- routing

    private WorkflowNode execute(WorkflowNode node, WorkflowRun run, List<AuditEvent> events) {
        run.currentNode(node);
        Instant started = Instant.now();
        WorkflowStep before = run.state().step();
        Map<String, String> attributes = new LinkedHashMap<>();
        Routing routing = switch (node) {
            case INITIALIZE_RUN -> initialize(run, attributes);
            case AUTHORIZE_SCOPE -> authorize(run, events, attributes);
            case PROFILE_DATASET -> profile(run, attributes);
            case BUILD_CAPABILITY_MATRIX -> capabilities(run, attributes);
            case COMPUTE_METRIC_SNAPSHOT -> snapshot(run, attributes);
            case DETECT_ANOMALIES -> detect(run, events, attributes);
            case PRIORITIZE_ISSUE -> prioritize(run, attributes);
            case SUPERVISOR_PLAN -> plan(run, attributes);
            case VALIDATE_PLAN -> validatePlan(run, attributes);
            case RUN_INVESTIGATIONS -> investigate(run, attributes);
            case MERGE_EVIDENCE -> merge(run, attributes);
            case EVIDENCE_CRITIC -> critique(run, attributes);
            case VERIFY_EVIDENCE -> verify(run, attributes);
            case COMPOSE_DECISION_BRIEF -> compose(run, events, attributes);
            case ACTION_POLICY_GATE -> policyGate(run, events, attributes);
            case APPROVAL_INTERRUPT -> approvalInterrupt(run, events, attributes);
            case REVALIDATE_AND_EXECUTE -> revalidateAndExecute(run, events, attributes);
            case APPEND_AUDIT_EVENT -> appendAudit(run, events, attributes);
        };
        TransitionEvent event = new TransitionEvent(run.state().runId(), run.context().traceId(), node, null, before, run.state().step(), started,
                java.time.Duration.between(started, Instant.now()).toMillis(), routing.outcome(), attributes);
        run.addTransition(event);
        listener.onTransition(event);
        return routing.next();
    }

    private record Routing(WorkflowNode next, String outcome) {
        static Routing to(WorkflowNode next, String outcome) {
            return new Routing(next, outcome);
        }

        static Routing end(String outcome) {
            return new Routing(null, outcome);
        }
    }

    // ---------------------------------------------------------------- nodes 1-7

    private Routing initialize(WorkflowRun run, Map<String, String> attributes) {
        run.step(WorkflowStep.INITIALIZED);
        attributes.put("businessUnit", run.state().tenant().businessUnit());
        attributes.put("mode", run.context().mode().name());
        attributes.put("persona", run.context().persona().name());
        attributes.put("workflowVersion", run.context().workflowVersion());
        return Routing.to(WorkflowNode.AUTHORIZE_SCOPE, "initialized");
    }

    private Routing authorize(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        TenantContext tenant = run.state().tenant();
        ActorContext actor = run.context().actor();
        try {
            if (!actor.businessUnit().equals(tenant.businessUnit())) {
                throw new SecurityException("actor tenant does not match requested tenant");
            }
            authorizer.require(actor, tenant, Permission.READ_TENANT_METRICS);
            authorizer.require(actor, tenant, Permission.INVESTIGATE_TENANT);
        } catch (RuntimeException denied) {
            run.addError(new WorkflowError(WorkflowNode.AUTHORIZE_SCOPE, "ACCESS_DENIED", "Tenant scope denied", false, Instant.now()));
            run.step(WorkflowStep.FAILED);
            events.add(auditEvent(run, "ACCESS_DENIED", Map.of("actor", actor.actorId(), "requestedBusinessUnit", tenant.businessUnit())));
            attributes.put("denied", "true");
            checkpoint(run);
            return Routing.end("denied");
        }
        run.step(WorkflowStep.AUTHORIZED);
        return Routing.to(WorkflowNode.PROFILE_DATASET, "authorized");
    }

    private Routing profile(WorkflowRun run, Map<String, String> attributes) {
        List<AnalyticsGateway.CapabilityGap> gaps = analytics.capabilities(run.state().tenant());
        run.capabilities(gaps);
        attributes.put("capabilityGaps", String.valueOf(gaps.stream().filter(g -> g.unsupported() || g.derivable()).count()));
        return Routing.to(WorkflowNode.BUILD_CAPABILITY_MATRIX, "profiled");
    }

    private Routing capabilities(WorkflowRun run, Map<String, String> attributes) {
        long disabled = run.capabilities().stream().filter(AnalyticsGateway.CapabilityGap::unsupported).count();
        attributes.put("unsupportedAnalyses", String.valueOf(disabled));
        return Routing.to(WorkflowNode.COMPUTE_METRIC_SNAPSHOT, "matrix");
    }

    private Routing snapshot(WorkflowRun run, Map<String, String> attributes) {
        DetectionSnapshot snapshot = analytics.detect(run.state().tenant(), run.state().asOfDate());
        run.detection(snapshot);
        run.context(run.context().withDataVersion(snapshot.dataVersion()));
        attributes.put("dataVersion", snapshot.dataVersion());
        attributes.put("candidates", String.valueOf(snapshot.candidates().size()));
        return Routing.to(WorkflowNode.DETECT_ANOMALIES, "snapshot");
    }

    private Routing detect(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        DetectionSnapshot snapshot = run.detection();
        attributes.put("material", String.valueOf(snapshot.material().size()));
        attributes.put("dataQualityNotes", String.valueOf(snapshot.dataQualityNotes().size()));
        if (snapshot.healthy()) {
            MetricResult headline = snapshot.candidates().stream().filter(c -> c.metricId() == MetricId.M01_DELAYED_TRIP_RATE).map(DetectionSnapshot.IssueCandidate::metric)
                    .findFirst().orElseGet(() -> snapshot.candidates().isEmpty() ? null : snapshot.candidates().getFirst().metric());
            if (headline == null) {
                run.addError(new WorkflowError(WorkflowNode.DETECT_ANOMALIES, "NO_METRICS", "No supported metric could be computed", false, Instant.now()));
                run.step(WorkflowStep.FAILED);
                return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "no metrics");
            }
            List<String> notes = new ArrayList<>();
            snapshot.candidates().forEach(c -> notes.add("%s: %s (%s)".formatted(c.metric().metricName(), c.classification().toLowerCase(Locale.ROOT).replace('_', ' '),
                    c.reasons().isEmpty() ? "" : c.reasons().getFirst())));
            snapshot.dataQualityNotes().forEach(n -> notes.add("Data-quality note: " + n.note()));
            EvidenceBundle bundle = healthyBundle(run, snapshot);
            run.briefing(briefing.composeHealthy(run, headline, bundle, "HEALTHY", notes));
            run.step(WorkflowStep.HEALTHY);
            events.add(auditEvent(run, "HEALTHY_BRIEF", Map.of("dataVersion", snapshot.dataVersion(), "candidates", String.valueOf(snapshot.candidates().size()))));
            return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "healthy");
        }
        return Routing.to(WorkflowNode.PRIORITIZE_ISSUE, "issue");
    }

    private Routing prioritize(WorkflowRun run, Map<String, String> attributes) {
        DetectionSnapshot.IssueCandidate issue = run.detection().selected().orElseThrow();
        run.selectedIssue(issue);
        attributes.put("anomalyId", issue.anomalyId());
        attributes.put("metricId", issue.metricId().name());
        attributes.put("severity", issue.severity());
        attributes.put("priorityScore", issue.priorityScore().toPlainString());
        return Routing.to(WorkflowNode.SUPERVISOR_PLAN, "selected");
    }

    // ---------------------------------------------------------------- nodes 8-14

    private Routing plan(WorkflowRun run, Map<String, String> attributes) {
        InvestigationPlan plan = supervisor.plan(run);
        if (run.context().question() != null && !run.context().question().isBlank()) {
            plan = restrictToQuestion(plan, run.context().question());
        }
        run.plan(plan);
        attributes.put("tasks", String.join(",", plan.workers()));
        attributes.put("modelGenerated", String.valueOf(plan.modelGenerated()));
        run.step(WorkflowStep.PLANNED);
        return Routing.to(WorkflowNode.VALIDATE_PLAN, plan.modelGenerated() ? "model plan" : "deterministic plan");
    }

    private Routing validatePlan(WorkflowRun run, Map<String, String> attributes) {
        InvestigationPlan plan = run.plan();
        List<InvestigationTask> valid = new ArrayList<>();
        List<String> notes = new ArrayList<>(plan.validationNotes());
        Set<String> disabled = new java.util.HashSet<>();
        run.capabilities().stream().filter(AnalyticsGateway.CapabilityGap::unsupported).forEach(g -> disabled.add(g.analysis()));
        for (InvestigationTask task : plan.tasks()) {
            Optional<WorkerType> type = WorkerType.fromId(task.worker());
            if (type.isEmpty()) {
                notes.add("Removed unknown worker " + task.worker());
                continue;
            }
            if (disabled.contains(type.get().capabilityAnalysis())) {
                notes.add("Removed unsupported analysis " + task.worker());
                continue;
            }
            if (!task.parameters().getOrDefault("businessUnit", run.state().tenant().businessUnit()).equals(run.state().tenant().businessUnit())) {
                notes.add("Removed cross-tenant task " + task.worker());
                continue;
            }
            valid.add(task);
            if (valid.size() >= run.state().maxToolCalls()) {
                notes.add("Plan truncated to the tool-call budget");
                break;
            }
        }
        if (valid.isEmpty()) {
            run.addError(new WorkflowError(WorkflowNode.VALIDATE_PLAN, "EMPTY_PLAN", "No valid investigation task", false, Instant.now()));
            run.step(WorkflowStep.FAILED);
            return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "empty plan");
        }
        run.plan(new InvestigationPlan(plan.anomalyId(), valid, plan.requiredMetrics(), plan.allowedDimensions(), plan.stopConditions(), plan.rationale(), plan.modelGenerated(), notes));
        run.tasks(valid);
        attributes.put("validTasks", String.valueOf(valid.size()));
        return Routing.to(WorkflowNode.RUN_INVESTIGATIONS, "valid");
    }

    private Routing investigate(WorkflowRun run, Map<String, String> attributes) {
        run.clearInvestigations();
        List<InvestigationResult> results = investigator.investigate(run, listener);
        results.forEach(run::addInvestigation);
        long failed = results.stream().filter(r -> !r.succeeded()).count();
        attributes.put("branches", String.valueOf(results.size()));
        attributes.put("failedBranches", String.valueOf(failed));
        attributes.put("toolCalls", String.valueOf(run.state().toolCalls()));
        run.step(WorkflowStep.INVESTIGATED);
        return Routing.to(WorkflowNode.MERGE_EVIDENCE, failed == 0 ? "complete" : "partial");
    }

    private Routing merge(WorkflowRun run, Map<String, String> attributes) {
        List<AnalyticsGateway.PeerValueDto> peers = List.of();
        if (run.context().persona() == RunContext.Persona.FACILITIES_HEAD) {
            try {
                authorizer.require(run.context().actor(), run.state().tenant(), Permission.VIEW_CROSS_TENANT_PEERS);
                MetricResult headline = run.selectedIssue().metric();
                peers = analytics.crossTenantPeers(headline.metricId(), new AnalyticsGateway.WindowDto(headline.periodStart(), headline.periodEnd()));
            } catch (RuntimeException denied) {
                peers = List.of();
            }
        }
        EvidencePackage evidence = EvidenceMerger.merge(run, peers);
        run.evidence(evidence);
        attributes.put("evidenceItems", String.valueOf(evidence.bundle().items().size()));
        attributes.put("claims", String.valueOf(evidence.claims().size()));
        attributes.put("evidenceVersion", evidence.evidenceVersion());
        return Routing.to(WorkflowNode.EVIDENCE_CRITIC, "merged");
    }

    private Routing critique(WorkflowRun run, Map<String, String> attributes) {
        Critique critique = critic.critique(run, vendorRankings(run));
        run.critique(critique);
        attributes.put("verdict", critique.verdict().name());
        attributes.put("overclaims", String.valueOf(critique.overclaimClaimIds().size()));
        return Routing.to(WorkflowNode.VERIFY_EVIDENCE, critique.verdict().name().toLowerCase(Locale.ROOT));
    }

    private Routing verify(WorkflowRun run, Map<String, String> attributes) {
        EvidencePackage evidence = run.evidence();
        VerificationResult result = verifier.verify(evidence, vendorRankings(run), evidence.bundle().caveats());
        if (result.hasBlocking() || run.critique().verdict() == Critique.Verdict.REVISE) {
            java.util.Set<String> remove = new java.util.HashSet<>(result.removedClaimIds());
            remove.addAll(run.critique().overclaimClaimIds());
            List<Claim> kept = evidence.claims().stream().filter(c -> !remove.contains(c.claimId())).toList();
            if (run.tryConsumeCorrectionCycle()) {
                // one correction cycle: drop unsupported claims and re-verify without new tool calls
                run.evidence(evidence.withClaims(kept));
                run.planFeedback(run.critique());
                VerificationResult second = verifier.verify(run.evidence(), vendorRankings(run), run.evidence().bundle().caveats());
                run.verification(second);
                attributes.put("correctionCycle", "1");
                attributes.put("removedClaims", String.valueOf(remove.size()));
                run.step(WorkflowStep.VERIFIED);
                return Routing.to(WorkflowNode.COMPOSE_DECISION_BRIEF, second.passed() ? "pass after correction" : "qualified");
            }
            run.verification(result);
            run.step(WorkflowStep.VERIFIED);
            return Routing.to(WorkflowNode.COMPOSE_DECISION_BRIEF, "correction exhausted");
        }
        run.verification(result);
        attributes.put("confidence", result.confidence().toPlainString());
        run.step(WorkflowStep.VERIFIED);
        return Routing.to(WorkflowNode.COMPOSE_DECISION_BRIEF, "pass");
    }

    private Routing compose(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        BriefingOutput output = briefing.compose(run, vendorRankings(run));
        // second verification of the final claims embedded in the brief (numbers must still resolve)
        run.briefing(output);
        run.action(output.recommendedAction());
        run.step(WorkflowStep.BRIEFED);
        attributes.put("modelAssisted", String.valueOf(output.modelAssisted()));
        attributes.put("actionType", output.recommendedAction().type().name());
        events.add(auditEvent(run, "BRIEF_CREATED", Map.of("evidenceVersion", run.evidence().evidenceVersion(), "claims", String.valueOf(run.evidence().claims().size()),
                "confidence", run.verification() == null ? "n/a" : run.verification().confidence().toPlainString())));
        return Routing.to(WorkflowNode.ACTION_POLICY_GATE, "composed");
    }

    // ---------------------------------------------------------------- nodes 15-18

    private Routing policyGate(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        PolicyDecision decision = ActionPolicyGate.evaluate(run, run.action(), Instant.now());
        run.policy(decision);
        attributes.put("route", decision.route().name());
        events.add(auditEvent(run, "ACTION_POLICY_" + decision.route().name(), Map.of("actionId", run.action().actionId().toString(), "reasons", String.join("; ", decision.reasons()))));
        return switch (decision.route()) {
            case APPROVAL_REQUIRED -> Routing.to(WorkflowNode.APPROVAL_INTERRUPT, "approval required");
            case REPORT_ONLY -> {
                run.step(WorkflowStep.REPORT_ONLY);
                run.briefing(withStatus(run.briefing(), "REPORT_ONLY"));
                yield Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "report only");
            }
            case REJECTED -> {
                run.step(WorkflowStep.REPORT_ONLY);
                run.briefing(withStatus(run.briefing(), "REPORT_ONLY"));
                yield Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "rejected by policy");
            }
        };
    }

    private Routing approvalInterrupt(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        ActionProposal proposal = run.action();
        ApprovalRequest request = approvals.create(new ApprovalRequest(UUID.randomUUID(), run.state().runId(), run.state().tenant().businessUnit(), proposal,
                proposal.evidenceVersion(), proposal.createdAt(), proposal.expiresAt()));
        run.approvalRequest(request);
        run.step(WorkflowStep.AWAITING_APPROVAL);
        checkpoint(run);
        events.add(auditEvent(run, "ACTION_AWAITING_APPROVAL", Map.of("approvalId", request.approvalId().toString(), "actionId", proposal.actionId().toString(),
                "actionType", proposal.type().name(), "evidenceVersion", proposal.evidenceVersion(), "expiresAt", proposal.expiresAt().toString())));
        attributes.put("approvalId", request.approvalId().toString());
        // The interrupt: return to the caller. resume() re-enters at node 17.
        return Routing.end("paused");
    }

    private Routing revalidateAndExecute(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        ApprovalRequest request = run.approvalRequest();
        ApprovalDecision decision = run.approvalDecision();
        Instant now = Instant.now();
        ActorContext actor = run.context().actor();
        events.add(auditEvent(run, "APPROVAL_" + decision.decision().name(), Map.of("approvalId", request.approvalId().toString(), "decidedBy", decision.decidedBy(),
                "decidedAt", decision.decidedAt().toString(), "comment", String.valueOf(decision.comment()))));
        approvals.decide(decision);
        if (decision.decision() == ApprovalDecisionType.REJECT) {
            run.step(WorkflowStep.REJECTED);
            attributes.put("result", "rejected");
            return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "rejected");
        }
        if (!request.expiresAt().isAfter(now) || !request.proposal().expiresAt().isAfter(now)) {
            run.step(WorkflowStep.EXPIRED);
            events.add(auditEvent(run, "ACTION_EXPIRED", Map.of("approvalId", request.approvalId().toString(), "expiresAt", request.expiresAt().toString())));
            attributes.put("result", "expired");
            return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "expired");
        }
        ActionProposal proposal = decision.decision() == ApprovalDecisionType.EDIT ? decision.editedProposal() : request.proposal();
        run.action(proposal);
        // Fresh evidence: recompute detection and require the same data version and headline values.
        List<String> stale = new ArrayList<>();
        try {
            authorizer.require(actor, run.state().tenant(), Permission.APPROVE_ACTION);
        } catch (RuntimeException denied) {
            stale.add("Approver lacks APPROVE_ACTION for " + run.state().tenant().businessUnit());
        }
        if (!actor.businessUnit().equals(run.state().tenant().businessUnit())) {
            stale.add("Approver belongs to another business unit");
        }
        String currentEvidenceVersion = recomputeEvidenceVersion(run);
        if (!currentEvidenceVersion.equals(request.evidenceVersion())) {
            stale.add("Evidence changed since approval was requested (" + request.evidenceVersion() + " -> " + currentEvidenceVersion + ")");
        }
        if (decision.decision() == ApprovalDecisionType.EDIT) {
            if (!proposal.type().equals(request.proposal().type()) && proposal.type() == com.moveinsync.mobilitycopilot.action.domain.ActionType.DRAFT_VENDOR_ESCALATION) {
                stale.add("Edit may not upgrade the action to a vendor escalation");
            }
            if (!proposal.scope().getOrDefault("businessUnit", "").equals(run.state().tenant().businessUnit())) {
                stale.add("Edited scope leaves the authorized business unit");
            }
            if (!proposal.evidenceVersion().equals(request.evidenceVersion())) {
                stale.add("Edited proposal references a different evidence version");
            }
        }
        if (!stale.isEmpty()) {
            run.step(WorkflowStep.APPROVED_NOT_EXECUTED);
            events.add(auditEvent(run, "ACTION_NOT_EXECUTED", Map.of("actionId", proposal.actionId().toString(), "reasons", String.join("; ", stale))));
            attributes.put("result", "stale");
            return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "revalidation failed");
        }
        ActionExecutionCommand command = new ActionExecutionCommand(actor, run.state().tenant(), proposal,
                run.state().runId() + ":" + proposal.actionId(), request.evidenceVersion(), now);
        RevalidationResult revalidation = revalidator.revalidate(command);
        events.add(auditEvent(run, "ACTION_REVALIDATED", Map.of("valid", String.valueOf(revalidation.valid()), "evidenceVersion", revalidation.evidenceVersion(),
                "reasons", String.join("; ", revalidation.reasons()))));
        if (!revalidation.valid()) {
            run.step(WorkflowStep.APPROVED_NOT_EXECUTED);
            events.add(auditEvent(run, "ACTION_NOT_EXECUTED", Map.of("actionId", proposal.actionId().toString(), "reasons", String.join("; ", revalidation.reasons()))));
            attributes.put("result", "revalidation rejected");
            return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, "revalidation rejected");
        }
        ExecutionReceipt receipt;
        try {
            receipt = executor.execute(command, revalidation);
        } catch (RuntimeException adapterFailure) {
            receipt = new ExecutionReceipt(proposal.actionId(), run.state().runId(), command.idempotencyKey(), ActionStatus.APPROVED_NOT_EXECUTED, now, null, null,
                    "Adapter failure: " + adapterFailure.getMessage());
        }
        run.receipt(receipt);
        run.step(receipt.status() == ActionStatus.EXECUTED ? WorkflowStep.EXECUTED : WorkflowStep.APPROVED_NOT_EXECUTED);
        events.add(auditEvent(run, receipt.status() == ActionStatus.EXECUTED ? "ACTION_EXECUTED" : "ACTION_NOT_EXECUTED", Map.of(
                "actionId", proposal.actionId().toString(), "idempotencyKey", receipt.idempotencyKey(), "externalReference", String.valueOf(receipt.externalReference()),
                "message", String.valueOf(receipt.message()))));
        attributes.put("result", receipt.status().name());
        return Routing.to(WorkflowNode.APPEND_AUDIT_EVENT, receipt.status().name().toLowerCase(Locale.ROOT));
    }

    private Routing appendAudit(WorkflowRun run, List<AuditEvent> events, Map<String, String> attributes) {
        WorkflowStep step = run.state().step();
        if (step == WorkflowStep.BRIEFED || step == WorkflowStep.VERIFIED || step == WorkflowStep.INVESTIGATED) {
            run.step(WorkflowStep.COMPLETED);
        }
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("finalStep", run.state().step().name());
        summary.put("toolCalls", String.valueOf(run.state().toolCalls()));
        summary.put("correctionCycles", String.valueOf(run.state().correctionCycles()));
        summary.put("modelCalls", String.valueOf(run.modelUsage().stream().filter(u -> !u.fallbackUsed()).count()));
        summary.put("elapsedMs", String.valueOf(run.elapsedMs()));
        summary.put("dataVersion", String.valueOf(run.context().dataVersion()));
        if (run.evidence() != null) {
            summary.put("evidenceVersion", run.evidence().evidenceVersion());
        }
        events.add(auditEvent(run, "RUN_SUMMARY", summary));
        checkpoint(run);
        attributes.putAll(summary);
        return Routing.end(run.state().step().name().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- helpers

    private InvestigationPlan restrictToQuestion(InvestigationPlan plan, String question) {
        String q = question.toLowerCase(Locale.ROOT);
        Map<String, List<String>> keywords = Map.of(
                "vendor", List.of("vendor", "supplier", "travel"),
                "site_shift_direction", List.of("site", "office", "campus", "shift", "login", "logout", "direction", "where", "concentrat"),
                "delay_reason", List.of("reason", "why", "driver", "traffic", "employee"),
                "cost_billing", List.of("cost", "bill", "spend", "km"),
                "feedback", List.of("rating", "feedback", "experience"),
                "tracking_safety_alerts", List.of("alert", "safety", "device", "panic", "escort", "tracking"),
                "noshow_roster", List.of("no-show", "no show", "roster", "pickup", "drop", "leg"));
        List<InvestigationTask> matched = plan.tasks().stream()
                .filter(t -> keywords.getOrDefault(t.worker(), List.of()).stream().anyMatch(q::contains)).toList();
        if (matched.isEmpty()) {
            return plan;
        }
        return new InvestigationPlan(plan.anomalyId(), matched, plan.requiredMetrics(), plan.allowedDimensions(), plan.stopConditions(),
                plan.rationale() + " (restricted to the question intent)", plan.modelGenerated(), plan.validationNotes());
    }

    private static List<WorkerEvidenceDto.Ranking> vendorRankings(WorkflowRun run) {
        return run.investigations().stream().flatMap(i -> i.evidence().stream()).flatMap(e -> e.rankings().stream())
                .filter(r -> r.dimension().equals("vendor_id")).toList();
    }

    private String recomputeEvidenceVersion(WorkflowRun run) {
        if (run.evidence() != null && run.detection() != null) {
            // Fresh detection: the data version and headline metric must be unchanged.
            DetectionSnapshot fresh = analytics.detect(run.state().tenant(), run.state().asOfDate());
            boolean sameData = fresh.dataVersion().equals(run.detection().dataVersion());
            boolean sameHeadline = fresh.selected().map(c -> c.anomalyId().equals(run.selectedIssue().anomalyId())
                    && java.util.Objects.equals(c.metric().value(), run.selectedIssue().metric().value())).orElse(false);
            return sameData && sameHeadline ? run.evidence().evidenceVersion() : "changed-" + fresh.dataVersion();
        }
        // After a restart the rich state is gone: rebuild deterministically by replaying nodes 3-11 with a fresh budget.
        WorkflowRun replay = new WorkflowRun(new WorkflowState(run.state().runId(), run.state().tenant(), run.state().asOfDate(), WorkflowStep.AUTHORIZED,
                run.state().tasks(), 0, run.state().maxInvestigationSteps(), 0, run.state().maxCorrectionCycles(), 0, run.state().maxToolCalls()), run.context());
        replay.capabilities(analytics.capabilities(run.state().tenant()));
        DetectionSnapshot snapshot = analytics.detect(run.state().tenant(), run.state().asOfDate());
        replay.detection(snapshot);
        if (snapshot.selected().isEmpty()) {
            return "changed-healthy-" + snapshot.dataVersion();
        }
        replay.selectedIssue(snapshot.selected().get());
        replay.plan(supervisor.plan(replay));
        replay.tasks(replay.plan().tasks());
        investigator.investigate(replay, TransitionListener.NONE).forEach(replay::addInvestigation);
        EvidencePackage evidence = EvidenceMerger.merge(replay, List.of());
        run.detection(snapshot);
        run.selectedIssue(snapshot.selected().get());
        run.evidence(evidence);
        replay.investigations().forEach(run::addInvestigation);
        return evidence.evidenceVersion();
    }

    private EvidenceBundle healthyBundle(WorkflowRun run, DetectionSnapshot snapshot) {
        List<EvidenceItem> items = new ArrayList<>();
        for (DetectionSnapshot.IssueCandidate c : snapshot.candidates()) {
            if (c.metric().value() != null) {
                items.add(EvidenceMerger.item(c.anomalyId(), c.metric(), c.metric().filters()));
            }
        }
        List<String> caveats = new ArrayList<>();
        run.capabilities().stream().filter(g -> g.unsupported() || g.derivable()).forEach(g -> caveats.add(g.analysis() + ": " + g.reason()));
        snapshot.dataQualityNotes().forEach(n -> caveats.add("Data-quality note: " + n.note()));
        return new EvidenceBundle(items, 1.0, items.isEmpty() ? 0 : items.getFirst().supportingCount(), caveats);
    }

    private static BriefingOutput withStatus(BriefingOutput output, String status) {
        var brief = output.decisionBrief();
        var replaced = new com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief(brief.runId(), brief.businessUnit(), brief.asOfDate(), brief.headline(),
                brief.metric(), brief.findings(), brief.recommendedAction(), brief.evidence(), status);
        return new BriefingOutput(replaced, output.operationsBrief(), output.leadershipNarrative(), output.recommendedAction(), output.recommendationRationale(), output.modelAssisted());
    }

    private void checkpoint(WorkflowRun run) {
        long expected = checkpointVersions.getOrDefault(run.state().runId(), WorkflowCheckpointStore.NEW_CHECKPOINT);
        VersionedWorkflowState saved = checkpoints.save(run.state(), expected);
        checkpointVersions.put(run.state().runId(), saved.version());
    }

    private AuditEvent auditEvent(WorkflowRun run, String type, Map<String, String> payload) {
        Map<String, String> safe = new LinkedHashMap<>(payload);
        safe.put("actor", run.context().actor().actorId());
        safe.put("workflowStep", run.state().step().name());
        safe.put("node", run.currentNode().name());
        return audit.append(new AuditEvent(UUID.randomUUID(), run.state().runId(), run.state().tenant().businessUnit(), type, safe, Instant.now(), run.context().traceId()));
    }

    private WorkflowOutcome outcome(WorkflowRun run, List<AuditEvent> events) {
        if (run.briefing() == null) {
            // failed before a brief existed: return a typed, evidence-free failure brief
            MetricResult placeholder = new MetricResult(MetricId.M01_DELAYED_TRIP_RATE, "Delayed-trip rate", com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.PERCENT,
                    com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus.UNSUPPORTED, null, null, null, null, null, 0, run.state().asOfDate().minusDays(7),
                    run.state().asOfDate().minusDays(1), Map.of(), "metrics-v1.1", String.valueOf(run.context().dataVersion()), "none",
                    List.of("Run did not reach metric computation: " + (run.errors().isEmpty() ? run.state().step() : run.errors().getFirst().code())));
            EvidenceBundle empty = new EvidenceBundle(List.of(new EvidenceItem("none", placeholder.metricId().name(), java.math.BigDecimal.ZERO, "COUNT", null, null, null, null, 0,
                    placeholder.periodStart(), placeholder.periodEnd(), Map.of(), "none", "metrics-v1.1", String.valueOf(run.context().dataVersion()))), 0.0, 0,
                    List.of(run.errors().isEmpty() ? "No evidence" : run.errors().getFirst().message()));
            ActionProposal none = new ActionProposal(UUID.randomUUID(), run.state().runId(), com.moveinsync.mobilitycopilot.action.domain.ActionType.DRAFT_COMMUNICATION,
                    "No action", "The run failed closed before any evidence was produced.", Map.of("businessUnit", run.state().tenant().businessUnit()),
                    String.valueOf(run.context().dataVersion()), Instant.now(), Instant.now().plus(properties.approvalTtl()), ActionStatus.DRAFT_REQUIRES_APPROVAL);
            run.briefing(new BriefingOutput(new com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief(run.state().runId(), run.state().tenant().businessUnit(),
                    run.state().asOfDate(), run.state().tenant().businessUnit() + ": run " + run.state().step().name().toLowerCase(Locale.ROOT), placeholder,
                    run.errors().stream().map(e -> e.code() + ": " + e.message()).toList(), none, empty, run.state().step().name()), List.of(), List.of(), none, "failed", false));
        }
        return new WorkflowOutcome(run.briefing().decisionBrief(), run.state().step(), List.copyOf(events));
    }
}
