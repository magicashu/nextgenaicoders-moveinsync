package com.moveinsync.mobilitycopilot.quality;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic gates run against fixture artifacts; the same evaluators run against live output in the scorecard. */
class QualityGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static JsonNode fixture(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("fixtures/quality/" + name)) {
            return MAPPER.readTree(in);
        }
    }

    @Test
    void g1ArtifactPassesEveryGate() throws Exception {
        JsonNode artifact = fixture("g1-run-artifact.json");
        assertThat(Evaluators.schema(artifact)).isEmpty();
        assertThat(Evaluators.evidenceSupport(artifact)).isEmpty();
        assertThat(Evaluators.tenantScope(artifact)).isEmpty();
        assertThat(Evaluators.transitions(artifact)).isEmpty();
        assertThat(Evaluators.idempotency(artifact)).isEmpty();
    }

    @Test
    void tamperedArtifactIsCaughtByEveryGate() throws Exception {
        JsonNode artifact = fixture("g1-run-artifact-tampered.json");
        List<Evaluators.Finding> schema = Evaluators.schema(artifact);
        assertThat(schema).extracting(Evaluators.Finding::code).contains("CONTRACT_VERSION");
        List<Evaluators.Finding> evidence = Evaluators.evidenceSupport(artifact);
        assertThat(evidence).extracting(Evaluators.Finding::code).contains("UNSUPPORTED_NUMBER", "CAUSAL_LANGUAGE", "UNCITED_CLAIM");
        assertThat(evidence).extracting(Evaluators.Finding::detail).anyMatch(d -> d.startsWith("47 in"));
        List<Evaluators.Finding> tenant = Evaluators.tenantScope(artifact);
        assertThat(tenant).extracting(Evaluators.Finding::code).contains("FOREIGN_EVIDENCE", "FOREIGN_ACTION_SCOPE", "FOREIGN_CLAIM");
        List<Evaluators.Finding> transitions = Evaluators.transitions(artifact);
        assertThat(transitions).extracting(Evaluators.Finding::code)
                .contains("EXECUTION_WITHOUT_APPROVAL", "UNVALIDATED_PLAN", "UNVERIFIED_BRIEF", "TOOL_BUDGET_EXCEEDED", "CORRECTION_BUDGET_EXCEEDED");
        assertThat(Evaluators.idempotency(artifact)).extracting(Evaluators.Finding::code).contains("DUPLICATE_EFFECT");
    }

    @Test
    void goldenCasesDeclareTheZeroToleranceExpectations() throws Exception {
        JsonNode g1 = MAPPER.readTree(java.nio.file.Files.readString(Corpus.path("evals/golden/g1-pinnacle-slc.json")));
        assertThat(g1.path("expected").path("headlineMetric").path("numerator").asInt()).isEqualTo(4357);
        assertThat(g1.path("expected").path("trajectory")).hasSize(16);
        assertThat(g1.path("expected").path("actionExecutedBeforeApproval").asBoolean()).isFalse();
        JsonNode g3 = MAPPER.readTree(java.nio.file.Files.readString(Corpus.path("evals/golden/g3-sign-off-regime-change.json")));
        assertThat(g3.path("expected").path("escalationCount").asInt()).isZero();
        assertThat(g3.path("expected").path("classification").asText()).isEqualTo("DATA_REGIME_CHANGE");
        JsonNode g2 = MAPPER.readTree(java.nio.file.Files.readString(Corpus.path("evals/golden/g2-vanta-aus.json")));
        assertThat(g2.path("expected").path("requiredCaveats")).hasSize(3);
    }
}
