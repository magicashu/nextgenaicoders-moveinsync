package com.moveinsync.mobilitycopilot.approval.adapter.postgres;

import com.moveinsync.mobilitycopilot.approval.adapter.inmemory.InMemoryWorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCheckpointStore;
import com.moveinsync.mobilitycopilot.workflow.domain.VersionedWorkflowState;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowState;

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
 * PostgreSQL checkpoint repository (table {@code workflow_checkpoint}, Flyway V1) with optimistic
 * versioning: an insert requires {@code NEW_CHECKPOINT}, an update requires the stored version.
 * Plain JDBC keeps the adapter compilable without the postgres Maven profile.
 */
public class JdbcWorkflowCheckpointStore implements WorkflowCheckpointStore {

    private final DataSource dataSource;

    public JdbcWorkflowCheckpointStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public VersionedWorkflowState save(WorkflowState state, long expectedVersion) {
        String json = WorkflowStateJson.write(state);
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            if (expectedVersion == NEW_CHECKPOINT) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO workflow_checkpoint (run_id, business_unit, workflow_step, state_json, version, updated_at) VALUES (?, ?, ?, ?::jsonb, 0, ?) ON CONFLICT (run_id) DO NOTHING")) {
                    insert.setObject(1, state.runId());
                    insert.setString(2, state.tenant().businessUnit());
                    insert.setString(3, state.step().name());
                    insert.setString(4, json);
                    insert.setTimestamp(5, Timestamp.from(now));
                    if (insert.executeUpdate() == 0) {
                        throw new InMemoryWorkflowCheckpointStore.CheckpointConflictException(state.runId(), expectedVersion, currentVersion(connection, state.runId()));
                    }
                }
                return new VersionedWorkflowState(state, 0, now);
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE workflow_checkpoint SET workflow_step = ?, state_json = ?::jsonb, version = version + 1, updated_at = ? WHERE run_id = ? AND version = ?")) {
                update.setString(1, state.step().name());
                update.setString(2, json);
                update.setTimestamp(3, Timestamp.from(now));
                update.setObject(4, state.runId());
                update.setLong(5, expectedVersion);
                if (update.executeUpdate() == 0) {
                    throw new InMemoryWorkflowCheckpointStore.CheckpointConflictException(state.runId(), expectedVersion, currentVersion(connection, state.runId()));
                }
            }
            return new VersionedWorkflowState(state, expectedVersion + 1, now);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save checkpoint for run " + state.runId(), e);
        }
    }

    @Override
    public Optional<VersionedWorkflowState> find(UUID runId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT state_json::text, version, updated_at FROM workflow_checkpoint WHERE run_id = ?")) {
            select.setObject(1, runId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new VersionedWorkflowState(WorkflowStateJson.readState(rs.getString(1)), rs.getLong(2), rs.getTimestamp(3).toInstant()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read checkpoint for run " + runId, e);
        }
    }

    private static long currentVersion(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT version FROM workflow_checkpoint WHERE run_id = ?")) {
            select.setObject(1, runId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getLong(1) : NEW_CHECKPOINT;
            }
        }
    }
}
