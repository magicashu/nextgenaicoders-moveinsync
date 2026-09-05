package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetProfileService;
import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** First governed official-data metric: M01, calculated at trip grain from immutable source files. */
@Service
public final class OfficialDuckDbGovernedMetricService implements GovernedMetricService {
    static final String M01_VERSION = "M01-v1.1";
    private static final Map<String, String> FILTER_COLUMNS = Map.of(
            "vendor_id", "vendor_id", "site_id", "office", "shift_id", "shift_type",
            "direction", "trip_direction", "mode", "product_type");
    private final MobilityDataProperties properties;
    private final DatasetProfileService profiles;

    public OfficialDuckDbGovernedMetricService(MobilityDataProperties properties, DatasetProfileService profiles) {
        this.properties = properties;
        this.profiles = profiles;
    }

    @Override
    public MetricEvidence compute(MetricRequest request) {
        validate(request);
        if (request.metricId() != MetricId.M01_DELAYED_TRIP_RATE) {
            return new MetricEvidence("metric-unavailable-" + request.metricId(), request, MetricStatus.UNAVAILABLE,
                    null, request.metricId().unit(), null, null, 0, null, null,
                    List.of("Metric is declared but not implemented in the current official-data slice."));
        }
        Path directory = Path.of(properties.directory());
        String dataVersion = profiles.profile(directory).dataVersion();
        if (!request.dataVersion().equals(dataVersion)) {
            throw new IllegalArgumentException("Metric request data version does not match the profiled official dataset.");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            createTripsView(connection, directory);
            Query query = m01Query(request);
            try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
                int index = 1;
                for (Object parameter : query.parameters()) {
                    statement.setObject(index++, parameter);
                }
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    long denominator = result.getLong("trip_count");
                    long numerator = result.getLong("delayed_trip_count");
                    BigDecimal value = denominator == 0 ? null : result.getBigDecimal("delayed_trip_rate");
                    MetricStatus status = denominator == 0 ? MetricStatus.UNAVAILABLE : MetricStatus.AVAILABLE;
                    List<String> warnings = denominator == 0
                            ? List.of("No eligible trips match the tenant, window, and requested filters.") : List.of();
                    return new MetricEvidence(evidenceId(request), request, status, value, request.metricId().unit(),
                            BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator), denominator, M01_VERSION,
                            "csv:" + directory.toAbsolutePath().normalize() + "#ride_data_trip", warnings);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to calculate governed M01 from official dataset", exception);
        }
    }

    private void createTripsView(Connection connection, Path directory) throws SQLException {
        String glob = directory.resolve("Ride_data _trip-*.csv").toAbsolutePath().normalize().toString().replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW official_trips AS
                SELECT trim(business_unit) AS business_unit,
                       CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT) AS trip_id,
                       CAST(strptime(trim(CAST(trip_date AS VARCHAR)), '%%B %%d, %%Y') AS DATE) AS trip_date,
                       CAST(replace(CAST(delay_minutes AS VARCHAR), ',', '') AS DOUBLE) AS delay_minutes,
                       trim(CAST(vendor_id AS VARCHAR)) AS vendor_id, trim(CAST(office AS VARCHAR)) AS office,
                       trim(CAST(shift_type AS VARCHAR)) AS shift_type,
                       trim(CAST(trip_direction AS VARCHAR)) AS trip_direction,
                       trim(CAST(product_type AS VARCHAR)) AS product_type
                FROM read_csv_auto('%s', union_by_name = true, header = true)
                WHERE business_unit IS NOT NULL AND trip_id IS NOT NULL AND trip_date IS NOT NULL
                """.formatted(glob);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Query m01Query(MetricRequest request) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) FILTER (WHERE delay_minutes > 0) AS delayed_trip_count,
                       count(*) AS trip_count,
                       round(100.0 * count(*) FILTER (WHERE delay_minutes > 0) / nullif(count(*), 0), 2) AS delayed_trip_rate
                FROM official_trips WHERE business_unit = ? AND trip_date BETWEEN ? AND ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(request.tenant().businessUnit(), request.window().start(), request.window().end()));
        Map<String, String> requestedFilters = request.filters() == null ? Map.of() : request.filters();
        for (Map.Entry<String, String> filter : new LinkedHashMap<>(requestedFilters).entrySet()) {
            String column = FILTER_COLUMNS.get(filter.getKey());
            if (column == null || filter.getValue() == null || filter.getValue().isBlank()) {
                throw new IllegalArgumentException("Unsupported or blank M01 filter: " + filter.getKey());
            }
            sql.append(" AND ").append(column).append(" = ?");
            parameters.add(filter.getValue());
        }
        return new Query(sql.toString(), List.copyOf(parameters));
    }

    private void validate(MetricRequest request) {
        if (request == null || request.tenant() == null || request.metricId() == null || request.measure() != MetricRequest.Measure.VALUE
                || request.window() == null || request.window().start() == null || request.window().end() == null
                || request.window().end().isBefore(request.window().start()) || request.dataVersion() == null || request.dataVersion().isBlank()) {
            throw new IllegalArgumentException("A governed metric request requires tenant, metric, VALUE measure, valid window, and data version.");
        }
    }

    private String evidenceId(MetricRequest request) {
        Map<String, String> filters = request.filters() == null ? Map.of() : request.filters();
        return "M01:" + request.tenant().businessUnit() + ":" + request.window().start() + ":" + request.window().end() + ":" + filters.hashCode();
    }

    private record Query(String sql, List<Object> parameters) {}
}
