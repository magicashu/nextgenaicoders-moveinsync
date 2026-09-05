package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

@Service
@Profile("legacy-sample")
public final class DuckDbMetricService implements MetricService {

    private static final String CONTRACT_VERSION = "M01-v1";
    private final MobilityDataProperties properties;

    public DuckDbMetricService(MobilityDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public MetricResult delayedTripRate(TenantContext tenant, LocalDate asOfDate) {
        LocalDate currentStart = asOfDate.minusDays(7);
        LocalDate currentEnd = asOfDate.minusDays(1);
        LocalDate baselineEnd = currentStart.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(27);
        Path dataDirectory = resolveDataDirectory();

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            createNormalizedView(connection, dataDirectory);
            WindowResult current = queryWindow(connection, tenant, currentStart, currentEnd);
            WindowResult baseline = queryWindow(connection, tenant, baselineStart, baselineEnd);
            return new MetricResult(
                    MetricId.M01_DELAYED_TRIP_RATE,
                    "Delayed-trip rate",
                    current.valuePercent(),
                    baseline.valuePercent(),
                    current.valuePercent().subtract(baseline.valuePercent()),
                    current.numerator(),
                    current.denominator(),
                    currentStart,
                    currentEnd,
                    CONTRACT_VERSION,
                    "csv:" + dataDirectory.toAbsolutePath().normalize());
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Unable to compute M01 from " + dataDirectory, exception);
        }
    }

    private void createNormalizedView(Connection connection, Path dataDirectory) throws SQLException {
        String glob = dataDirectory.resolve("Ride_data _trip-*.csv")
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace("'", "''");
        String sql = """
                CREATE OR REPLACE TEMP VIEW trips_normalized AS
                SELECT
                    trim(business_unit) AS business_unit,
                    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT) AS trip_id,
                    CAST(trip_date AS DATE) AS trip_date,
                    CAST(replace(CAST(delay_minutes AS VARCHAR), ',', '') AS DOUBLE) AS delay_minutes
                FROM read_csv_auto('%s', union_by_name = true, header = true)
                """.formatted(glob);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private WindowResult queryWindow(
            Connection connection,
            TenantContext tenant,
            LocalDate start,
            LocalDate end) throws SQLException, IOException {
        String sql = readClasspathResource("sql/metrics/m01_delayed_trip_rate.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenant.businessUnit());
            statement.setObject(2, start);
            statement.setObject(3, end);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("M01 returned no row");
                }
                return new WindowResult(
                        resultSet.getLong("delayed_trips"),
                        resultSet.getLong("total_trips"),
                        resultSet.getBigDecimal("value_percent"));
            }
        }
    }

    private Path resolveDataDirectory() {
        Path configured = Path.of(properties.directory());
        if (Files.isDirectory(configured)) {
            return configured;
        }
        Path fromBackendModule = Path.of("..").resolve(configured).normalize();
        if (Files.isDirectory(fromBackendModule)) {
            return fromBackendModule;
        }
        throw new IllegalStateException("Dataset directory does not exist: " + configured);
    }

    private String readClasspathResource(String name) throws IOException {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Missing classpath resource: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record WindowResult(long numerator, long denominator, BigDecimal valuePercent) {
    }
}
