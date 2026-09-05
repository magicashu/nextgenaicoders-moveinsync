package com.moveinsync.mobilitycopilot.api.dto;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** REST payloads. Every analytical number carries an evidence reference; there is no free-form numeric field. */
public final class ApiDtos {

    private ApiDtos() {
    }

    /** One evidence-backed KPI tile. */
    public record Kpi(String label, MetricResult metric, String evidenceId, String comparison, String configuredTarget, String targetLabel, boolean meetsTarget) {
    }

    public record Finding(String claimId, String text, String kind, List<String> evidenceIds, String worker) {
    }

    public record ApprovalView(UUID approvalId, UUID actionId, String status, String actionType, String title, String rationale, Map<String, String> scope,
                               String evidenceVersion, Instant evidenceTimestamp, Instant createdAt, Instant expiresAt, String consequence) {
    }

    public record TrustPanel(UUID runId, String traceId, String finalStep, String dataVersion, String contractVersion, String workflowVersion,
                             String promptVersion, String modelId, String ruleVersion, String targetVersion, long latencyMs, int modelCalls,
                             int fallbackCalls, long inputTokens, long outputTokens, int toolCalls, BigDecimal confidence, List<String> confidenceComponents,
                             List<String> capabilityGaps, List<String> dataQualityNotes, Map<String, String> branchStatus, List<TransitionView> transitions) {
    }

    public record TransitionView(String node, String subNode, String outcome, long durationMs, Instant startedAt) {
    }

    public record OperationsSection(String headline, String status, Kpi headlineKpi, List<Kpi> supportingKpis, List<Finding> findings, List<Finding> caveats,
                                    ActionProposal recommendedAction, ApprovalView approval, ExecutionReceipt receipt) {
    }

    public record LeadershipSection(String title, List<String> narrative, String recommendation, String forwardableText) {
    }

    /** GET /api/v1/briefs/morning and POST /api/v1/workflows. */
    public record MorningBriefResponse(UUID runId, UUID workflowId, String businessUnit, LocalDate asOfDate, String persona, String status,
                                       OperationsSection operations, LeadershipSection leadership, EvidenceBundle evidence, TrustPanel trust,
                                       List<String> suggestedQuestions, List<String> errors) {
    }

    public record WorkflowRunRequest(LocalDate asOfDate, String persona) {
    }

    /** POST /api/v1/questions. */
    public record QuestionRequest(@NotBlank @Size(max = 500) String question, LocalDate asOfDate, UUID relatedRunId, String persona) {
    }

    public record QuestionResponse(UUID runId, String businessUnit, String intent, List<String> workers, boolean refused, String refusalReason,
                                   String answer, List<Finding> supportingFindings, List<Finding> caveats, EvidenceBundle evidence, TrustPanel trust,
                                   ActionProposal draftedAction, List<String> followUps) {
    }

    /** POST /api/v1/approvals/{approvalId}/decision. */
    public record ApprovalDecisionRequest(@NotBlank String decision, @Size(max = 1000) String comment, Map<String, String> editedScope) {
    }

    public record ApprovalDecisionResponse(UUID approvalId, UUID runId, String decision, String approvalStatus, String workflowStatus,
                                           ExecutionReceipt receipt, List<String> revalidation, TrustPanel trust) {
    }

    /** GET /api/v1/audit/{workflowId}. */
    public record AuditResponse(UUID runId, String businessUnit, String traceId, List<AuditEvent> events, int count) {
    }
}
