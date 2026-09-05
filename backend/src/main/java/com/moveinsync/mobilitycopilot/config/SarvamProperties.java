package com.moveinsync.mobilitycopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("mobility.sarvam")
public record SarvamProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.sarvam.ai/v1/chat/completions") URI endpoint,
        @DefaultValue("sarvam-105b") String model,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("65536") int maxRequestBytes,
        @DefaultValue("131072") int maxResponseBytes,
        @DefaultValue("4") int maxConcurrentCalls) {

    public SarvamProperties {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !("https".equals(endpoint.getScheme()) || ("http".equals(endpoint.getScheme())
                && ("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost()))))) {
            throw new IllegalArgumentException("Sarvam endpoint requires HTTPS (HTTP allowed only for loopback tests)");
        }
        if (model == null || model.isBlank() || timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0 || maxRequestBytes < 1 || maxRequestBytes > 1_048_576
                || maxResponseBytes < 1 || maxResponseBytes > 1_048_576 || maxConcurrentCalls < 1 || maxConcurrentCalls > 32) {
            throw new IllegalArgumentException("Invalid Sarvam bounds or model configuration");
        }
    }

    @Override
    public String toString() {
        return "SarvamProperties[apiKey=REDACTED, model=" + model + "]";
    }
}
