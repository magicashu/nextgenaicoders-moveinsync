package com.moveinsync.mobilitycopilot.audit.adapter.postgres;

import com.moveinsync.mobilitycopilot.approval.adapter.postgres.WorkflowStateJson;
import com.moveinsync.mobilitycopilot.audit.application.AuditLedger;
import com.moveinsync.mobilitycopilot.audit.domain.AuditChain;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.audit.domain.ChainedAuditEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL append-only ledger ({@code audit_event}). Flyway V2 installs a trigger that rejects
 * UPDATE and DELETE, so history is immutable at the database, not only in this class. Each row
 * carries the previous hash for its run; the chain is computed inside one transaction with the
 * run's last row locked to keep appends serial per run.
 */
public class JdbcAuditLedger implements AuditLedger {

    private static final String SELECT = "SELECT sequence_id, event_id, run_id, business_unit, event_type, payload::text, occurred_at, trace_id, previous_hash, event_hash FROM audit_event";

    private final DataSource dataSource;

    public JdbcAuditLedger(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            String previous = AuditChain.GENESIS;
            try (PreparedStatement last = connection.prepareStatement(
                    "SELECT event_hash FROM audit_event WHERE run_id = ? ORDER BY sequence_id DESC LIMIT 1 FOR UPDATE")) {
                last.setObject(1, event.runId());
                try (ResultSet rs = last.executeQuery()) {
                    if (rs.next()) {
                        previous = rs.getString(1);
                    }
                }
            }
            String hash = AuditChain.hash(event, previous);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO audit_event (event_id, run_id, business_unit, event_type, payload, occurred_at, trace_id, previous_hash, event_hash)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)""")) {
                insert.setObject(1, event.eventId());
                insert.setObject(2, event.runId());
                insert.setString(3, event.businessUnit());
                insert.setString(4, event.eventType());
                insert.setString(5, WorkflowStateJson.write(event.payload()));
                insert.setTimestamp(6, Timestamp.from(event.occurredAt()));
                insert.setString(7, event.traceId());
                insert.setString(8, previous);
                insert.setString(9, hash);
                insert.executeUpdate();
            }
            connection.commit();
            return event;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to append audit event", e);
        }
    }

    @Override
    public List<AuditEvent> findByRunId(UUID runId) {
        return chainForRun(runId).stream().map(ChainedAuditEvent::event).toList();
    }

    @Override
    public List<ChainedAuditEvent> chainForRun(UUID runId) {
        return query(SELECT + " WHERE run_id = ? ORDER BY sequence_id", runId, Integer.MAX_VALUE);
    }

    @Override
    public List<ChainedAuditEvent> recentForBusinessUnit(String businessUnit, int limit) {
        return query(SELECT + " WHERE business_unit = ? ORDER BY sequence_id DESC LIMIT ?", businessUnit, limit);
    }

    @Override
    public boolean verifyChain(UUID runId) {
        String previous = AuditChain.GENESIS;
        for (ChainedAuditEvent chained : chainForRun(runId)) {
            if (!chained.previousHash().equals(previous) || !AuditChain.hash(chained.event(), previous).equals(chained.eventHash())) {
                return false;
            }
            previous = chained.eventHash();
        }
        return true;
    }

    private List<ChainedAuditEvent> query(String sql, Object parameter, int limit) {
        List<ChainedAuditEvent> events = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement select = connection.prepareStatement(sql)) {
            select.setObject(1, parameter);
            if (sql.contains("LIMIT ?")) {
                select.setInt(2, limit);
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    AuditEvent event = new AuditEvent(rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                            WorkflowStateJson.readStringMap(rs.getString(6)), rs.getTimestamp(7).toInstant(), rs.getString(8));
                    events.add(new ChainedAuditEvent(rs.getLong(1), event, rs.getString(9), rs.getString(10)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read audit events", e);
        }
        return events;
    }
}
