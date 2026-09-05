package com.moveinsync.mobilitycopilot.config;

import com.moveinsync.mobilitycopilot.observability.Span;
import com.moveinsync.mobilitycopilot.observability.TraceRecorder;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Integration-owned bindings that keep optional AI assistance fail-safe. */
@Configuration
public class WorkflowCompositionConfiguration {

    @Bean
    @ConditionalOnMissingBean(LanguageModelPort.class)
    public LanguageModelPort unavailableLanguageModel() {
        return new LanguageModelPort.Unavailable();
    }

    @Bean
    @ConditionalOnMissingBean(TransitionListener.class)
    public TransitionListener traceTransitionListener(TraceRecorder recorder) {
        Map<UUID, TraceRecorder.Trace> traces = new ConcurrentHashMap<>();
        return event -> {
            TraceRecorder.Trace trace = traces.computeIfAbsent(event.runId(), runId -> recorder.start(
                    runId,
                    event.attributes().getOrDefault("businessUnit", "unknown"),
                    Map.of("workflow.version", "workflow-v1")));
            Map<String, Object> attributes = new LinkedHashMap<>(event.attributes());
            attributes.put("workflow.node", event.node().name());
            attributes.put("workflow.duration_ms", event.durationMs());
            attributes.put("workflow.from_step", event.fromStep() == null ? "" : event.fromStep().name());
            attributes.put("workflow.to_step", event.toStep() == null ? "" : event.toStep().name());
            Span span = trace.begin(event.node().spanName(), spanKind(event.node()), attributes);
            trace.end(span, event.toStep() == WorkflowStep.FAILED ? "ERROR" : "OK",
                    Map.of("workflow.outcome", event.outcome()));
            if ((event.node() == WorkflowNode.APPEND_AUDIT_EVENT && terminal(event.toStep()))
                    || event.toStep() == WorkflowStep.FAILED) {
                recorder.finish(trace, event.toStep() == WorkflowStep.FAILED ? "ERROR" : "OK", attributes);
                traces.remove(event.runId());
            }
        };
    }

    private static Span.Kind spanKind(WorkflowNode node) {
        return switch (node) {
            case AUTHORIZE_SCOPE -> Span.Kind.AUTHORIZATION;
            case SUPERVISOR_PLAN -> Span.Kind.AGENT;
            case RUN_INVESTIGATIONS -> Span.Kind.TOOL;
            case EVIDENCE_CRITIC, VERIFY_EVIDENCE -> Span.Kind.CRITIC;
            case COMPOSE_DECISION_BRIEF -> Span.Kind.REPORT;
            case APPROVAL_INTERRUPT -> Span.Kind.APPROVAL;
            case REVALIDATE_AND_EXECUTE -> Span.Kind.REVALIDATION;
            case APPEND_AUDIT_EVENT -> Span.Kind.AUDIT_LINK;
            default -> Span.Kind.WORKFLOW_NODE;
        };
    }

    private static boolean terminal(WorkflowStep step) {
        return step == WorkflowStep.HEALTHY || step == WorkflowStep.REPORT_ONLY || step == WorkflowStep.EXECUTED
                || step == WorkflowStep.APPROVED_NOT_EXECUTED || step == WorkflowStep.REJECTED
                || step == WorkflowStep.EXPIRED || step == WorkflowStep.COMPLETED || step == WorkflowStep.FAILED;
    }
}
