package com.moveinsync.mobilitycopilot.reporting.application;

import java.util.UUID;

public class ApprovalNotFoundException extends RuntimeException {
    public ApprovalNotFoundException(UUID approvalId) {
        super("No approval with id " + approvalId + " is visible to this tenant");
    }
}
