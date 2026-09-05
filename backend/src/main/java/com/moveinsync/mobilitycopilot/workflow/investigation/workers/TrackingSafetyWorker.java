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
 * M13/M14/M16 — Alert rate, severe alert rate, tracking gap rate from alerts_data.
 *
 * G3 acceptance rule: a spike in DEVICE_NOT_REACHABLE or SUPPLEMENTARY_ALERT is a
 * recording/tracking regime change — NOT an operational safety incident.
 * When regime-change signals dominate the window, status is set to PARTIAL and
 * a "recording regime change" warning is added. The result must never be used
 * to justify an operational escalation in this case.
 *
 * Data cleaning applied:
 *   - severity == "False" rows → treated as null severity, excluded from Sev counts
 *   - stwid == "0" → trip-level alerts, included in rate but excluded from per-rider analysis
 */
@Component
public final class TrackingSafetyWorker implements RegisterableWorker<MetricEvidence> {

    private static final String METRIC_VERSION = "M13-v1.1";

    // Event types that indicate a tracking/recording regime change rather than an operational incident
    private static final Set<String> REGIME_CHANGE_EVENT_TYPES = Set.of(
            "DEVICE_NOT_REACHABLE",
            "SUPPLEMENTARY_ALERT"
    );

    // When regime-change events are >= this fraction of all alerts, flag regime change
    private static final double REGIME_CHANGE_DOMINANCE_THRESHOLD = 0.50;

    private final MobilityDataProperties properties;

    public TrackingSafetyWorker(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkerType workerType() {
        return WorkerType.TRACKING_SAFETY;
    }

    @Override
    public String name() {
        return "tracking-safety-alerts";
    }

    @Override
    public MetricEvidence execute(RunContext context, InvestigationTask task) {
        String businessUnit = context.tenant().businessUnit();
        MetricRequest request = task.requests().isEmpty() ? null : task.requests().get(0);
        if (request == null) {
            return unavailable(context, "no MetricRequest provided for TRACKING_SAFETY task");
        }

        MetricWindow window = request.window();
        Path dataDir = resolveDataDir();
        List<String> warnings = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createAlertsView(conn, dataDir);

            AlertStat stat = queryAlertStats(conn, businessUnit, window.start(), window.end());
            long totalTrips = queryTotalTrips(conn, businessUnit, window.start(), window.end(), dataDir);

            if (stat.totalAlerts() == 0) {
                warnings.add("no alerts found for business_unit=" + businessUnit
                        + " window=" + window.start() + " to " + window.end());
                return new MetricEvidence(
                        UUID.randomUUID().toString(), request, MetricStatus.AVAILABLE,
                        BigDecimal.ZERO, MetricUnit.PER_THOUSAND_TRIPS,
                        BigDecimal.ZERO, BigDecimal.valueOf(totalTrips), totalTrips,
                        METRIC_VERSION, "csv:" + dataDir.toAbsolutePath(), List.of()
                );
            }

            // G3: detect regime change dominance
            boolean regimeChange = isRegimeChange(stat);
            if (regimeChange) {
                warnings.add("recording regime change detected: DEVICE_NOT_REACHABLE or SUPPLEMENTARY_ALERT"
                        + " events dominate (" + stat.regimeChangeAlerts() + "/" + stat.totalAlerts()
                        + ") — do NOT escalate as operational safety incident");
            }

            // Alert rate per 1,000 trips (M13)
            BigDecimal alertRate = totalTrips > 0
                    ? BigDecimal.valueOf(stat.totalAlerts() * 1000.0 / totalTrips)
                            .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Severe alert rate (M14) — Sev-1 and Sev-2 only, excluding "False" rows
            BigDecimal severeRate = totalTrips > 0
                    ? BigDecimal.valueOf(stat.severeAlerts() * 1000.0 / totalTrips)
                            .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            warnings.add("alert_rate=" + alertRate + " per 1,000 trips");
            warnings.add("severe_alert_rate (Sev-1/2)=" + severeRate + " per 1,000 trips");
            warnings.add("total_alerts=" + stat.totalAlerts()
                    + " open=" + stat.openAlerts() + " closed=" + stat.closedAlerts());

            if (stat.nullSeverityAlerts() > 0) {
                warnings.add("null/invalid severity rows excluded from Sev counts: "
                        + stat.nullSeverityAlerts());
            }

            // Status is PARTIAL when regime change dominates — evidence exists but must not drive escalation
            MetricStatus status = regimeChange ? MetricStatus.PARTIAL : MetricStatus.AVAILABLE;

            return new MetricEvidence(
                    UUID.randomUUID().toString(),
                    request,
                    status,
                    alertRate,
                    MetricUnit.PER_THOUSAND_TRIPS,
                    BigDecimal.valueOf(stat.totalAlerts()),
                    BigDecimal.valueOf(totalTrips),
                    totalTrips,
                    METRIC_VERSION,
                    "csv:" + dataDir.toAbsolutePath(),
                    List.copyOf(warnings)
            );

        } catch (SQLException e) {
            return unavailable(context, "DuckDB error in TrackingSafetyWorker: " + e.getMessage());
        }
    }

