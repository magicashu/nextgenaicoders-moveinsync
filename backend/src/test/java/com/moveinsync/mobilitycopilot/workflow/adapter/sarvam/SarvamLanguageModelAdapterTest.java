package com.moveinsync.mobilitycopilot.workflow.adapter.sarvam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.SarvamProperties;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.agents.SupervisorPlanningRequest;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SarvamLanguageModelAdapterTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> response = new AtomicReference<>(
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"tasks\\\":[]}\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}");
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("api-subscription-key"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private SarvamProperties properties(String apiKey) {
        return new SarvamProperties(apiKey, "http://127.0.0.1:" + server.getAddress().getPort()
                + "/v1/chat/completions", "sarvam-105b", Duration.ofSeconds(2), 24000, 64);
    }

    static LanguageModelPort.ModelRequest request() {
        var tenant = new TenantContext("test-tenant");
        var context = new RunContext(UUID.randomUUID(),
                new ActorContext("test", Set.of("TRANSPORT_MANAGER"), Set.of(tenant)), tenant,
                "TRANSPORT_MANAGER", LocalDate.of(2026, 8, 1),
                new RunVersions("test-data", "metrics-v1", "workflow-v1", "prompt-v1", "sarvam-105b", "config-v1"),
                new WorkflowBudget(12, 4, 1, Duration.ofSeconds(30), 4), Instant.now().plusSeconds(60));
        return new LanguageModelPort.ModelRequest(context, LanguageModelPort.AgentRole.SUPERVISOR,
                "prompt-v1", "Return JSON only: {\"tasks\":[]}. Do not add tasks.", List.of());
    }

    @Test void sends_documented_auth_and_bounded_json_payload_and_reads_usage() throws Exception {
        var result = new SarvamLanguageModelAdapter(properties("test-secret"), null).complete(request());
        var payload = new ObjectMapper().readTree(body.get());
        assertThat(key.get()).isEqualTo("test-secret");
        assertThat(payload.path("model").asText()).isEqualTo("sarvam-105b");
        assertThat(payload.path("max_tokens").asInt()).isEqualTo(4096);
        assertThat(payload.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(payload.path("stream").asBoolean()).isFalse();
        assertThat(body.get()).doesNotContain("test-secret");
        assertThat(result.structuredOutput()).isEqualTo("{\"tasks\":[]}");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(5);
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 500})
    void errors_do_not_retry_or_expose_provider_body(int code) {
        status.set(code); response.set("private provider details");
        assertThatThrownBy(() -> new SarvamLanguageModelAdapter(properties("test-secret"), null).complete(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP " + code)
                .hasMessageNotContaining("private provider details").hasMessageNotContaining("test-secret");
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "not-json",
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"not-json\"}}]}"})
    void rejects_missing_malformed_or_truncated_output(String invalid) {
        response.set(invalid);
        assertThatThrownBy(() -> new SarvamLanguageModelAdapter(properties("test-secret"), null).complete(request()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void missing_key_fails_before_network_and_properties_redact_it() {
        assertThatThrownBy(() -> new SarvamLanguageModelAdapter(properties(""), null))
                .hasMessageContaining("SARVAM_API_KEY");
        assertThat(properties("test-secret").toString()).doesNotContain("test-secret");
        assertThat(calls).hasValue(0);
    }

    @Test void oversized_prompt_fails_before_network() {
        var r = request();
        var oversized = new LanguageModelPort.ModelRequest(r.context(), r.role(), r.promptVersion(), "x".repeat(24001), List.of());
        assertThatThrownBy(() -> new SarvamLanguageModelAdapter(properties("test-secret"), null).complete(oversized))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }

    @Test void both_agents_use_http_adapter_and_critic_cannot_promote_rejected_claims() {
        var context = request().context();
        var metricRequest = new com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest(context.tenant(),
                com.moveinsync.mobilitycopilot.metrics.domain.MetricId.M01_DELAYED_TRIP_RATE,
                com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest.Measure.VALUE,
                new com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow(LocalDate.of(2026,6,1),LocalDate.of(2026,6,7)),
                Map.of(),context.versions().data());
        var evidence = new com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence("E1", metricRequest,
                com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus.AVAILABLE, new java.math.BigDecimal("20"),
                com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.PERCENT,
                new java.math.BigDecimal("20"), new java.math.BigDecimal("100"),100,"metrics-v1","fixture",List.of());
        var adapter = new SarvamLanguageModelAdapter(properties("test-secret"),null);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("model",adapter);beans.registerSingleton("mapper",mapper);
        var supervisor = new com.moveinsync.mobilitycopilot.workflow.agents.GovernedSupervisorAgent(
                Optional.empty(),beans.getBeanProvider(LanguageModelPort.class),beans.getBeanProvider(ObjectMapper.class));
        var capabilities = new EnumMap<com.moveinsync.mobilitycopilot.metrics.domain.MetricId,
                com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix.Capability>(com.moveinsync.mobilitycopilot.metrics.domain.MetricId.class);
        for(var id:com.moveinsync.mobilitycopilot.metrics.domain.MetricId.values()) capabilities.put(id,
                new com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix.Capability(
                        com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix.Status.SUPPORTED,"fixture"));
        var issue = new com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyIssue("test",context.tenant(),context.versions().data(),
                "Delay","UNASSESSED","DELAY",List.of(evidence),Map.of(),List.of());
        var plan = supervisor.plan(new SupervisorPlanningRequest(context,issue,
                new com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix(context.tenant(),context.versions().data(),capabilities)));
        assertThat(plan.tasks()).hasSize(5);
        assertThat(calls).hasValue(1);
        response.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"overallStatus\\\":\\\"NEEDS_CORRECTION\\\",\\\"claims\\\":[{\\\"claimId\\\":\\\"claim-E1\\\",\\\"decision\\\":\\\"REJECT\\\",\\\"issues\\\":[],\\\"requiredCaveats\\\":[]}],\\\"globalCaveats\\\":[]}\"}}]}");
        var critic = new com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgentImpl(
                new com.moveinsync.mobilitycopilot.evidence.application.DeterministicEvidenceVerifier(),Optional.of(adapter),mapper);
        assertThat(critic.review(context,new InvestigationResult(List.of(evidence),List.of(),List.of(),List.of())).claims()).isEmpty();
        assertThat(calls).hasValue(2);
        status.set(429);
        assertThat(critic.review(context,new InvestigationResult(List.of(evidence),List.of(),List.of(),List.of())).claims()).hasSize(1);
        assertThat(calls).hasValue(3);
    }
}
