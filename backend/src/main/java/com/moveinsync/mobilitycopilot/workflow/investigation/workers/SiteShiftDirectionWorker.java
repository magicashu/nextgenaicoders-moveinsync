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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes delayed-trip concentration by office (site), shift_type, and trip_direction.
 *
 * G1 acceptance: identifies WHERE and WHEN delay increase is concentrated.
 * Supports optional dimension filter via task.requests().get(0).filters():
 *   "office", "shift_type", "trip_direction" keys are honoured.
 */
@Component
public final class SiteShiftDirectionWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M01-v1.1";
    private static final int TOP_N = 5;

    private final MobilityDataProperties properties;

    public SiteShiftDirectionWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.SITE_SHIFT_DIRECTION;
    }

    @Override
    public String name() {
        return "site-shift-direction-delay";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for SITE_SHIFT_DIRECTION task");
        }

        MetricWindow window = request.window();
        Map<String, String> filters = request.filters() != null ? request.filters() : Map.of();
        Path dataDir = resolveDataDir();
        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createTripsView(conn, dataDir);

            List<SlotStat> currentSlots = querySlots(conn, businessUnit, window.start(), window.end(), filters);

            if (currentSlots.isEmpty()) {
                return unavailable(context, "no trip data for business_unit=" + businessUnit
                        + " in window " + window.start() + " to " + window.end());
            }

            // Top-N concentration finding
            List<SlotStat> topSlots = currentSlots.stream()
                    .sorted((a, b) -> Double.compare(b.delayRate(), a.delayRate()))
                    .limit(TOP_N)
                    .toList();

            long totalDelayed = currentSlots.stream().mapToLong(SlotStat::delayedTrips).sum();
            long totalTrips = currentSlots.stream().mapToLong(SlotStat::totalTrips).sum();
            BigDecimal overallRate = totalTrips > 0
                    ? BigDecimal.valueOf(totalDelayed * 100.0 / totalTrips).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            warnings.add("top concentration: " + topSlots.stream()
                    .map(s -> s.office() + "/" + s.shiftType() + "/" + s.direction()
                            + "=" + String.format("%.1f%%", s.delayRate() * 100))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none"));

            if (filters.isEmpty()) {
                warnings.add("analysed " + currentSlots.size() + " office/shift/direction combinations");
            }

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    MetricStatus.AVAILABLE,
                    overallRate,
                    MetricUnit.PERCENT,
                    BigDecimal.valueOf(totalDelayed),
                    BigDecimal.valueOf(totalTrips),
                    totalTrips,
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in SiteShiftDirectionWorker: " + e.getMessage());
        }
    }

    private List<SlotStat> querySlots(Connection conn, String businessUnit,
                                       LocalDate start, LocalDate end,
                                       Map<String, String> filters) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    office,
                    shift_type,
                    trip_direction,
                    COUNT(*) AS total_trips,
                    SUM(CASE WHEN delay_minutes > 0 THEN 1 ELSE 0 END) AS delayed_trips
                FROM trips_normalized
                WHERE business_unit = ?
                  AND trip_date BETWEEN ? AND ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(businessUnit);
        params.add(start);
        params.add(end);

        if (filters.containsKey("office")) {
            sql.append("  AND office = ?\n");
            params.add(filters.get("office"));
        }
        if (filters.containsKey("shift_type")) {
            sql.append("  AND shift_type = ?\n");
            params.add(filters.get("shift_type"));
        }
        if (filters.containsKey("trip_direction")) {
            sql.append("  AND trip_direction = ?\n");
            params.add(filters.get("trip_direction"));
        }

        sql.append("""
                GROUP BY office, shift_type, trip_direction
                HAVING COUNT(*) > 0
                """);

        List<SlotStat> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long total = rs.getLong("total_trips");
                    long delayed = rs.getLong("delayed_trips");
                    double rate = total > 0 ? (double) delayed / total : 0.0;
                    results.add(new SlotStat(
                            rs.getString("office"),
                            rs.getString("shift_type"),
                            rs.getString("trip_direction"),
                            delayed, total, rate));
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
                    trim(office)                                                     AS office,
                    trim(shift_type)                                                 AS shift_type,
                    trim(trip_direction)                                             AS trip_direction,
                    CAST(replace(CAST(delay_minutes AS VARCHAR), ',', '') AS DOUBLE) AS delay_minutes
                FROM read_csv_auto('%s', union_by_name = true, header = true)
                """.formatted(glob);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private MetricEvidence unavailable(RunContext context, String reason) {
        MetricRequest req = new MetricRequest(
                context.tenant(),
                MetricId.M01_DELAYED_TRIP_RATE,
                MetricRequest.Measure.VALUE,
                new MetricWindow(context.asOfDate().minusWeeks(1), context.asOfDate()),
                Map.of(),
                context.versions().data()
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

    private record SlotStat(String office, String shiftType, String direction,
                             long delayedTrips, long totalTrips, double delayRate) {}
}
