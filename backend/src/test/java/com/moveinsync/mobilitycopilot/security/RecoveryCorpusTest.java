package com.moveinsync.mobilitycopilot.security;

import com.moveinsync.mobilitycopilot.quality.Corpus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCorpusTest {

    @Test
    void recoveryCasesCoverEveryRequiredFailureMode() throws Exception {
        JsonNode corpus = new ObjectMapper().readTree(Files.readString(Corpus.path("evals/recovery/cases.json")));
        assertThat(corpus.path("cases")).extracting(c -> c.path("kind").asText())
                .contains("tool_timeout", "retry_exhaustion", "partial_branch_failure", "malformed_model_output", "crash_resume", "duplicate_effect", "adapter_failure", "telemetry_outage");
        for (JsonNode c : corpus.path("cases")) {
            JsonNode expected = c.path("expected");
            if (expected.has("effects")) {
                assertThat(expected.path("effects").asInt()).isEqualTo(1);
            }
            if (expected.has("actionExecuted")) {
                assertThat(expected.path("actionExecuted").asBoolean()).isFalse();
            }
        }
    }
}
