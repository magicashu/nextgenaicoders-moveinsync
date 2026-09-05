package com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb;

import com.moveinsync.mobilitycopilot.ingestion.application.DatasetChecksums;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetFileCatalog;
import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityFinding;
import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityReport;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetCatalog;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import com.moveinsync.mobilitycopilot.ingestion.domain.FileProfile;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discovers, validates and normalises the organizer files into DuckDB tables.
 * Every file is read as text and cast with TRY_CAST so malformed values become NULL and are
 * counted instead of failing the load. Missing columns become NULL columns and are reported.
 */
public final class DuckDbDatasetLoader {

    private static final List<String> SCHEMA_SCRIPTS = List.of(
            "sql/schema/01_trips.sql",
            "sql/schema/02_legs.sql",
            "sql/schema/03_bills.sql",
            "sql/schema/04_feedback.sql",
            "sql/schema/05_alerts.sql",
            "sql/schema/06_snapshots.sql");

    private final DatasetFileCatalog fileCatalog;

    public DuckDbDatasetLoader(DatasetFileCatalog fileCatalog) {
        this.fileCatalog = fileCatalog;
    }

    public record LoadResult(DatasetCatalog catalog, DataQualityReport qualityReport) {
    }

    public LoadResult load(Connection connection, Path directory) throws SQLException {
        Map<DatasetFile, List<Path>> discovered = fileCatalog.discover(directory);
        if (discovered.getOrDefault(DatasetFile.RIDES, List.of()).isEmpty()) {
            throw new IllegalStateException("No ride files matching '" + DatasetFile.RIDES.glob() + "' in " + directory);
        }
        Map<DatasetFile, FileProfile> profiles = new LinkedHashMap<>();
        List<String> digests = new ArrayList<>();
        for (DatasetFile file : DatasetFile.values()) {
            List<Path> paths = discovered.getOrDefault(file, List.of());
            profiles.put(file, createRawTable(connection, file, paths));
            profiles.get(file).checksums().forEach(digests::add);
        }
        for (String script : SCHEMA_SCRIPTS) {
            executeScript(connection, SqlResources.read(script));
        }
        Map<DatasetFile, FileProfile> finished = new LinkedHashMap<>();
        for (var entry : profiles.entrySet()) {
            FileProfile p = entry.getValue();
            long normalized = p.present() ? count(connection, "SELECT count(*) FROM " + entry.getKey().tableName()) : 0;
            finished.put(entry.getKey(), new FileProfile(p.file(), p.paths(), p.checksums(), p.present(), p.rawRows(),
                    normalized, p.missingColumns(), p.unexpectedColumns()));
        }
        String dataVersion = DatasetChecksums.dataVersion(digests);
        DatasetCatalog catalog = new DatasetCatalog(directory, dataVersion, new ArrayList<>(finished.values()));
        return new LoadResult(catalog, profile(connection, catalog));
    }

    private FileProfile createRawTable(Connection connection, DatasetFile file, List<Path> paths) throws SQLException {
        if (paths.isEmpty()) {
            // Create an empty typed table so downstream SQL compiles and reports zero rows.
            String columns = file.columns().stream().map(c -> quote(c) + " VARCHAR").collect(Collectors.joining(", "));
            execute(connection, "CREATE OR REPLACE TABLE " + file.rawTableName() + " (" + columns + ")");
            return FileProfile.missing(file);
        }
        Set<String> actual = new LinkedHashSet<>();
        for (Path path : paths) {
            actual.addAll(columnsOf(connection, path));
        }
        List<String> missing = file.columns().stream().filter(c -> !actual.contains(c)).toList();
        List<String> unexpected = actual.stream().filter(c -> !file.columns().contains(c)).toList();
        String select = file.columns().stream()
                .map(c -> actual.contains(c) ? quote(c) : "NULL::VARCHAR AS " + quote(c))
                .collect(Collectors.joining(", "));
        String fileList = paths.stream().map(p -> "'" + escape(p.toString()) + "'").collect(Collectors.joining(", "));
        execute(connection, "CREATE OR REPLACE TABLE " + file.rawTableName() + " AS SELECT " + select
                + " FROM read_csv([" + fileList + "], header = true, all_varchar = true, union_by_name = true, null_padding = true)");
        long rows = count(connection, "SELECT count(*) FROM " + file.rawTableName());
        List<String> checksums = paths.stream().map(DatasetChecksums::sha256).toList();
        return new FileProfile(file, paths.stream().map(Path::toString).toList(), checksums, true, rows, 0, missing, unexpected);
    }

