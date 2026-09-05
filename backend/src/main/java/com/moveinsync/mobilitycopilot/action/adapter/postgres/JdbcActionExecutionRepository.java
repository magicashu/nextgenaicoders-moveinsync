package com.moveinsync.mobilitycopilot.action.adapter.postgres;

import com.moveinsync.mobilitycopilot.action.application.ActionExecutionRepository;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionRecord;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL idempotency table ({@code action_execution}, unique on idempotency_key and action_id).
 * The claim is a single INSERT ... ON CONFLICT DO NOTHING, which is atomic across processes.
 */
public class JdbcActionExecutionRepository implements ActionExecutionRepository {

    private static final String SELECT = "SELECT idempotency_key, action_id, run_id, business_unit, action_type, status, evidence_version, claimed_at, revalidated_at, executed_at, external_reference, message, attempts FROM action_execution";

    private final DataSource dataSource;

    public JdbcActionExecutionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ClaimResult claim(ActionExecutionRecord candidate) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO action_execution (action_id, run_id, idempotency_key, business_unit, action_type, status, evidence_version, claimed_at, revalidated_at, attempts)
                    VALUES (?, ?, ?, ?, ?, 'EXECUTING', ?, ?, ?, 1) ON CONFLICT DO NOTHING""")) {
                insert.setObject(1, candidate.actionId());
                insert.setObject(2, candidate.runId());
                insert.setString(3, candidate.idempotencyKey());
                insert.setString(4, candidate.businessUnit());
                insert.setString(5, candidate.type().name());
                insert.setString(6, candidate.evidenceVersion());
                insert.setTimestamp(7, Timestamp.from(candidate.claimedAt()));
                insert.setTimestamp(8, candidate.revalidatedAt() == null ? null : Timestamp.from(candidate.revalidatedAt()));
                if (insert.executeUpdate() == 1) {
                    return new ClaimResult(candidate, true);
                }
            }
            ActionExecutionRecord existing = find(connection, candidate.idempotencyKey())
                    .or(() -> findByActionId(candidate.actionId()))
                    .orElseThrow(() -> new IllegalStateException("Claim conflict without a readable row for " + candidate.idempotencyKey()));
            if (existing.status() == ActionStatus.APPROVED_NOT_EXECUTED && existing.idempotencyKey().equals(candidate.idempotencyKey())) {
                try (PreparedStatement retry = connection.prepareStatement(
                        "UPDATE action_execution SET status = 'EXECUTING', attempts = attempts + 1, revalidated_at = ? WHERE idempotency_key = ? AND status = 'APPROVED_NOT_EXECUTED'")) {
                    retry.setTimestamp(1, candidate.revalidatedAt() == null ? null : Timestamp.from(candidate.revalidatedAt()));
                    retry.setString(2, candidate.idempotencyKey());
                    retry.executeUpdate();
                }
            }
            return new ClaimResult(existing, false);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to claim idempotency key", e);
        }
    }

    @Override
    public ActionExecutionRecord complete(String idempotencyKey, ActionStatus status, Instant at, String externalReference, String message) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE action_execution SET status = ?, executed_at = ?, external_reference = ?, message = ? WHERE idempotency_key = ?")) {
            update.setString(1, status.name());
            update.setTimestamp(2, status == ActionStatus.EXECUTED ? Timestamp.from(at) : null);
            update.setString(3, externalReference);
            update.setString(4, message);
            update.setString(5, idempotencyKey);
            update.executeUpdate();
            return find(connection, idempotencyKey).orElseThrow(() -> new IllegalStateException("Unknown idempotency key " + idempotencyKey));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to complete action execution", e);
        }
    }

    @Override
    public Optional<ActionExecutionRecord> find(String idempotencyKey) {
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, idempotencyKey);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read action execution", e);
        }
    }

    @Override
    public Optional<ActionExecutionRecord> findByActionId(UUID actionId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(SELECT + " WHERE action_id = ?")) {
            select.setObject(1, actionId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read action execution", e);
        }
    }

    private static Optional<ActionExecutionRecord> find(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT + " WHERE idempotency_key = ?")) {
            select.setString(1, idempotencyKey);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static ActionExecutionRecord map(ResultSet rs) throws SQLException {
        return new ActionExecutionRecord(rs.getString(1), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4),
                ActionType.valueOf(rs.getString(5)), ActionStatus.valueOf(rs.getString(6)), rs.getString(7), rs.getTimestamp(8).toInstant(),
                rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(),
                rs.getString(11), rs.getString(12), rs.getInt(13));
    }
}
