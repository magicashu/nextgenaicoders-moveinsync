package com.moveinsync.mobilitycopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "mobility.data")
public record MobilityDataProperties(String directory) {

    public MobilityDataProperties {
        Objects.requireNonNull(directory, "mobility.data.directory is required");
        if (directory.isBlank()) {
            throw new IllegalArgumentException("mobility.data.directory must not be blank");
        }
    }
}
