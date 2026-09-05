package com.moveinsync.mobilitycopilot.evidence.application;

import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.List;

/** WS2: implement deterministic value/unit/window/version/citation checks and safe rejection. */
public interface EvidenceVerifier {
    VerificationResult verify(RunContext context, List<Claim> candidates, List<MetricEvidence> evidence);
}
