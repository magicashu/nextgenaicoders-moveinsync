package com.moveinsync.mobilitycopilot.workflow.application.ports;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.List;

/** WS2: optional Sarvam adapter, bounded provider calls, redaction and deterministic fallback. */
public interface LanguageModelPort {
    ModelResponse complete(ModelRequest request);

    enum AgentRole { SUPERVISOR, INVESTIGATOR, EVIDENCE_CRITIC, BRIEFING_ACTION }

    /** Validate role, tenant, data/metric/prompt versions and payload limits before calling a provider. */
    record ModelRequest(RunContext context, AgentRole role, String promptVersion,
                        String prompt, List<MetricEvidence> evidence) {}

    /** Structured output remains untrusted until the role parses and verifies it. */
    record ModelResponse(String model, String structuredOutput, long inputTokens,
                         long outputTokens, long latencyMillis) {}
}
