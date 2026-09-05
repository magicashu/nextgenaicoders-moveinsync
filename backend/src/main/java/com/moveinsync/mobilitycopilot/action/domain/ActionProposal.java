package com.moveinsync.mobilitycopilot.action.domain;

import java.util.UUID;

public record ActionProposal(
        UUID actionId,
        String type,
        String title,
        String rationale,
        String status) {
}
