package com.moveinsync.mobilitycopilot.observability;

/**
 * Stable attribute keys for spans and audit links. Only safe metadata is ever attached: tenant-safe
 * identifiers, versions, counts, latency, token/cost measures and outcomes. Never raw employee data,
 * prompts, completions, secrets or chain-of-thought.
 */
public final class TraceAttributes {

    public static final String BUSINESS_UNIT = "mobility.business_unit";
    public static final String RUN_ID = "mobility.run_id";
    public static final String TRACE_ID = "mobility.trace_id";
    public static final String METRIC_ID = "mobility.metric_id";
    public static final String CONTRACT_VERSION = "mobility.contract_version";
    public static final String DATA_VERSION = "mobility.data_version";
    public static final String WORKFLOW_VERSION = "mobility.workflow_version";
    public static final String PROMPT_VERSION = "mobility.prompt_version";
    public static final String MODEL_ID = "mobility.model_id";
    public static final String RULE_VERSION = "mobility.rule_version";
    public static final String NODE = "mobility.node";
    public static final String WORKER = "mobility.worker";
    public static final String TOOL = "mobility.tool";
    public static final String OUTCOME = "mobility.outcome";
    public static final String EVIDENCE_COUNT = "mobility.evidence_count";
    public static final String EVIDENCE_VERSION = "mobility.evidence_version";
    public static final String RETRIES = "mobility.retries";
    public static final String APPROVAL_ID = "mobility.approval_id";
    public static final String ACTION_ID = "mobility.action_id";
    public static final String AUDIT_EVENT_ID = "mobility.audit_event_id";
    public static final String PERSONA = "mobility.persona";
    public static final String INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String MODEL_NAME = "gen_ai.request.model";
    public static final String LANGFUSE_SESSION = "langfuse.session.id";
    public static final String LANGFUSE_USER = "langfuse.user.id";
    public static final String LANGFUSE_TRACE_NAME = "langfuse.trace.name";
    public static final String LANGFUSE_TAGS = "langfuse.trace.tags";
    public static final String LANGFUSE_OBSERVATION_TYPE = "langfuse.observation.type";
    public static final String ESTIMATED_COST_USD = "mobility.estimated_cost_usd";

    private TraceAttributes() {
    }
}
