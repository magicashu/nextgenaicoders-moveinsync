package com.moveinsync.mobilitycopilot.approval.application;

import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalStore {

    ApprovalRequest create(ApprovalRequest request);

    ApprovalDecision decide(ApprovalDecision decision);

    Optional<ApprovalRequest> findRequest(UUID approvalId);

    Optional<ApprovalDecision> findDecision(UUID approvalId);
}