    private boolean isRegimeChange(AlertStat stat) {
        if (stat.totalAlerts() == 0) return false;
        return (double) stat.regimeChangeAlerts() / stat.totalAlerts() >= REGIME_CHANGE_DOMINANCE_THRESHOLD;
    }

    private AlertStat queryAlertStats(Connection conn, String businessUnit,
                                       LocalDate start, LocalDate end) throws SQLException {
        String regimeTypes = REGIME_CHANGE_EVENT_TYPES.stream()
                .map(t -> "'" + t + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("''");

        String sql = """
                SELECT
                    COUNT(*) AS total_alerts,
                    SUM(CASE WHEN event_type IN (%s) THEN 1 ELSE 0 END) AS regime_change_alerts,
                    SUM(CASE WHEN severity IN ('Sev-1', 'Sev-2') THEN 1 ELSE 0 END) AS severe_alerts,
                    SUM(CASE WHEN severity IS NULL THEN 1 ELSE 0 END) AS null_severity_alerts,
                    SUM(CASE WHEN state_text IN ('OPEN', 'NEW') THEN 1 ELSE 0 END) AS open_alerts,
                    SUM(CASE WHEN state_text = 'CLOSED' THEN 1 ELSE 0 END) AS closed_alerts
                FROM alerts_normalized
                WHERE business_unit = ?
                  AND alert_date BETWEEN ? AND ?
                """.formatted(regimeTypes);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, businessUnit);
            ps.setObject(2, start);
            ps.setObject(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AlertStat(
                            rs.getLong("total_alerts"),
                            rs.getLong("regime_change_alerts"),
                            rs.getLong("severe_alerts"),
                            rs.getLong("null_severity_alerts"),
                            rs.getLong("open_alerts"),
                            rs.getLong("closed_alerts")
                    );
                }
            }
        }
        return new AlertStat(0, 0, 0, 0, 0, 0);
    }

    private long queryTotalTrips(Connection conn, String businessUnit,
                                  LocalDate start, LocalDate end, Path dataDir) throws SQLException {
        // Create trips view if not already present
        String glob = dataDir.resolve("Ride_data _trip-*.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String createView = """
                CREATE OR REPLACE TEMP VIEW trips_normalized AS
                SELECT
                    trim(business_unit) AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT) AS trip_id,
                    CAST(replace(trip_date, ', ', ' ') AS DATE) AS trip_date
                FROM read_csv_auto('%s', union_by_name = true, header = true)
                """.formatted(glob);
        try (Statement st = conn.createStatement()) {
            st.execute(createView);
        }

        String sql = "SELECT COUNT(*) AS cnt FROM trips_normalized WHERE business_unit = ? AND trip_date BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, businessUnit);
            ps.setObject(2, start);
            ps.setObject(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("cnt") : 0;
            }
        }
    }

    private void createAlertsView(Connection conn, Path dataDir) throws SQLException {
        // alerts_data: start_time format is "May 1, 2026, 12:03 AM" — extract date
        // severity "False" cleaned to null
        String alertsFile = dataDir.resolve("alerts_data.csv")
                .toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW alerts_normalized AS
                SELECT
                    trim(business_unit)                                                      AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT)               AS trip_id,
                    trim(event_type)                                                         AS event_type,
                    CASE WHEN trim(severity) IN ('Sev-1','Sev-2','Sev-3') THEN trim(severity) ELSE NULL END
                                                                                             AS severity,
                    trim(state_text)                                                         AS state_text,
                    CAST(strptime(
                             trim(regexp_replace(start_time, ',\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)', '')),
                             '%%B %%e, %%Y'
                         ) AS DATE)                                                          AS alert_date
                FROM read_csv_auto('%s', header = true)
                """.formatted(alertsFile);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private MetricEvidence unavailable(RunContext context, String reason) {
        MetricRequest req = new MetricRequest(
                context.tenant(), MetricId.M13_ALERT_RATE,
                MetricRequest.Measure.VALUE,
                new MetricWindow(context.asOfDate().minusWeeks(1), context.asOfDate()),
                Map.of(), context.versions().data()
        );
        return new MetricEvidence(
                UUID.randomUUID().toString(), req, MetricStatus.UNAVAILABLE,
                null, MetricUnit.PER_THOUSAND_TRIPS, null, null, 0,
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

    private record AlertStat(long totalAlerts, long regimeChangeAlerts, long severeAlerts,
                              long nullSeverityAlerts, long openAlerts, long closedAlerts) {}
}
