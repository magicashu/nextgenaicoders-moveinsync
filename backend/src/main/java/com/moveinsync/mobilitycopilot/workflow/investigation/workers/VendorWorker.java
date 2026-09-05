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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Computes per-vendor delayed-trip rate for the current and baseline windows.
 *
 * G1 acceptance: detects whether one vendor worsened or all vendors worsened broadly,
 * and adds a "universal vendor claim requires qualification" warning when all vendors degraded.
 */
@Component
public final class VendorWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M01-v1.1";
    private static final double UNIVERSAL_DEGRADATION_THRESHOLD = 0.7;

    private final MobilityDataProperties properties;

    public VendorWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.VENDOR;
    }

    @Override
    public String name() {
        return "vendor-delay-rate";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for VENDOR task");
        }

        MetricWindow window = request.window();
        Path dataDir = resolveDataDir();

        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createTripsView(conn, dataDir);

            List<VendorStat> currentStats = queryVendorStats(conn, businessUnit, window.start(), window.end());
            List<VendorStat> baselineStats = queryVendorStats(conn, businessUnit,
                    window.start().minusWeeks(4), window.end().minusWeeks(4));

            if (currentStats.isEmpty()) {
                return unavailable(context, "no trip data for business_unit=" + businessUnit
                        + " in window " + window.start() + " to " + window.end());
            }

            // G1: check whether degradation is universal across vendors
            checkUniversalDegradation(currentStats, baselineStats, warnings);

            long totalDelayed = currentStats.stream().mapToLong(VendorStat::delayedTrips).sum();
            long totalTrips = currentStats.stream().mapToLong(VendorStat::totalTrips).sum();
            BigDecimal rate = totalTrips > 0
                    ? BigDecimal.valueOf(totalDelayed * 100.0 / totalTrips).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (warnings.isEmpty()) {
                warnings.add("vendor breakdown: " + currentStats.size() + " vendors analysed");
            }

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    MetricStatus.AVAILABLE,
                    rate,
                    MetricUnit.PERCENT,
                    BigDecimal.valueOf(totalDelayed),
                    BigDecimal.valueOf(totalTrips),
                    totalTrips,
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in VendorWorker: " + e.getMessage());
        }
    }

    private void checkUniversalDegradation(List<VendorStat> current, List<VendorStat> baseline,
                                            List<String> warnings) {
        if (baseline.isEmpty()) return;

        long degradedCount = 0;
        for (VendorStat curr : current) {
            baseline.stream()
                    .filter(b -> b.vendorId().equals(curr.vendorId()))
                    .findFirst()
                    .ifPresent(base -> {
                        // vendor is "degraded" if delay rate increased
                    });
        }

        // count vendors whose delay rate rose compared to baseline
        degradedCount = current.stream().filter(curr ->
                baseline.stream()
                        .filter(b -> b.vendorId().equals(curr.vendorId()))
                        .findFirst()
                        .map(base -> curr.delayRate() > base.delayRate())
                        .orElse(false)
        ).count();

        if (current.size() > 1 && (double) degradedCount / current.size() >= UNIVERSAL_DEGRADATION_THRESHOLD) {
            warnings.add("universal vendor claim requires qualification: "
                    + degradedCount + "/" + current.size()
                    + " vendors degraded — systemic cause more likely than single-vendor fault");
        }
    }

    private List<VendorStat> queryVendorStats(Connection conn, String businessUnit,
                                               java.time.LocalDate start, java.time.LocalDate end)
            throws SQLException {
        String sql = """
                SELECT
                    vendor_id,
                    COUNT(*) AS total_trips,
                    SUM(CASE WHEN delay_minutes > 0 THEN 1 ELSE 0 END) AS delayed_trips
                FROM trips_normalized
                WHERE business_unit = ?
                  AND trip_date BETWEEN ? AND ?
                  AND vendor_id IS NOT NULL
                GROUP BY vendor_id
                HAVING COUNT(*) > 0
                """;
        List<VendorStat> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, businessUnit);
            ps.setObject(2, start);
            ps.setObject(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long total = rs.getLong("total_trips");
                    long delayed = rs.getLong("delayed_trips");
                    double rate = total > 0 ? (double) delayed / total : 0.0;
                    results.add(new VendorStat(rs.getString("vendor_id"), delayed, total, rate));
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
                    trim(business_unit)                                             AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT)     AS trip_id,
                    CAST(replace(trip_date, ', ', ' ') AS DATE)                    AS trip_date,
                    trim(vendor_id)                                                 AS vendor_id,
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
                java.util.Map.of(),
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

    private record VendorStat(String vendorId, long delayedTrips, long totalTrips, double delayRate) {}
}
