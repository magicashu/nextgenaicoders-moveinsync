package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import java.time.Instant;

/** WS3: issue only after approval/policy/freshness validation. Construction alone grants no permission. */
public record ExecutionPermit(ActionProposal proposal, ApprovalDecision approval,
                              String freshDataVersion, Instant revalidatedAt) {}
