package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyIssue;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier;
import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetProfile;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.agents.BriefingActionAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorAgent;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowEngine;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.EvidenceMerger;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.PlanValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 18-node workflow engine.
 *
 * Implementation status:
 *   Nodes 1–2  : minimal validation + WS3 auth stub
 *   Nodes 3–7  : WS1 stubs — typed handoff contracts defined, throw UnsupportedOperationException
 *   Nodes 8–11 : IMPLEMENTED — Supervisor, plan validation, Agent 2 investigation, evidence merge
 *   Nodes 12–13: IMPLEMENTED — EvidenceCriticAgent + EvidenceVerifier
 *   Node  14   : WS4 stub — BriefingActionAgent
 *   Nodes 15–18: WS3/WS4 stubs — policy, approval, revalidate/execute, audit
 *
 * WS1 handoff contracts (nodes 3–7):
 *   node3 → DatasetProfile       (DatasetProfileService.profile)
 *   node4 → CapabilityMatrix     (CapabilityMatrixService.describe)
 *   node5 → List<MetricEvidence> (GovernedMetricService.compute per M01–M18)
 *   node6 → List<AnomalyIssue>   (GovernedAnomalyService.detect)
 *   node7 → AnomalyIssue         (highest priority selected issue)
 */
@Service
public final class DeterministicWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DeterministicWorkflowEngine.class);

    private final SupervisorAgent supervisorAgent;
    private final InvestigationAgent investigationAgent;
    private final EvidenceCriticAgent evidenceCriticAgent;
    private final EvidenceVerifier evidenceVerifier;
    private final PlanValidator planValidator;
    private final EvidenceMerger evidenceMerger;
    private final BriefingActionAgent briefingActionAgent;

    public DeterministicWorkflowEngine(SupervisorAgent supervisorAgent,
                                        InvestigationAgent investigationAgent,
                                        EvidenceCriticAgent evidenceCriticAgent,
                                        EvidenceVerifier evidenceVerifier,
                                        PlanValidator planValidator,
                                        EvidenceMerger evidenceMerger,
                                        BriefingActionAgent briefingActionAgent) {
        this.supervisorAgent = supervisorAgent;
        this.investigationAgent = investigationAgent;
        this.evidenceCriticAgent = evidenceCriticAgent;
        this.evidenceVerifier = evidenceVerifier;
        this.planValidator = planValidator;
        this.evidenceMerger = evidenceMerger;
        this.briefingActionAgent = briefingActionAgent;
    }

    // -------------------------------------------------------------------------
    // WorkflowEngine contract
    // -------------------------------------------------------------------------

    @Override
    public WorkflowCheckpoint start(RunContext context) {
        log.info("run={} starting workflow", context.runId());
        return runFrom(context, WorkflowNode.INITIALIZE_RUN);
    }

    @Override
    public WorkflowCheckpoint resume(RunContext context, ApprovalDecision decision) {
        throw new UnsupportedOperationException(
                "TODO WS3: implement approval resume — revalidate access/expiry/evidence before mock action");
    }

    // -------------------------------------------------------------------------
    // Main routing loop
    // -------------------------------------------------------------------------

    private WorkflowCheckpoint runFrom(RunContext context, WorkflowNode startNode) {
        WorkflowNode current = startNode;
        List<MetricEvidence> evidence = new ArrayList<>();
        List<VerifiedClaim> verifiedClaims = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Typed state passed between nodes
        DatasetProfile datasetProfile = null;
        CapabilityMatrix capabilityMatrix = null;
        String selectedIssueId = null;
        InvestigationPlan plan = null;
        InvestigationResult investigationResult = null;
        VerificationResult verificationResult = null;
        DecisionBrief brief = null;

        while (true) {
            log.debug("run={} node={}", context.runId(), current);
            try {
                switch (current) {

                    // ---- Nodes 1–2: minimal impl ----

                    case INITIALIZE_RUN -> {
                        node1_validate(context);
                        warnings.add("node1: run initialized runId=" + context.runId());
                        current = WorkflowNode.AUTHORIZE_SCOPE;
                    }

                    case AUTHORIZE_SCOPE -> {
                        // WS3: call AccessAuthorizer.require(actor, tenant, READ_ANALYTICS)
                        warnings.add("node2: authorization stubbed — WS3 must implement AccessAuthorizer");
                        current = WorkflowNode.PROFILE_DATASET;
                    }

                    // ---- Nodes 3–7: WS1 stubs with typed contracts ----

                    case PROFILE_DATASET -> {
                        // WS1: implement DatasetProfileService.profile(Path sourceDirectory)
                        // Returns: DatasetProfile with fileProfiles, dataVersion, warnings
                        datasetProfile = ws1_profileDataset(context);
                        warnings.add("node3: dataset profile stubbed — WS1 must implement DatasetProfileService");
                        current = WorkflowNode.BUILD_CAPABILITY_MATRIX;
                    }

                    case BUILD_CAPABILITY_MATRIX -> {
                        // WS1: implement CapabilityMatrixService.describe(tenant, dataVersion)
                        // Returns: CapabilityMatrix with per-metric SUPPORTED/DERIVABLE/UNAVAILABLE
                        capabilityMatrix = ws1_buildCapabilityMatrix(context, datasetProfile);
                        warnings.add("node4: capability matrix stubbed — WS1 must implement CapabilityMatrixService");
                        current = WorkflowNode.COMPUTE_METRIC_SNAPSHOT;
                    }

                    case COMPUTE_METRIC_SNAPSHOT -> {
                        // WS1: implement GovernedMetricService.compute() for M01–M18
                        // Returns: List<MetricEvidence> — current + baseline per metric, per tenant
                        List<MetricEvidence> snapshot = ws1_computeMetricSnapshot(context, capabilityMatrix);
                        evidence.addAll(snapshot);
                        warnings.add("node5: metric snapshot stubbed — WS1 must implement GovernedMetricService");
                        current = WorkflowNode.DETECT_ANOMALIES;
                    }

                    case DETECT_ANOMALIES -> {
                        // WS1: implement GovernedAnomalyService.detect(evidence, capabilityMatrix)
                        // Returns: List<AnomalyIssue> — material changes with severity, category, impactComponents
                        List<AnomalyIssue> anomalies = ws1_detectAnomalies(context, evidence);
                        if (anomalies.isEmpty()) {
                            warnings.add("node6: no material anomalies detected — healthy result");
                            return checkpoint(context, WorkflowNode.DETECT_ANOMALIES,
                                    WorkflowCheckpoint.Status.COMPLETED, evidence, List.of(), warnings);
                        }
                        warnings.add("node6: anomaly detection stubbed — WS1 must implement GovernedAnomalyService");
                        current = WorkflowNode.PRIORITIZE_ISSUE;
                    }

                    case PRIORITIZE_ISSUE -> {
                        // WS1: rank anomalies by approved impact features, return highest-priority issueId
                        // Input:  List<AnomalyIssue> from node6
                        // Output: selected AnomalyIssue.issueId() — string passed to Supervisor
                        selectedIssueId = ws1_prioritizeIssue(context, evidence);
                        warnings.add("node7: issue prioritization stubbed — WS1 must implement prioritizer");
                        current = WorkflowNode.SUPERVISOR_PLAN;
                    }

                    // ---- Nodes 8–11: WS2 IMPLEMENTED ----

                    case SUPERVISOR_PLAN -> {
                        plan = supervisorAgent.plan(context, selectedIssueId);
                        warnings.add("node8: supervisor produced " + plan.tasks().size()
                                + " task(s) for issue=" + selectedIssueId);
                        current = WorkflowNode.VALIDATE_PLAN;
                    }

                    case VALIDATE_PLAN -> {
                        PlanValidator.ValidationResult vr = planValidator.validate(plan, context);
                        warnings.addAll(vr.rejections());
                        plan = vr.plan();
                        warnings.add("node9: plan validated — "
                                + vr.allowedTasks().size() + "/" + vr.allowedTasks().size() + vr.rejections().size()
                                + " tasks allowed");
                        if (!vr.hasAllowedTasks()) {
                            warnings.add("no valid tasks remain — stopping");
                            return checkpoint(context, WorkflowNode.VALIDATE_PLAN,
                                    WorkflowCheckpoint.Status.FAILED, evidence, List.of(), warnings);
                        }
                        current = WorkflowNode.RUN_INVESTIGATIONS;
                    }

                    case RUN_INVESTIGATIONS -> {
                        investigationResult = investigationAgent.investigate(context, plan);
                        warnings.add("node10: completed=" + investigationResult.completedTasks().size()
                                + " pending=" + investigationResult.pendingTasks().size()
                                + " evidence=" + investigationResult.evidence().size());
                        current = WorkflowNode.MERGE_EVIDENCE;
                    }

                    case MERGE_EVIDENCE -> {
                        EvidenceMerger.MergeResult merged = evidenceMerger.merge(investigationResult);
                        evidence.addAll(merged.evidence());
                        warnings.addAll(merged.warnings());
                        warnings.add("node11: merged " + merged.evidence().size() + " unique evidence items");
                        current = WorkflowNode.EVIDENCE_CRITIC;
                    }

                    // ---- Nodes 12–13: WS2 IMPLEMENTED ----

                    case EVIDENCE_CRITIC -> {
                        VerificationResult critique = evidenceCriticAgent.review(context, investigationResult);
                        warnings.addAll(critique.warnings());
                        warnings.add("node12: critic status=" + critique.status()
                                + " accepted=" + critique.claims().size()
                                + " rejected=" + critique.rejectedClaimIds().size());
                        if (critique.status() == VerificationResult.Status.REJECTED) {
                            return checkpoint(context, WorkflowNode.EVIDENCE_CRITIC,
                                    WorkflowCheckpoint.Status.FAILED, evidence, List.of(), warnings);
                        }
                        // Run the deterministic verifier over accepted critic claims
                        List<Claim> criticClaims = critique.claims().stream()
                                .map(vc -> new Claim(vc.claimId(), vc.text(),
                                        vc.evidenceIds(), vc.kind()))
                                .toList();
                        verificationResult = evidenceVerifier.verify(
                                context, criticClaims, evidence);
                        warnings.addAll(verificationResult.warnings());
                        warnings.add("node13: verifier status=" + verificationResult.status()
                                + " verified=" + verificationResult.claims().size()
                                + " rejected=" + verificationResult.rejectedClaimIds().size());
                        verifiedClaims.addAll(verificationResult.claims());
                        current = WorkflowNode.VERIFY_EVIDENCE;
                    }

                    case VERIFY_EVIDENCE -> {
                        // Already executed inside EVIDENCE_CRITIC case above (one correction cycle)
                        current = WorkflowNode.COMPOSE_DECISION_BRIEF;
                    }

                    // ---- Node 14: WS2 IMPLEMENTED — BriefingActionAgent ----

                    case COMPOSE_DECISION_BRIEF -> {
                        VerificationResult briefInput = verificationResult != null
                                ? verificationResult
                                : new VerificationResult(VerificationResult.Status.QUALIFIED,
                                        List.copyOf(verifiedClaims), Set.of(), List.copyOf(warnings));
                        brief = briefingActionAgent.draft(context, briefInput);
                        warnings.add("node14: brief composed proposals=" + brief.proposedActions().size()
                                + " caveats=" + brief.caveats().size());
                        current = WorkflowNode.ACTION_POLICY_GATE;
                    }

                    case ACTION_POLICY_GATE -> {
                        return stubCheckpoint(context, WorkflowNode.ACTION_POLICY_GATE,
                                evidence, verifiedClaims, warnings,
                                "TODO WS3: implement action policy gate");
                    }

                    case APPROVAL_INTERRUPT -> {
                        return checkpoint(context, WorkflowNode.APPROVAL_INTERRUPT,
                                WorkflowCheckpoint.Status.AWAITING_APPROVAL,
                                evidence, verifiedClaims, warnings);
                    }

                    case REVALIDATE_AND_EXECUTE_MOCK_ACTION -> {
                        return stubCheckpoint(context, WorkflowNode.REVALIDATE_AND_EXECUTE_MOCK_ACTION,
                                evidence, verifiedClaims, warnings,
                                "TODO WS3: implement revalidation and idempotent mock action");
                    }

                    case APPEND_AUDIT_EVENT -> {
                        return stubCheckpoint(context, WorkflowNode.APPEND_AUDIT_EVENT,
                                evidence, verifiedClaims, warnings,
                                "TODO WS3: implement AuditLedger.append()");
                    }
                }

            } catch (UnsupportedOperationException stub) {
                warnings.add("stub reached at node=" + current + ": " + stub.getMessage());
                return checkpoint(context, current, WorkflowCheckpoint.Status.PARTIAL,
                        evidence, verifiedClaims, warnings);
            } catch (Exception e) {
                log.error("run={} node={} failed: {}", context.runId(), current, e.getMessage(), e);
                warnings.add("node " + current + " failed: " + e.getMessage());
                return checkpoint(context, current, WorkflowCheckpoint.Status.FAILED,
                        evidence, List.of(), warnings);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Node 1 — minimal validation (WS3 will extend)
    // -------------------------------------------------------------------------

    private void node1_validate(RunContext context) {
        if (context.runId() == null) throw new IllegalArgumentException("runId required");
        if (context.tenant() == null) throw new IllegalArgumentException("tenant required");
        if (context.asOfDate() == null) throw new IllegalArgumentException("asOfDate required");
        if (context.budget() == null) throw new IllegalArgumentException("budget required");
    }

    // -------------------------------------------------------------------------
    // Nodes 3–7 — WS1 stubs with typed return contracts
    // -------------------------------------------------------------------------

    private DatasetProfile ws1_profileDataset(RunContext context) {
        // WS1 must: call DatasetProfileService.profile(Path.of(mobilityDataProperties.directory()))
        // and return a DatasetProfile with file checksums, row counts and quality warnings.
        throw new UnsupportedOperationException(
                "TODO WS1: DatasetProfileService.profile(sourceDirectory) — "
                + "read official CSV files, compute sha256, count source/parsed/canonical rows");
    }

    private CapabilityMatrix ws1_buildCapabilityMatrix(RunContext context,
                                                        DatasetProfile profile) {
        // WS1 must: call CapabilityMatrixService.describe(context.tenant(), profile.dataVersion())
        // and return per-metric SUPPORTED/DERIVABLE_WITH_CAVEAT/UNAVAILABLE status.
        throw new UnsupportedOperationException(
                "TODO WS1: CapabilityMatrixService.describe(tenant, dataVersion) — "
                + "derive from profile availability; no GPS/route/driver-id unless data present");
    }

    private List<MetricEvidence> ws1_computeMetricSnapshot(RunContext context,
                                                             CapabilityMatrix matrix) {
        // WS1 must: call GovernedMetricService.compute(MetricRequest) for each enabled metric
        // in the capability matrix. Return List<MetricEvidence> — current + baseline per metric.
        // Use approved M01–M18 definitions. UNAVAILABLE metrics must explain why (no guesses).
        throw new UnsupportedOperationException(
                "TODO WS1: GovernedMetricService.compute() for enabled metrics in capability matrix — "
                + "M01 delayed trip rate minimum; M03 delay reason; M06 no-show; M09 cost; M13 alerts");
    }

    private List<AnomalyIssue> ws1_detectAnomalies(RunContext context,
                                                     List<MetricEvidence> evidence) {
        // WS1 must: apply approved comparison rules (prior 4 complete weeks baseline),
        // materiality thresholds and exclusions. Return empty list for healthy result.
        // AnomalyIssue must carry severity, category, impactComponents and evidence references.
        throw new UnsupportedOperationException(
                "TODO WS1: GovernedAnomalyService.detect(evidence, capabilityMatrix) — "
                + "apply approved delta thresholds; G3: DEVICE_NOT_REACHABLE spike is regime change, not anomaly");
    }

    private String ws1_prioritizeIssue(RunContext context, List<MetricEvidence> evidence) {
        // WS1 must: rank AnomalyIssue list by approved impact features (severity, affected population,
        // delta magnitude) and return the highest-priority AnomalyIssue.issueId().
        throw new UnsupportedOperationException(
                "TODO WS1: issue prioritizer — rank by approved impact features, return issueId");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkflowCheckpoint checkpoint(RunContext context, WorkflowNode node,
                                           WorkflowCheckpoint.Status status,
                                           List<MetricEvidence> evidence,
                                           List<VerifiedClaim> claims,
                                           List<String> warnings) {
        return new WorkflowCheckpoint(context, 1L, node, status,
                List.copyOf(evidence), List.copyOf(claims), null, List.copyOf(warnings));
    }

    private WorkflowCheckpoint stubCheckpoint(RunContext context, WorkflowNode node,
                                               List<MetricEvidence> evidence,
                                               List<VerifiedClaim> claims,
                                               List<String> warnings, String msg) {
        List<String> w = new ArrayList<>(warnings);
        w.add(msg);
        return checkpoint(context, node, WorkflowCheckpoint.Status.PARTIAL, evidence, claims, w);
    }
}
