package com.moveinsync.mobilitycopilot.metrics.domain;

/** Typed rejection of an incompatible metric request (unknown filter, disallowed dimension, missing selector). */
public class MetricRequestException extends IllegalArgumentException {

    private final String code;

    public MetricRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
