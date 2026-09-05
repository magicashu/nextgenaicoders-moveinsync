package com.moveinsync.mobilitycopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mobility.ai.sarvam")
public record SarvamProperties(String apiKey, String endpoint, String model,
                                Duration timeout, int maxPromptChars, int maxEvidenceItems) {
    @Override public String toString() {
        return "SarvamProperties[apiKey=<redacted>, model=" + model + ", timeout=" + timeout + "]";
    }
}
