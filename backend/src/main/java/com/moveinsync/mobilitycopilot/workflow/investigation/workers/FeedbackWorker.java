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
 * M11/M12 — Driver rating and safety rating from trip_feedback.
 *
 * Rating-zero policy: rating = 0 means "unrated" for legacy rows — excluded from mean.
 * Coverage caveat (G2): when fewer than 20% of trips have feedback, a warning is added
 * and status is set to PARTIAL to signal limited confidence.
 *
 * Optional filter via task.requests().get(0).filters(): "trip_type" (LOGIN/LOGOUT).
 * driver_rating and safety_rating are returned as separate warnings; the primary
 * value is driver_rating mean (M11/M12 family).
 */
@Component
public final class FeedbackWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M12-v1.1";
    private static final double LOW_COVERAGE_THRESHOLD = 0.20;

    private final MobilityDataProperties properties;

    public FeedbackWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.FEEDBACK;
    }

    @Override
    public String name() {
        return "feedback-ratings";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for FEEDBACK task");
        }

        MetricWindow window = request.window();
        Map<String, String> filters = request.filters() != null ? request.filters() : Map.of();
        Path dataDir = resolveDataDir();
        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createFeedbackView(conn, dataDir);
            createTripsView(conn, dataDir);

            RatingStat stat = queryRatings(conn, businessUnit, window.start(), window.end(), filters);

            if (stat.ratedLegs() == 0) {
                return unavailable(context, "no feedback data for business_unit=" + businessUnit
                        + " window=" + window.start() + " to " + window.end());
            }

            // Coverage check against total trips in the same window
            long totalTrips = queryTotalTrips(conn, businessUnit, window.start(), window.end());
            double coverage = totalTrips > 0 ? (double) stat.ratedLegs() / totalTrips : 0.0;
            boolean lowCoverage = coverage < LOW_COVERAGE_THRESHOLD;

            warnings.add(String.format("feedback coverage: %.1f%% (%d rated / %d trips)",
                    coverage * 100, stat.ratedLegs(), totalTrips));

            if (lowCoverage) {
                warnings.add("low feedback coverage (<20%) — ratings may not represent fleet-wide experience");
            }

            if (stat.meanSafetyRating() != null) {
                warnings.add("mean safety_rating=" + stat.meanSafetyRating()
                        + " (rated legs=" + stat.ratedLegs() + ")");
            }
            if (stat.meanRouteRating() != null) {
                warnings.add("mean route_rating=" + stat.meanRouteRating());
            }

            MetricStatus status = lowCoverage ? MetricStatus.PARTIAL : MetricStatus.AVAILABLE;

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    status,
                    stat.meanDriverRating(),
                    MetricUnit.RATING,
                    BigDecimal.valueOf(stat.ratedLegs()),
                    BigDecimal.valueOf(totalTrips),
                    stat.ratedLegs(),
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in FeedbackWorker: " + e.getMessage());
        }
    }

    private RatingStat queryRatings(Connection conn, String businessUnit,
                                     LocalDate start, LocalDate end,
                                     Map<String, String> filters) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS rated_legs,
                    AVG(CASE WHEN driver_rating > 0 THEN driver_rating END) AS mean_driver,
                    AVG(CASE WHEN safety_rating > 0 THEN safety_rating END) AS mean_safety,
                    AVG(CASE WHEN route_rating  > 0 THEN route_rating  END) AS mean_route
                FROM feedback_normalized
                WHERE business_unit = ?
                  AND trip_date BETWEEN ? AND ?
                  AND driver_rating > 0
                """);

        List<Object> params = new ArrayList<>();
        params.add(businessUnit);
        params.add(start);
        params.add(end);

        if (filters.containsKey("trip_type")) {
            sql.append("  AND trip_type = ?\n");
            params.add(filters.get("trip_type"));
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long ratedLegs = rs.getLong("rated_legs");
                    double meanDriver = rs.getDouble("mean_driver");
                    double meanSafety = rs.getDouble("mean_safety");
                    double meanRoute = rs.getDouble("mean_route");
                    return new RatingStat(
                            ratedLegs,
                            ratedLegs > 0 ? BigDecimal.valueOf(meanDriver).setScale(2, RoundingMode.HALF_UP) : null,
                            ratedLegs > 0 ? BigDecimal.valueOf(meanSafety).setScale(2, RoundingMode.HALF_UP) : null,
                            ratedLegs > 0 ? BigDecimal.valueOf(meanRoute).setScale(2, RoundingMode.HALF_UP) : null
                    );
                }
            }
        }
        return new RatingStat(0, null, null, null);
    }

    private long queryTotalTrips(Connection conn, String businessUnit,
                                  LocalDate start, LocalDate end) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM trips_normalized
                WHERE business_unit = ? AND trip_date BETWEEN ? AND ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, businessUnit);
            ps.setObject(2, start);
            ps.setObject(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("cnt") : 0;
            }
        }
    }

    private void createFeedbackView(Connection conn, Path dataDir) throws SQLException {
        // trip_feedback: trip_date format is "June 3, 2026, 11:00 AM" — extract date portion only
        String feedbackFile = dataDir.resolve("trip_feedback.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW feedback_normalized AS
                SELECT
                    trim(business_unit)                                          AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT)   AS trip_id,
                    trim(trip_type)                                              AS trip_type,
                    CAST(SUBSTR(trip_date, 1, POSITION(',' IN trip_date) - 1)
                         || SUBSTR(trip_date, POSITION(',' IN trip_date) + 1,
                                  POSITION(',' IN SUBSTR(trip_date, POSITION(',' IN trip_date) + 1)))
                         AS DATE)                                                AS trip_date,
                    CAST(replace(CAST(stwid AS VARCHAR), ',', '') AS BIGINT)     AS stwid,
                    driver_rating,
                    safety_rating,
                    route_rating,
                    cab_rating,
                    marshal_rating
                FROM read_csv_auto('%s', header = true)
                """.formatted(feedbackFile);

        // DuckDB date parsing for "June 3, 2026, 11:00 AM" is complex; use strptime directly
        String sqlSimpler = """
                CREATE OR REPLACE TEMP VIEW feedback_normalized AS
                SELECT
                    trim(business_unit)                                                 AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT)          AS trip_id,
                    trim(trip_type)                                                     AS trip_type,
                    CAST(strptime(trim(regexp_replace(trip_date, ',\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)', '')),
                                  '%%B %%e, %%Y') AS DATE)                             AS trip_date,
                    CAST(replace(CAST(stwid AS VARCHAR), ',', '') AS BIGINT)            AS stwid,
                    CAST(driver_rating AS INTEGER)                                      AS driver_rating,
                    CAST(safety_rating AS INTEGER)                                      AS safety_rating,
                    CAST(route_rating  AS INTEGER)                                      AS route_rating,
                    CAST(cab_rating    AS INTEGER)                                      AS cab_rating,
                    CAST(marshal_rating AS INTEGER)                                     AS marshal_rating
                FROM read_csv_auto('%s', header = true)
                """.formatted(feedbackFile);
        try (Statement st = conn.createStatement()) {
            st.execute(sqlSimpler);
        }
    }

    private void createTripsView(Connection conn, Path dataDir) throws SQLException {
        String glob = dataDir.resolve("Ride_data _trip-*.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW trips_normalized AS
                SELECT
                    trim(business_unit)                                             AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT)      AS trip_id,
                    CAST(replace(trip_date, ', ', ' ') AS DATE)                     AS trip_date
                FROM read_csv_auto('%s', union_by_name = true, header = true)
                """.formatted(glob);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private MetricEvidence unavailable(RunContext context, String reason) {
        MetricRequest req = new MetricRequest(
                context.tenant(), MetricId.M12_MEAN_DRIVER_SAFETY_RATING,
                MetricRequest.Measure.DRIVER_RATING,
                new MetricWindow(context.asOfDate().minusWeeks(1), context.asOfDate()),
                Map.of(), context.versions().data()
        );
        return new MetricEvidence(
                UUID.randomUUID().toString(), req, MetricStatus.UNAVAILABLE,
                null, MetricUnit.RATING, null, null, 0,
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

    private record RatingStat(long ratedLegs, BigDecimal meanDriverRating,
                               BigDecimal meanSafetyRating, BigDecimal meanRouteRating) {}
}
