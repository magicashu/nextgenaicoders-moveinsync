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
 * M03 — Delay reason mix by category (TRAFFIC, DRIVER, EMPLOYEE, NODELAY).
 *
 * Computes the percentage contribution of each delay_reason to total delayed trips
 * for the scoped business unit and window. Reason categories are data labels,
 * not proof of root cause — this is reflected in the warnings.
 */
@Component
public final class DelayReasonWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M03-v1.1";

    private final MobilityDataProperties properties;

    public DelayReasonWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.DELAY_REASON;
    }

    @Override
    public String name() {
        return "delay-reason-mix";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for DELAY_REASON task");
        }

        MetricWindow window = request.window();
        Path dataDir = resolveDataDir();
        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createTripsView(conn, dataDir);

            List<ReasonStat> stats = queryReasonStats(conn, businessUnit, window.start(), window.end());

            if (stats.isEmpty()) {
                return unavailable(context, "no trip data for business_unit=" + businessUnit
                        + " window=" + window.start() + " to " + window.end());
            }

            long totalTrips = stats.stream().mapToLong(ReasonStat::tripCount).sum();
            long delayedTrips = stats.stream()
                    .filter(r -> !"NODELAY".equalsIgnoreCase(r.reason()))
                    .mapToLong(ReasonStat::tripCount)
                    .sum();

            // Build breakdown warning for each reason
            for (ReasonStat s : stats) {
                if (!"NODELAY".equalsIgnoreCase(s.reason())) {
                    BigDecimal pct = totalTrips > 0
                            ? BigDecimal.valueOf(s.tripCount() * 100.0 / totalTrips)
                                    .setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    warnings.add("delay_reason=" + s.reason() + " trips=" + s.tripCount()
                            + " (" + pct + "% of all trips)");
                }
            }

            warnings.add("reason categories are recorded labels, not verified root causes");

            BigDecimal delayedRate = totalTrips > 0
                    ? BigDecimal.valueOf(delayedTrips * 100.0 / totalTrips).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    MetricStatus.AVAILABLE,
                    delayedRate,
                    MetricUnit.PERCENT,
                    BigDecimal.valueOf(delayedTrips),
                    BigDecimal.valueOf(totalTrips),
                    totalTrips,
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in DelayReasonWorker: " + e.getMessage());
        }
    }

    private List<ReasonStat> queryReasonStats(Connection conn, String businessUnit,
                                               LocalDate start, LocalDate end) throws SQLException {
        String sql = """
                SELECT
                    UPPER(COALESCE(TRIM(delay_reason), 'UNKNOWN')) AS reason,
                    COUNT(*) AS trip_count
                FROM trips_normalized
                WHERE business_unit = ?
                  AND trip_date BETWEEN ? AND ?
                GROUP BY reason
                ORDER BY trip_count DESC
                """;
        List<ReasonStat> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, businessUnit);
            ps.setObject(2, start);
            ps.setObject(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ReasonStat(rs.getString("reason"), rs.getLong("trip_count")));
                }
            }
        }
        return results;
    }

    private void createTripsView(Connection conn, Path dataDir) throws SQLException {
        String glob = dataDir.resolve("Ride_data _trip-*.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW trips_normalized AS
                SELECT
                    trim(business_unit)                                              AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT)      AS trip_id,
                    CAST(replace(trip_date, ', ', ' ') AS DATE)                     AS trip_date,
                    delay_reason,
                    CAST(replace(CAST(delay_minutes AS VARCHAR), ',', '') AS DOUBLE) AS delay_minutes
                FROM read_csv_auto('%s', union_by_name = true, header = true)
                """.formatted(glob);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private MetricEvidence unavailable(RunContext context, String reason) {
        MetricRequest req = new MetricRequest(
                context.tenant(), MetricId.M03_DELAY_REASON_MIX,
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

    private record ReasonStat(String reason, long tripCount) {}
}
