package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public final class SupervisorAgent {

    public List<InvestigationTask> plan(WorkflowState state) {
        Map<String, String> scope = Map.of(
                "businessUnit", state.tenant().businessUnit(),
                "asOfDate", state.asOfDate().toString());
        return List.of(
                new InvestigationTask("vendor", "Which vendors contribute to the change?", scope),
                new InvestigationTask("site-shift-direction", "Where is the change concentrated?", scope),
                new InvestigationTask("delay-reason", "Which governed delay reasons changed?", scope));
    }
}
