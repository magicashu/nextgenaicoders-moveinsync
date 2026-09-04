package com.moveinsync.mobilitycopilot.approval.adapter.inmemory;

import com.moveinsync.mobilitycopilot.approval.application.ApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalStatus;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalTransitionException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Demo/test repository with the same transition guarantees as the PostgreSQL adapter. */
public class InMemoryApprovalRepository implements ApprovalRepository {

    private final Map<UUID, ApprovalRecord> records = new ConcurrentHashMap<>();

    @Override
    public synchronized ApprovalRequest create(ApprovalRequest request) {
        if (records.containsKey(request.approvalId())) {
            throw new ApprovalTransitionException("DUPLICATE_APPROVAL", "Approval already exists");
        }
        if (records.values().stream().anyMatch(r -> r.request().proposal().actionId().equals(request.proposal().actionId()) && r.status() == ApprovalStatus.PENDING)) {
            throw new ApprovalTransitionException("DUPLICATE_ACTION", "A pending approval already exists for this action");
        }
        records.put(request.approvalId(), new ApprovalRecord(request, ApprovalStatus.PENDING, null, Instant.now()));
        return request;
    }

    @Override
    public synchronized ApprovalDecision decide(ApprovalDecision decision) {
        ApprovalRecord record = records.get(decision.approvalId());
        if (record == null) {
            throw new ApprovalTransitionException("UNKNOWN_APPROVAL", "Approval request does not exist");
        }
        if (record.status() != ApprovalStatus.PENDING) {
            throw new ApprovalTransitionException("ALREADY_DECIDED", "Approval is already " + record.status());
        }
        ApprovalStatus status = switch (decision.decision()) {
            case APPROVE -> ApprovalStatus.APPROVED;
            case REJECT -> ApprovalStatus.REJECTED;
            case EDIT -> ApprovalStatus.EDITED;
        };
        records.put(decision.approvalId(), new ApprovalRecord(record.request(), status, decision, Instant.now()));
        return decision;
    }

    @Override
    public Optional<ApprovalRequest> findRequest(UUID approvalId) {
        return Optional.ofNullable(records.get(approvalId)).map(ApprovalRecord::request);
    }

    @Override
    public Optional<ApprovalDecision> findDecision(UUID approvalId) {
        return Optional.ofNullable(records.get(approvalId)).flatMap(ApprovalRecord::decisionOptional);
    }

    @Override
    public Optional<ApprovalRecord> findRecord(UUID approvalId) {
        return Optional.ofNullable(records.get(approvalId));
    }

    @Override
    public Optional<ApprovalRecord> findByActionId(UUID actionId) {
        return records.values().stream().filter(r -> r.request().proposal().actionId().equals(actionId))
                .max(java.util.Comparator.comparing(ApprovalRecord::updatedAt));
    }

    @Override
    public List<ApprovalRecord> findByRunId(UUID runId) {
        return records.values().stream().filter(r -> r.request().runId().equals(runId)).toList();
    }

    @Override
    public List<ApprovalRecord> findPending(String businessUnit) {
        return records.values().stream().filter(r -> r.status() == ApprovalStatus.PENDING && r.request().businessUnit().equals(businessUnit)).toList();
    }

    @Override
    public synchronized ApprovalRecord expire(UUID approvalId, Instant now) {
        ApprovalRecord record = records.get(approvalId);
        if (record == null) {
            throw new ApprovalTransitionException("UNKNOWN_APPROVAL", "Approval request does not exist");
        }
        if (record.status() == ApprovalStatus.PENDING) {
            record = new ApprovalRecord(record.request(), ApprovalStatus.EXPIRED, null, now);
            records.put(approvalId, record);
        }
        return record;
    }

    static ApprovalStatus statusFor(ApprovalDecisionType type) {
        return switch (type) {
            case APPROVE -> ApprovalStatus.APPROVED;
            case REJECT -> ApprovalStatus.REJECTED;
            case EDIT -> ApprovalStatus.EDITED;
        };
    }
}
