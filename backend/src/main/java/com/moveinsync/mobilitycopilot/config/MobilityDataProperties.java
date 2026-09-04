package com.moveinsync.mobilitycopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mobility.data")
public record MobilityDataProperties(String directory) {
}
