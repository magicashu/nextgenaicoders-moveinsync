package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Small helper that borrows a connection, binds parameters and maps rows. */
final class DuckDbQueries {

    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private DuckDbQueries() {
    }

    static <T> List<T> query(AnalyticsStore store, GovernedSqlTemplate.Rendered rendered, RowMapper<T> mapper) {
        try (Connection connection = store.borrow();
             PreparedStatement statement = connection.prepareStatement(rendered.sql())) {
            for (int i = 0; i < rendered.parameters().size(); i++) {
                statement.setObject(i + 1, rendered.parameters().get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Governed query failed: " + e.getMessage(), e);
        }
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : rs.getLong(column);
    }

    static java.math.BigDecimal nullableDecimal(ResultSet rs, String column, int scale) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return java.math.BigDecimal.valueOf(rs.getDouble(column)).setScale(scale, java.math.RoundingMode.HALF_UP);
    }
}
