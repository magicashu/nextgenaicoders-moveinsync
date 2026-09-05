package com.moveinsync.mobilitycopilot.reporting.application;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {
    public RunNotFoundException(UUID runId) {
        super("No decision run with id " + runId + " is visible to this tenant");
    }
}
