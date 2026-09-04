package com.moveinsync.mobilitycopilot.approval.adapter.postgres;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 mapping for the compact control-plane payloads stored as JSONB. */
public final class WorkflowStateJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private WorkflowStateJson() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static WorkflowState readState(String json) {
        return MAPPER.readValue(json, WorkflowState.class);
    }

    public static ActionProposal readProposal(String json) {
        return MAPPER.readValue(json, ActionProposal.class);
    }

    public static java.util.Map<String, String> readStringMap(String json) {
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class, String.class, String.class));
    }
}
