package com.moveinsync.mobilitycopilot.approval.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import java.util.Optional;
import java.util.UUID;

/** WS3 owns transactional proposal/version checks and authorized decision persistence. */
public interface ApprovalRepository {
    void saveProposal(ActionProposal proposal);
    Optional<ActionProposal> findProposal(TenantContext tenant, UUID actionId);
    boolean recordDecision(ApprovalDecision decision, long expectedProposalVersion);
}
