package com.moveinsync.mobilitycopilot.workflow.domain;

import java.util.Map;

public record InvestigationTask(
        String worker,
        String question,
        Map<String, String> parameters) {
}
