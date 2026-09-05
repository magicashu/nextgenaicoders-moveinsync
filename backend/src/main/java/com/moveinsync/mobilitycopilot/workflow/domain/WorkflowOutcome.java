package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;

import java.util.List;

public record WorkflowOutcome(
        DecisionBrief brief,
        WorkflowStep finalStep,
        List<AuditEvent> auditEvents) {
}
