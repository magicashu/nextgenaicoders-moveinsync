package com.moveinsync.mobilitycopilot.quality;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ten deterministic metric fixtures (D-033). The corpus is always validated; the live comparison
 * runs when scripts/demo/scorecard.sh has written evals/results/metric-results.json from DuckDB.
 */
class MetricFixtureGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void corpusHasTenReconciledFixtures() throws Exception {
        JsonNode corpus = MAPPER.readTree(Files.readString(Corpus.path("evals/golden/metric-fixtures.json")));
        assertThat(corpus.path("contractVersion").asText()).isEqualTo("metrics-v1.1");
        assertThat(corpus.path("fixtures")).hasSize(10);
        assertThat(corpus.path("fixtures").get(0).path("expected").path("numerator").asInt()).isEqualTo(4357);
        assertThat(corpus.path("fixtures").get(6).path("expected").path("count").asInt()).isEqualTo(6753);
        assertThat(corpus.path("fixtures").get(7).path("expected").path("duplicateLegsRemoved").asInt()).isEqualTo(708);
    }

    @Test
    void liveResultsMatchTheFixturesWhenPresent() throws Exception {
        assumeTrue(Corpus.exists("evals/results/metric-results.json"), "run scripts/demo/scorecard.sh to produce live metric results");
        JsonNode corpus = MAPPER.readTree(Files.readString(Corpus.path("evals/golden/metric-fixtures.json")));
        JsonNode results = MAPPER.readTree(Files.readString(Corpus.path("evals/results/metric-results.json")));
        List<String> mismatches = new ArrayList<>();
        for (JsonNode fixture : corpus.path("fixtures")) {
            JsonNode actual = results.path(fixture.path("id").asText());
            if (actual.isMissingNode()) {
                mismatches.add(fixture.path("id").asText() + ": no live result");
                continue;
            }
            fixture.path("expected").properties().forEach(entry -> {
                JsonNode expected = entry.getValue();
                JsonNode got = actual.path(entry.getKey());
                if (expected.isNumber() && (!got.isNumber() || Math.abs(got.asDouble() - expected.asDouble()) > 0.011)) {
                    mismatches.add(fixture.path("id").asText() + "." + entry.getKey() + ": expected " + expected + " got " + got);
                } else if (expected.isTextual() && !expected.asText().equals(got.asText())) {
                    mismatches.add(fixture.path("id").asText() + "." + entry.getKey() + ": expected " + expected + " got " + got);
                }
            });
        }
        assertThat(mismatches).isEmpty();
    }
}
