package com.moveinsync.mobilitycopilot.workflow.domain;

public enum WorkflowStep {
    INITIALIZED,
    AUTHORIZED,
    PLANNED,
    INVESTIGATED,
    VERIFIED,
    BRIEFED,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    // terminal refinements added by the workflow workstream (additive, D-041 shapes unchanged)
    HEALTHY,
    REPORT_ONLY,
    EXECUTED,
    APPROVED_NOT_EXECUTED,
    REJECTED,
    EXPIRED
}
