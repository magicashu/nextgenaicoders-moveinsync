package com.moveinsync.mobilitycopilot.workflow.adapter.sarvam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.config.SarvamProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit billable opt-in. Never permits deterministic fallback to disguise provider failure. */
@EnabledIfSystemProperty(named = "sarvamLive", matches = "true")
class SarvamLiveTest {
    @Test void live_provider_returns_expected_json() throws Exception {
        String key = System.getenv("SARVAM_API_KEY");
        assertThat(key).as("Set SARVAM_API_KEY locally before running the live test").isNotBlank();
        var properties = new SarvamProperties(key,
                System.getenv().getOrDefault("SARVAM_ENDPOINT", "https://api.sarvam.ai/v1/chat/completions"),
                System.getenv().getOrDefault("SARVAM_MODEL", "sarvam-105b"), Duration.ofSeconds(30), 24000, 64);
        var result = new SarvamLanguageModelAdapter(properties, null).complete(SarvamLanguageModelAdapterTest.request());
        var json = new ObjectMapper().readTree(result.structuredOutput());
        assertThat(json.path("tasks").isArray()).isTrue();
        assertThat(json.path("tasks").size()).isZero();
        assertThat(result.model()).isEqualTo(properties.model());
    }
}
