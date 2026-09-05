package com.moveinsync.mobilitycopilot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mobility.ai.ollama")
public record OllamaProperties(String baseUrl, String model, double temperature, Duration timeout) {}
