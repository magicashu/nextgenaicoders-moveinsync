package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.Optional;

/**
 * WS1/WS2 handoff: resolves a detector-selected issue only after the workflow has authorized
 * the run. Implementations must not perform an unscoped issue lookup.
 */
public interface SupervisorIssueSource {
    Optional<SupervisorPlanningRequest> find(RunContext context, String issueId);
}
