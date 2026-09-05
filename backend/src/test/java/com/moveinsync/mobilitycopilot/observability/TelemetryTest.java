package com.moveinsync.mobilitycopilot.observability;

import com.moveinsync.mobilitycopilot.observability.export.LangfuseOtlpExporter;
import com.moveinsync.mobilitycopilot.observability.export.OtlpJson;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryTest {

    @Test
    void oneNestedTraceCoversTheWholeDecisionPathWithSafeAttributesOnly() {
        TraceExporter.InMemory exporter = new TraceExporter.InMemory();
        TraceRecorder recorder = new TraceRecorder(exporter);
        UUID runId = UUID.randomUUID();
        TraceRecorder.Trace trace = recorder.start(runId, "pinnacle-Slc", Map.of(TraceAttributes.PERSONA, "TRANSPORT_MANAGER", "prompt", "secret prompt text"));

        Span authorize = trace.begin("authorize_scope", Span.Kind.AUTHORIZATION, Map.of(TraceAttributes.OUTCOME, "authorized"));
        trace.end(authorize, "OK", Map.of());
        Span supervisor = trace.begin("supervisor_plan", Span.Kind.AGENT, Map.of(TraceAttributes.MODEL_ID, "none"));
        Span model = trace.begin("model.supervisor", Span.Kind.MODEL, Map.of(TraceAttributes.MODEL_NAME, "none", "gen_ai.prompt", "must not appear"));
        trace.recordModelCall("supervisor", "none", 0, 0, 0, true);
        trace.end(model, "OK", Map.of());
        trace.end(supervisor, "OK", Map.of("tasks", "6"));
        Span investigations = trace.begin("run_investigations", Span.Kind.WORKFLOW_NODE, Map.of());
        for (String worker : List.of("vendor", "site_shift_direction", "delay_reason")) {
            Span tool = trace.beginUnder(investigations, "tool." + worker, Span.Kind.TOOL, Map.of(TraceAttributes.WORKER, worker, "stwid", "149530"));
            trace.recordToolCall(worker, 40, true);
            trace.end(tool, "OK", Map.of(TraceAttributes.EVIDENCE_COUNT, "3"));
        }
        trace.end(investigations, "OK", Map.of());
        for (String[] step : new String[][] {{"evidence_critic", "CRITIC"}, {"compose_decision_brief", "REPORT"}, {"approval_interrupt", "APPROVAL"}, {"revalidate", "REVALIDATION"}, {"execute", "EXECUTION"}, {"audit_link", "AUDIT_LINK"}}) {
            Span span = trace.begin(step[0], Span.Kind.valueOf(step[1]), Map.of());
            trace.end(span, "OK", Map.of(TraceAttributes.AUDIT_EVENT_ID, UUID.randomUUID().toString()));
        }
        recorder.finish(trace, "OK", Map.of(TraceAttributes.OUTCOME, "EXECUTED"));

        assertThat(trace.traceId()).hasSize(32).isEqualTo(TraceRecorder.traceIdFor(runId));
        assertThat(trace.spans()).hasSize(14);
        assertThat(trace.root().children()).extracting(Span::name).containsExactly("authorize_scope", "supervisor_plan", "run_investigations", "evidence_critic",
                "compose_decision_brief", "approval_interrupt", "revalidate", "execute", "audit_link");
        assertThat(trace.root().children().get(2).children()).extracting(Span::name).containsExactly("tool.vendor", "tool.site_shift_direction", "tool.delay_reason");
        assertThat(trace.root().attributes()).doesNotContainKey("prompt").containsEntry(TraceAttributes.BUSINESS_UNIT, "pinnacle-Slc");
        assertThat(model.attributes()).doesNotContainKey("gen_ai.prompt").containsEntry(TraceAttributes.LANGFUSE_OBSERVATION_TYPE, "generation");
        assertThat(trace.root().children().get(2).children().getFirst().attributes().get("stwid")).startsWith("tok_");
        assertThat(trace.ledger().toolCalls()).isEqualTo(3);
        assertThat(trace.ledger().fallbackCalls()).isEqualTo(1);
        assertThat(trace.summary()).containsKeys("latencyMs", "toolCalls", TraceAttributes.ESTIMATED_COST_USD);
        assertThat(exporter.traces()).hasSize(1);

        JsonNode otlp = new ObjectMapper().valueToTree(OtlpJson.request(trace, "mobility-decision-copilot", "test"));
        assertThat(otlp.path("resourceSpans").get(0).path("scopeSpans").get(0).path("spans")).hasSize(14);
        assertThat(otlp.toString()).doesNotContain("secret prompt text").doesNotContain("149530").contains("langfuse.trace.name");
    }

    @Test
    void langfuseOutageNeverBlocksOrLosesTheLocalTraceId() throws Exception {
        LangfuseOtlpExporter exporter = new LangfuseOtlpExporter("http://127.0.0.1:9", "pk-lf-test", "sk-lf-test", "svc", "test", 2, Duration.ofMillis(300));
        TraceRecorder recorder = new TraceRecorder(exporter);
        long started = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            TraceRecorder.Trace trace = recorder.start(UUID.randomUUID(), "pinnacle-Slc", Map.of());
            recorder.finish(trace, "OK", Map.of());
            assertThat(trace.traceId()).hasSize(32);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(elapsedMs).as("export must not block the workflow").isLessThan(200);
        Thread.sleep(1200);
        TraceExporter.ExporterStatus status = exporter.status();
        assertThat(status.degraded()).isTrue();
        assertThat(status.failures() + status.dropped()).isGreaterThanOrEqualTo(3);
        assertThat(status.lastError()).doesNotContain("sk-lf-test");
        exporter.close();
    }

    @Test
    void costLedgerEstimatesFromConfiguredPrices() {
        CostAndLatencyLedger ledger = new CostAndLatencyLedger(new java.math.BigDecimal("0.0030"), new java.math.BigDecimal("0.0150"));
        ledger.recordModelCall("supervisor", "model-x", 1000, 200, 800, false);
        ledger.recordModelCall("critic", "model-x", 0, 0, 0, true);
        ledger.recordToolCall("vendor", 50, true);
        ledger.recordToolCall("cost_billing", 30, false);
        assertThat(ledger.estimatedCostUsd()).isEqualByComparingTo("0.006000");
        assertThat(ledger.attributes()).containsEntry("modelCalls", "1").containsEntry("fallbackCalls", "1").containsEntry("toolFailures", "1").containsEntry("toolCalls.vendor", "1");
    }
}
