package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;

import java.util.Objects;

/** Versions and identifiers captured with every decision run. */
public record RunContext(
        ActorContext actor,
        Persona persona,
        RequestMode mode,
        String traceId,
        String workflowVersion,
        String promptVersion,
        String modelId,
        String dataVersion,
        String question) {

    public static final String WORKFLOW_VERSION = "langgraph4j-v1";
    public static final String PROMPT_VERSION = "prompts-v1.1";

    public enum Persona { TRANSPORT_MANAGER, FACILITIES_HEAD, LINE_MANAGER }

    public enum RequestMode { SCHEDULED, ON_DEMAND, QUESTION, RESUME }

    public RunContext {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(persona);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(traceId);
        Objects.requireNonNull(workflowVersion);
        Objects.requireNonNull(promptVersion);
        Objects.requireNonNull(modelId);
    }

    public RunContext withDataVersion(String version) {
        return new RunContext(actor, persona, mode, traceId, workflowVersion, promptVersion, modelId, version, question);
    }
}
