package com.moveinsync.mobilitycopilot.workflow.domain;

/** The reusable four-node investigation subgraph executed once per validated task. */
public enum InvestigationNode {
    CHOOSE_ANALYSIS,
    EXECUTE_ANALYSIS,
    VALIDATE_TOOL_RESULT,
    PROGRESS_GATE
}