    private List<String> columnsOf(Connection connection, Path path) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM (DESCRIBE SELECT * FROM read_csv(?, header = true, all_varchar = true))")) {
            statement.setString(1, path.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    private DataQualityReport profile(Connection connection, DatasetCatalog catalog) throws SQLException {
        List<DataQualityFinding> findings = new ArrayList<>();
        long rawTrips = count(connection, "SELECT count(*) FROM raw_rides");
        long trips = count(connection, "SELECT count(*) FROM trips");
        findings.add(finding("Q0-DUPLICATE-TRIPS", "Duplicate or unkeyed ride rows removed by (business_unit, trip_id)",
                rawTrips - trips, "Keep the first row per composite key; drop rows without tenant, trip id or date", DataQualityFinding.Severity.INFO));
        findings.add(finding("Q1-TRIP-ID-COLLISIONS", "trip_id values shared by more than one business unit",
                scalar(connection, SqlResources.read("sql/quality/q01_trip_id_collisions.sql")),
                "Composite key (business_unit, trip_id) everywhere; never join on trip_id alone", DataQualityFinding.Severity.WARNING));
        if (catalog.isPresent(DatasetFile.LEGS)) {
            long rawLegs = catalog.profile(DatasetFile.LEGS).rawRows();
            long legs = count(connection, "SELECT count(*) FROM legs");
            findings.add(finding("Q1-DUPLICATE-LEGS", "Duplicate employee legs removed by (business_unit, trip_id, stwid)",
                    rawLegs - legs, "Keep the first leg by planned pickup", DataQualityFinding.Severity.INFO));
            findings.add(finding("Q10-NEGATIVE-DISTANCE", "Legs with negative planned or traveled km",
                    scalar(connection, SqlResources.read("sql/quality/q10_negative_distance.sql")), "Null out and flag", DataQualityFinding.Severity.INFO));
            findings.add(finding("Q11-NULL-PLANNED-EPOCH", "Legs without planned pickup epoch",
                    scalar(connection, SqlResources.read("sql/quality/q11_null_planned_epochs.sql")),
                    "Excluded from punctuality denominators", DataQualityFinding.Severity.INFO));
        }
        if (catalog.isPresent(DatasetFile.BILLS)) {
            long rawBills = catalog.profile(DatasetFile.BILLS).rawRows();
            long bills = count(connection, "SELECT count(*) FROM bills");
            findings.add(finding("Q1-DUPLICATE-BILLS", "Exact duplicate bill lines removed",
                    rawBills - bills, "Drop exact duplicates; keep and flag multi-line bills", DataQualityFinding.Severity.INFO));
            findings.add(finding("Q3-NEGATIVE-BILLS", "Negative bill lines (billing adjustments)",
                    scalar(connection, SqlResources.read("sql/quality/q03_negative_bills.sql")),
                    "Excluded from spend metrics and reported as adjustments", DataQualityFinding.Severity.WARNING));
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlResources.read("sql/quality/q04_extreme_delays.sql"))) {
            rs.next();
            findings.add(finding("Q4-CAPPED-DELAYS", "Delayed trips above 600 minutes", rs.getLong("capped_rows"),
                    "Capped at 600 minutes for averages; late flag kept", DataQualityFinding.Severity.INFO));
            findings.add(finding("Q4-QUARANTINED-DELAYS", "Delayed trips above 1,440 minutes", rs.getLong("quarantined_rows"),
                    "Quarantined from averages; late flag kept", DataQualityFinding.Severity.INFO));
        }
        findings.add(finding("Q8-HEADCOUNT-MISMATCH", "Trips where planned != actual + no-show",
                scalar(connection, SqlResources.read("sql/quality/q08_headcount_mismatch.sql")),
                "No-show derived from employee legs, never from trip counts", DataQualityFinding.Severity.INFO));
        findings.add(finding("Q9-OCCUPANCY-CAPPED", "Trips with more riders than capacity",
                scalar(connection, SqlResources.read("sql/quality/q09_occupancy_capped.sql")), "Occupancy capped at 100%", DataQualityFinding.Severity.INFO));
        if (catalog.isPresent(DatasetFile.ALERTS)) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(SqlResources.read("sql/quality/q06_unclassified_severity.sql"))) {
                rs.next();
                findings.add(finding("Q6-UNCLASSIFIED-SEVERITY", "Alerts with severity literal 'False'", rs.getLong("unclassified_rows"),
                        "Treated as UNCLASSIFIED; only Sev-1/2/3 count toward severity metrics", DataQualityFinding.Severity.INFO));
                findings.add(finding("Q6-NULL-SEVERITY", "Alerts with null severity", rs.getLong("null_rows"),
                        "Excluded from severity metrics and acknowledgement SLA", DataQualityFinding.Severity.INFO));
            }
            findings.add(finding("Q5-SIGN-OFF-VIOLATIONS", "EMPLOYEE_SIGN_OFF_TIME_VIOLATION alerts",
                    count(connection, "SELECT count(*) FROM alerts WHERE is_excluded_event_type"),
                    "Excluded from alert-rate metrics; step change classified as data-regime change", DataQualityFinding.Severity.WARNING));
        }
        if (catalog.isPresent(DatasetFile.FEEDBACK)) {
            findings.add(finding("Q12-MARSHAL-ZERO", "Feedback rows with marshal rating 0 (no marshal)",
                    scalar(connection, SqlResources.read("sql/quality/q12_marshal_zero.sql")), "Excluded from averages", DataQualityFinding.Severity.INFO));
        }
        for (FileProfile p : catalog.files()) {
            if (!p.present()) {
                findings.add(finding("FILE-MISSING-" + p.file().name(), "Dataset file missing: " + p.file().glob(), 0,
                        "Dependent metrics report UNSUPPORTED with a typed reason", DataQualityFinding.Severity.WARNING));
            } else if (!p.missingColumns().isEmpty()) {
                findings.add(finding("COLUMNS-MISSING-" + p.file().name(), "Missing columns: " + p.missingColumns(), p.rawRows(),
                        "Columns normalised to NULL; dependent metrics degrade", DataQualityFinding.Severity.WARNING));
            }
        }
        Map<String, Double> joinCoverage = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlResources.read("sql/quality/join_coverage.sql"))) {
            while (rs.next()) {
                long rows = rs.getLong("rows");
                joinCoverage.put(rs.getString("source"), rows == 0 ? 0.0 : rs.getLong("matched") / (double) rows);
            }
        }
        Map<String, Double> feedbackCoverage = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlResources.read("sql/quality/feedback_coverage.sql"))) {
            while (rs.next()) {
                feedbackCoverage.put(rs.getString(1), rs.getObject(2) == null ? 0.0 : rs.getDouble(2));
            }
        }
        Map<String, Double> zeroKm = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlResources.read("sql/quality/q02_zero_billed_km_share.sql"))) {
            while (rs.next()) {
                zeroKm.put(rs.getString(1), rs.getObject("zero_share") == null ? 0.0 : rs.getDouble("zero_share"));
            }
        }
        return new DataQualityReport(catalog.dataVersion(), catalog.files(), findings, joinCoverage, feedbackCoverage, zeroKm);
    }

    private static DataQualityFinding finding(String code, String description, long rows, String handling,
                                              DataQualityFinding.Severity severity) {
        return new DataQualityFinding(code, description, Math.max(rows, 0), handling, severity);
    }

    private static void executeScript(Connection connection, String script) throws SQLException {
        for (String statement : script.split(";\\s*\\n")) {
            String sql = statement.strip();
            if (!sql.isEmpty()) {
                execute(connection, sql);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        return scalar(connection, sql);
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String literal) {
        return literal.replace("'", "''");
    }
}
