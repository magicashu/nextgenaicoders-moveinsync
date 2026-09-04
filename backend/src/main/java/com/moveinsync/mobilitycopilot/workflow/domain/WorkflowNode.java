package com.moveinsync.mobilitycopilot.workflow.domain;

/** The eighteen main workflow nodes (architecture plan section 5). START/END are not counted. */
public enum WorkflowNode {
    INITIALIZE_RUN(1, Kind.DETERMINISTIC),
    AUTHORIZE_SCOPE(2, Kind.DETERMINISTIC),
    PROFILE_DATASET(3, Kind.DETERMINISTIC),
    BUILD_CAPABILITY_MATRIX(4, Kind.DETERMINISTIC),
    COMPUTE_METRIC_SNAPSHOT(5, Kind.DETERMINISTIC),
    DETECT_ANOMALIES(6, Kind.DETERMINISTIC),
    PRIORITIZE_ISSUE(7, Kind.DETERMINISTIC),
    SUPERVISOR_PLAN(8, Kind.LLM_SUPERVISOR),
    VALIDATE_PLAN(9, Kind.DETERMINISTIC),
    RUN_INVESTIGATIONS(10, Kind.SUBGRAPH_INVESTIGATOR),
    MERGE_EVIDENCE(11, Kind.DETERMINISTIC),
    EVIDENCE_CRITIC(12, Kind.LLM_CRITIC),
    VERIFY_EVIDENCE(13, Kind.DETERMINISTIC),
    COMPOSE_DECISION_BRIEF(14, Kind.LLM_BRIEFING),
    ACTION_POLICY_GATE(15, Kind.DETERMINISTIC),
    APPROVAL_INTERRUPT(16, Kind.HUMAN),
    REVALIDATE_AND_EXECUTE(17, Kind.DETERMINISTIC),
    APPEND_AUDIT_EVENT(18, Kind.DETERMINISTIC);

    public enum Kind { DETERMINISTIC, LLM_SUPERVISOR, LLM_CRITIC, LLM_BRIEFING, SUBGRAPH_INVESTIGATOR, HUMAN }

    private final int ordinalNumber;
    private final Kind kind;

    WorkflowNode(int ordinalNumber, Kind kind) {
        this.ordinalNumber = ordinalNumber;
        this.kind = kind;
    }

    public int number() {
        return ordinalNumber;
    }

    public Kind kind() {
        return kind;
    }

    /** Stable span name used by telemetry. */
    public String spanName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
