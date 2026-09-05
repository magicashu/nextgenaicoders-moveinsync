package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.RegisterableWorker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * M06 — No-show rate using the approved emp_data population.
 *
 * Denominator = eligible employee-leg records (stwid != 0) for the scoped window.
 * Never uses ride_data_trip.noshow_cnt as denominator — that would conflate
 * trip-level aggregates with individual leg eligibility.
 *
 * Optional filters: "office", "shift_type", "product_type" via task.requests().get(0).filters().
 */
@Component
public final class NoShowRosterWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M06-v1.1";

    private final MobilityDataProperties properties;

    public NoShowRosterWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.NO_SHOW_ROSTER;
    }

    @Override
    public String name() {
        return "no-show-roster";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for NO_SHOW_ROSTER task");
        }

        MetricWindow window = request.window();
        Map<String, String> filters = request.filters() != null ? request.filters() : Map.of();
        Path dataDir = resolveDataDir();
        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createEmpView(conn, dataDir);

            NoShowStat stat = queryNoShowStat(conn, businessUnit, window.start(), window.end(), filters);

            if (stat.eligibleLegs() == 0) {
                return unavailable(context, "no eligible employee legs for business_unit=" + businessUnit
                        + " window=" + window.start() + " to " + window.end());
            }

            BigDecimal noShowRate = BigDecimal.valueOf(stat.noShows() * 100.0 / stat.eligibleLegs())
                    .setScale(2, RoundingMode.HALF_UP);

            warnings.add("eligible legs (stwid != 0): " + stat.eligibleLegs());
            warnings.add("no-shows: " + stat.noShows());

            if (!filters.isEmpty()) {
                warnings.add("filters applied: " + filters);
            }

            // Surface breakdown by not_boarding_reason when no-show count is material
            if (stat.noShows() > 0) {
                Map<String, Long> breakdown = queryNoShowBreakdown(conn, businessUnit,
                        window.start(), window.end(), filters);
                breakdown.forEach((reason, count) ->
                        warnings.add("not_boarding_reason=" + reason + " count=" + count));
            }

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    MetricStatus.AVAILABLE,
                    noShowRate,
                    MetricUnit.PERCENT,
                    BigDecimal.valueOf(stat.noShows()),
                    BigDecimal.valueOf(stat.eligibleLegs()),
                    stat.eligibleLegs(),
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in NoShowRosterWorker: " + e.getMessage());
        }
    }

    private NoShowStat queryNoShowStat(Connection conn, String businessUnit,
                                        LocalDate start, LocalDate end,
                                        Map<String, String> filters) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS eligible_legs,
                    SUM(CASE WHEN is_no_show = true THEN 1 ELSE 0 END) AS no_shows
                FROM emp_normalized
                WHERE business_unit = ?
                  AND trip_date BETWEEN ? AND ?
                  AND stwid != 0
                """);

        List<Object> params = new ArrayList<>();
        params.add(businessUnit);
        params.add(start);
        params.add(end);

        appendFilters(sql, params, filters);

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new NoShowStat(rs.getLong("eligible_legs"), rs.getLong("no_shows"));
                }
            }
        }
        return new NoShowStat(0, 0);
    }

    private Map<String, Long> queryNoShowBreakdown(Connection conn, String businessUnit,
                                                    LocalDate start, LocalDate end,
                                                    Map<String, String> filters) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(not_boarding_reason, 'UNKNOWN') AS reason,
                    COUNT(*) AS cnt
                FROM emp_normalized
                WHERE business_unit = ?
                  AND trip_date BETWEEN ? AND ?
                  AND stwid != 0
                  AND is_no_show = true
                """);

        List<Object> params = new ArrayList<>();
        params.add(businessUnit);
        params.add(start);
        params.add(end);

        appendFilters(sql, params, filters);
        sql.append(" GROUP BY reason ORDER BY cnt DESC");

        Map<String, Long> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("reason"), rs.getLong("cnt"));
            }
        }
        return result;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, Map<String, String> filters) {
        if (filters.containsKey("office")) {
            sql.append("  AND office = ?\n");
            params.add(filters.get("office"));
        }
        if (filters.containsKey("shift_type")) {
            sql.append("  AND shift_type = ?\n");
            params.add(filters.get("shift_type"));
        }
        if (filters.containsKey("product_type")) {
            sql.append("  AND product_type = ?\n");
            params.add(filters.get("product_type"));
        }
    }

    private void createEmpView(Connection conn, Path dataDir) throws SQLException {
        // emp_data: trip_id and stwid are already int64, trip_date is ISO YYYY-MM-DD
        // planned_km / traveled_km: negatives are invalid — excluded from distance calcs
        // but we do NOT drop rows here; is_no_show and boarding_status are still valid
        String empFile = dataDir.resolve("emp_Data.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW emp_normalized AS
                SELECT
                    trim(business_unit)          AS business_unit,
                    CAST(trip_id AS BIGINT)       AS trip_id,
                    CAST(trip_date AS DATE)        AS trip_date,
                    CAST(stwid AS BIGINT)          AS stwid,
                    trim(office)                  AS office,
                    trim(shift_type)              AS shift_type,
                    trim(product_type)            AS product_type,
                    CAST(is_no_show AS BOOLEAN)   AS is_no_show,
                    trim(not_boarding_reason)     AS not_boarding_reason,
                    trim(boarding_status)         AS boarding_status
                FROM read_csv_auto('%s', header = true)
                """.formatted(empFile);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private MetricEvidence unavailable(RunContext context, String reason) {
        MetricRequest req = new MetricRequest(
                context.tenant(), MetricId.M06_NO_SHOW_RATE,
                MetricRequest.Measure.VALUE,
                new MetricWindow(context.asOfDate().minusWeeks(1), context.asOfDate()),
                Map.of(), context.versions().data()
        );
        return new MetricEvidence(
                UUID.randomUUID().toString(), req, MetricStatus.UNAVAILABLE,
                null, MetricUnit.PERCENT, null, null, 0,
                METRIC_VERSION, "unavailable", List.of(reason)
        );
    }

    private Path resolveDataDir() {
        Path p = Path.of(properties.directory());
        if (Files.isDirectory(p)) return p;
        Path alt = Path.of("..").resolve(p).normalize();
        if (Files.isDirectory(alt)) return alt;
        throw new IllegalStateException("Dataset directory not found: " + p);
    }

    private record NoShowStat(long eligibleLegs, long noShows) {}
}
