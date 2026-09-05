package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import java.util.List;
import com.moveinsync.mobilitycopilot.workflow.domain.*;

/** WS2: challenge claims and invoke deterministic evidence validation; reject unsupported vendor blame. */
public interface EvidenceCriticAgent {
    VerificationResult review(RunContext context, InvestigationResult investigation);

    /** Candidate claims come from evidence merging; this overload preserves the original workflow boundary. */
    VerificationResult review(RunContext context, List<Claim> candidates, InvestigationResult investigation);
}
