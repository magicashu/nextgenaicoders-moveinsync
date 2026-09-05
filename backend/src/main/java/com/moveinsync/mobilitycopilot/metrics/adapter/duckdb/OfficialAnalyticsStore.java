package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.config.AnalyticsProperties;
import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetProfileService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** One immutable analytical snapshot per process; no source scan occurs on the query path. */
@Component
public final class OfficialAnalyticsStore implements AutoCloseable {
    public static final String REGISTRY_VERSION = "M01-M18-v1.1";
    private static final String STORAGE_VERSION = "normalized-v2";
    private final MobilityDataProperties data;
    private final DatasetProfileService profiles;
    private final AnalyticsProperties limits;
    private volatile String version;
    private volatile boolean closed;
    private ArrayBlockingQueue<Connection> connections;

    public OfficialAnalyticsStore(MobilityDataProperties data, DatasetProfileService profiles, AnalyticsProperties limits) {
        this.data = data;
        this.profiles = profiles;
        this.limits = limits;
    }

    public AnalyticsProperties limits() { return limits; }
    public String dataVersion() { initialize(); return version; }

    private synchronized void initialize() {
        if (closed) throw new IllegalStateException("Analytics store is closed");
        if (version != null) return;
        Path source = Path.of(data.directory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(source) && Files.isDirectory(Path.of("..").resolve(data.directory()))) {
            source = Path.of("..").resolve(data.directory()).toAbsolutePath().normalize();
        }
        String pinnedVersion = profiles.profile(source).dataVersion();
        Path database = Path.of(limits.database()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(database.getParent());
            String url = "jdbc:duckdb:" + database;
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.execute("SET memory_limit='" + limits.memoryLimit() + "'");
                statement.execute("SET threads=" + limits.threads());
                statement.execute("SET preserve_insertion_order=false");
                statement.execute("CREATE TABLE IF NOT EXISTS snapshot_metadata(data_version VARCHAR, storage_version VARCHAR)");
                boolean reusable;
                try (ResultSet r = statement.executeQuery("SELECT data_version, storage_version FROM snapshot_metadata")) {
                    reusable = r.next() && pinnedVersion.equals(r.getString(1)) && STORAGE_VERSION.equals(r.getString(2));
                }
                if (!reusable) {
                    connection.setAutoCommit(false);
                    try {
                        String sql;
                        try (var stream = getClass().getResourceAsStream("/sql/schema/02_official_analytics.sql")) {
                            if (stream == null) throw new IllegalStateException("Missing analytics schema");
                            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        sql = sql.replace("$SOURCE", source.toString().replace("'", "''"));
                        for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
                        statement.execute("DELETE FROM snapshot_metadata");
                        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO snapshot_metadata VALUES (?, ?)")) {
                            insert.setString(1, pinnedVersion); insert.setString(2, STORAGE_VERSION); insert.executeUpdate();
                        }
                        connection.commit();
                    } catch (Exception failure) {
                        connection.rollback(); throw failure;
                    } finally { connection.setAutoCommit(true); }
                }
            }
            var pool = new ArrayBlockingQueue<Connection>(limits.connections());
            try {
                for (int i = 0; i < limits.connections(); i++) {
                    Connection c=DriverManager.getConnection(url);
                    pool.add(c);
                    try(Statement s=c.createStatement()) {
                        s.execute("SET memory_limit='"+limits.memoryLimit()+"'");
                        s.execute("SET threads="+limits.threads());
                    }
                }
            } catch (SQLException failure) {
                for (Connection c : pool) c.close();
                throw failure;
            }
            connections = pool;
            version = pinnedVersion;
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Cannot initialize immutable analytical snapshot", exception);
        }
    }

    public <T> T query(String sql, List<?> parameters, RowReader<T> reader) {
        initialize();
        Connection connection = null;
        try {
            connection = connections.poll(limits.queryTimeoutSeconds(), TimeUnit.SECONDS);
            if (connection == null) throw new IllegalStateException("Analytics capacity exceeded");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(limits.queryTimeoutSeconds());
                for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
                try (ResultSet rows = statement.executeQuery()) { return reader.read(rows); }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Analytics query interrupted", exception);
        } catch (SQLException exception) {
            throw new IllegalStateException("Governed analytics query failed", exception);
        } finally {
            if (connection != null) {
                if (closed) { try { connection.close(); } catch (SQLException ignored) { /* shutting down */ } }
                else connections.offer(connection);
            }
        }
    }

    @FunctionalInterface public interface RowReader<T> { T read(ResultSet rows) throws SQLException; }

    @Override @PreDestroy public synchronized void close() {
        closed = true;
        if (connections != null) {
            Connection connection;
            while ((connection = connections.poll()) != null) try { connection.close(); } catch (SQLException ignored) { /* shutting down */ }
        }
    }
}
