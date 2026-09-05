package com.moveinsync.mobilitycopilot.approval.adapter.postgres;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.approval.application.ApprovalRepository;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecision;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRecord;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalStatus;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalTransitionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL approval repository over Flyway V1/V2 tables. Decisions are guarded by a status predicate so a race cannot decide twice. */
public class JdbcApprovalRepository implements ApprovalRepository {

    private static final String SELECT = """
            SELECT r.approval_id, r.run_id, r.business_unit, r.proposal_json::text, r.evidence_version, r.created_at, r.expires_at, r.status, r.updated_at,
                   d.decision, d.decided_by, d.decided_at, d.comment, d.edited_proposal_json::text
            FROM approval_request r
            LEFT JOIN approval_decision d ON d.approval_id = r.approval_id
            """;

    private final DataSource dataSource;

    public JdbcApprovalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ApprovalRequest create(ApprovalRequest request) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO approval_request (approval_id, run_id, business_unit, action_id, proposal_json, evidence_version, created_at, expires_at, status, updated_at)
                     VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, 'PENDING', ?)""")) {
            insert.setObject(1, request.approvalId());
            insert.setObject(2, request.runId());
            insert.setString(3, request.businessUnit());
            insert.setObject(4, request.proposal().actionId());
            insert.setString(5, WorkflowStateJson.write(request.proposal()));
            insert.setString(6, request.evidenceVersion());
            insert.setTimestamp(7, Timestamp.from(request.createdAt()));
            insert.setTimestamp(8, Timestamp.from(request.expiresAt()));
            insert.setTimestamp(9, Timestamp.from(Instant.now()));
            insert.executeUpdate();
            return request;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new ApprovalTransitionException("DUPLICATE_APPROVAL", "Approval or pending action already exists");
            }
            throw new IllegalStateException("Unable to create approval request", e);
        }
    }

    @Override
    public ApprovalDecision decide(ApprovalDecision decision) {
        String status = switch (decision.decision()) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case EDIT -> "EDITED";
        };
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE approval_request SET status = ?, updated_at = ? WHERE approval_id = ? AND status = 'PENDING'")) {
                update.setString(1, status);
                update.setTimestamp(2, Timestamp.from(decision.decidedAt()));
                update.setObject(3, decision.approvalId());
                if (update.executeUpdate() == 0) {
                    connection.rollback();
                    Optional<ApprovalRecord> existing = findRecord(decision.approvalId());
                    throw new ApprovalTransitionException(existing.isPresent() ? "ALREADY_DECIDED" : "UNKNOWN_APPROVAL",
                            existing.map(r -> "Approval is already " + r.status()).orElse("Approval request does not exist"));
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO approval_decision (approval_id, run_id, action_id, decision, decided_by, decided_at, comment, edited_proposal_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)""")) {
                insert.setObject(1, decision.approvalId());
                insert.setObject(2, decision.runId());
                insert.setObject(3, decision.actionId());
                insert.setString(4, decision.decision().name());
                insert.setString(5, decision.decidedBy());
                insert.setTimestamp(6, Timestamp.from(decision.decidedAt()));
                insert.setString(7, decision.comment());
                insert.setString(8, decision.editedProposal() == null ? null : WorkflowStateJson.write(decision.editedProposal()));
                insert.executeUpdate();
            }
            connection.commit();
            return decision;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to record approval decision", e);
        }
    }

    @Override
    public Optional<ApprovalRequest> findRequest(UUID approvalId) {
        return findRecord(approvalId).map(ApprovalRecord::request);
    }

    @Override
    public Optional<ApprovalDecision> findDecision(UUID approvalId) {
        return findRecord(approvalId).flatMap(ApprovalRecord::decisionOptional);
    }

    @Override
    public Optional<ApprovalRecord> findRecord(UUID approvalId) {
        return query(SELECT + " WHERE r.approval_id = ?", approvalId).stream().findFirst();
    }

    @Override
    public Optional<ApprovalRecord> findByActionId(UUID actionId) {
        return query(SELECT + " WHERE r.action_id = ? ORDER BY r.updated_at DESC", actionId).stream().findFirst();
    }

    @Override
    public List<ApprovalRecord> findByRunId(UUID runId) {
        return query(SELECT + " WHERE r.run_id = ? ORDER BY r.created_at", runId);
    }

    @Override
    public List<ApprovalRecord> findPending(String businessUnit) {
        return query(SELECT + " WHERE r.business_unit = ? AND r.status = 'PENDING' ORDER BY r.created_at", businessUnit);
    }

    @Override
    public ApprovalRecord expire(UUID approvalId, Instant now) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE approval_request SET status = 'EXPIRED', updated_at = ? WHERE approval_id = ? AND status = 'PENDING'")) {
            update.setTimestamp(1, Timestamp.from(now));
            update.setObject(2, approvalId);
            update.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to expire approval", e);
        }
        return findRecord(approvalId).orElseThrow(() -> new ApprovalTransitionException("UNKNOWN_APPROVAL", "Approval request does not exist"));
    }

    private List<ApprovalRecord> query(String sql, Object parameter) {
        List<ApprovalRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement select = connection.prepareStatement(sql)) {
            select.setObject(1, parameter);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    records.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read approvals", e);
        }
        return records;
    }

    private static ApprovalRecord map(ResultSet rs) throws SQLException {
        ActionProposal proposal = WorkflowStateJson.readProposal(rs.getString(4));
        ApprovalRequest request = new ApprovalRequest(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), proposal, rs.getString(5),
                rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant());
        ApprovalStatus status = ApprovalStatus.valueOf(rs.getString(8));
        ApprovalDecision decision = null;
        if (rs.getString(10) != null) {
            String edited = rs.getString(14);
            decision = new ApprovalDecision(request.approvalId(), proposal.actionId(), request.runId(), ApprovalDecisionType.valueOf(rs.getString(10)),
                    rs.getString(11), rs.getTimestamp(12).toInstant(), rs.getString(13), edited == null ? null : WorkflowStateJson.readProposal(edited));
        }
        return new ApprovalRecord(request, status, decision, rs.getTimestamp(9).toInstant());
    }
}
