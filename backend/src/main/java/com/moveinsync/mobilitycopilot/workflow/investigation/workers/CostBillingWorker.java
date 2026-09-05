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
 * M09/M10 — Median billed cost per trip and cost per billed km from bill_data.
 *
 * Key data quirks handled:
 *   - bill_data.trip_id has NO commas (plain numeric string "1123974") — unlike all other files
 *   - trip_cost is comma-formatted ("1,200") — strip commas before math
 *   - total_trip_km = 0.0 on a meaningful share of rows — cost-per-km is UNAVAILABLE when this applies
 *   - slab_name is null ~20% of the time — included in population, nulls are expected
 *
 * No budget or savings claim is made — only billed-cost measures are returned.
 */
@Component
public final class CostBillingWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M09-v1.1";

    private final MobilityDataProperties properties;

    public CostBillingWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.COST_BILLING;
    }

    @Override
    public String name() {
        return "cost-billing";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for COST_BILLING task");
        }

        MetricWindow window = request.window();
        Map<String, String> filters = request.filters() != null ? request.filters() : Map.of();
        Path dataDir = resolveDataDir();
        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createBillView(conn, dataDir);

            BillStat stat = queryBillStats(conn, businessUnit, window.start(), window.end(), filters);

            if (stat.tripCount() == 0) {
                return unavailable(context, "no billing data for business_unit=" + businessUnit
                        + " window=" + window.start() + " to " + window.end());
            }

            warnings.add("billed trips analysed: " + stat.tripCount());
            warnings.add("median_billed_cost_per_trip=" + stat.medianCost());

            // Cost-per-km: only meaningful when km > 0
            if (stat.zeroKmTrips() > 0) {
                warnings.add("cost_per_km unsupported: " + stat.zeroKmTrips()
                        + " trips have total_trip_km=0 — no budget or savings claim can be made");
            }
            if (stat.costPerKm() != null) {
                warnings.add("mean_cost_per_billed_km=" + stat.costPerKm()
                        + " (computed on " + (stat.tripCount() - stat.zeroKmTrips()) + " trips with km>0)");
            }

            if (!filters.isEmpty()) {
                warnings.add("filters applied: " + filters);
            }

            MetricStatus status = stat.zeroKmTrips() > stat.tripCount() / 2
                    ? MetricStatus.PARTIAL   // majority of rows have zero km — cost-per-km unreliable
                    : MetricStatus.AVAILABLE;

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    status,
                    stat.medianCost(),
                    MetricUnit.CURRENCY,
                    stat.totalCost(),
                    BigDecimal.valueOf(stat.tripCount()),
                    stat.tripCount(),
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in CostBillingWorker: " + e.getMessage());
        }
    }

    private BillStat queryBillStats(Connection conn, String businessUnit,
                                     LocalDate start, LocalDate end,
                                     Map<String, String> filters) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS trip_count,
                    MEDIAN(trip_cost_numeric) AS median_cost,
                    AVG(CASE WHEN total_trip_km > 0 THEN trip_cost_numeric / total_trip_km END) AS cost_per_km,
                    SUM(trip_cost_numeric) AS total_cost,
                    SUM(CASE WHEN total_trip_km = 0 OR total_trip_km IS NULL THEN 1 ELSE 0 END) AS zero_km_trips
                FROM bill_normalized
                WHERE business_unit = ?
                  AND cycle_start_date BETWEEN ? AND ?
                  AND trip_cost_numeric IS NOT NULL
                """);

        List<Object> params = new ArrayList<>();
        params.add(businessUnit);
        params.add(start);
        params.add(end);

        if (filters.containsKey("vendor")) {
            sql.append("  AND vendor = ?\n");
            params.add(filters.get("vendor"));
        }
        if (filters.containsKey("office")) {
            sql.append("  AND office = ?\n");
            params.add(filters.get("office"));
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long tripCount = rs.getLong("trip_count");
                    if (tripCount == 0) return new BillStat(0, null, null, null, 0);

                    BigDecimal median = rs.getBigDecimal("median_cost");
                    if (median != null) median = median.setScale(2, RoundingMode.HALF_UP);

                    double costPerKmRaw = rs.getDouble("cost_per_km");
                    BigDecimal costPerKm = rs.wasNull() ? null
                            : BigDecimal.valueOf(costPerKmRaw).setScale(4, RoundingMode.HALF_UP);

                    BigDecimal totalCost = rs.getBigDecimal("total_cost");
                    if (totalCost != null) totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);

                    long zeroKm = rs.getLong("zero_km_trips");

                    return new BillStat(tripCount, median, costPerKm, totalCost, zeroKm);
                }
            }
        }
        return new BillStat(0, null, null, null, 0);
    }

    private void createBillView(Connection conn, Path dataDir) throws SQLException {
        // bill_data: trip_id is plain numeric string (no commas), unlike all other files
        // trip_cost is comma-formatted — strip before cast
        // cycle_start date format: "May 1, 2026, 12:00 AM" — extract date portion
        String billFile = dataDir.resolve("bill_data.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW bill_normalized AS
                SELECT
                    trim(business_unit)                                                       AS business_unit,
                    CAST(trip_id AS BIGINT)                                                   AS trip_id,
                    trim(office)                                                              AS office,
                    trim(vendor)                                                              AS vendor,
                    CAST(strptime(
                             trim(regexp_replace(cycle_start, ',\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)', '')),
                             '%%B %%e, %%Y'
                         ) AS DATE)                                                           AS cycle_start_date,
                    total_trip_km,
                    TRY_CAST(replace(CAST(trip_cost AS VARCHAR), ',', '') AS DECIMAL(12,2))  AS trip_cost_numeric
                FROM read_csv_auto('%s', header = true)
                """.formatted(billFile);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private MetricEvidence unavailable(RunContext context, String reason) {
        MetricRequest req = new MetricRequest(
                context.tenant(), MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP,
                MetricRequest.Measure.VALUE,
                new MetricWindow(context.asOfDate().minusWeeks(1), context.asOfDate()),
                Map.of(), context.versions().data()
        );
        return new MetricEvidence(
                UUID.randomUUID().toString(), req, MetricStatus.UNAVAILABLE,
                null, MetricUnit.CURRENCY, null, null, 0,
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

    private record BillStat(long tripCount, BigDecimal medianCost, BigDecimal costPerKm,
                             BigDecimal totalCost, long zeroKmTrips) {}
}
