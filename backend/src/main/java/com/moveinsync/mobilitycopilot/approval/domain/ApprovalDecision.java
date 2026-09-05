package com.moveinsync.mobilitycopilot.approval.domain;

import java.time.Instant;
import java.util.UUID;

public record ApprovalDecision(
        UUID actionId,
        String decision,
        String decidedBy,
        Instant decidedAt,
        String comment) {
}
