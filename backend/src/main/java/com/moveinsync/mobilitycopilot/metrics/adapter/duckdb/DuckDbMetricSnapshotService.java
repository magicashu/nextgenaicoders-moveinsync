package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import com.moveinsync.mobilitycopilot.metrics.application.MetricSnapshotService;
import com.moveinsync.mobilitycopilot.metrics.domain.DailyPoint;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRegistry;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequestException;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricSeries;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads the daily snapshot tables built at load time. */
@Service
public class DuckDbMetricSnapshotService implements MetricSnapshotService {

    private final AnalyticsStore store;

    public DuckDbMetricSnapshotService(AnalyticsStore store) {
        this.store = store;
    }

    @Override
    public MetricSeries dailySeries(TenantContext tenant, MetricId metricId, MetricWindow window) {
        String source = switch (metricId) {
            case M01_DELAYED_TRIP_RATE, M17_EV_SHARE, M08_OCCUPANCY_RATE, M02_DELAY_MINUTES -> "sql/snapshots/daily_trip_series.sql";
            case M04_ON_TIME_PICKUP_RATE, M05_ON_TIME_DROP_RATE, M06_NO_SHOW_RATE, M07_DASHBOARD_CANCELLATION_RATE -> "sql/snapshots/daily_leg_series.sql";
            default -> throw new MetricRequestException("NO_SNAPSHOT", "No daily snapshot for " + metricId);
        };
        var rendered = GovernedSqlTemplate.render(SqlResources.read(source), tenant.businessUnit(), window, Map.of(), Set.of(), Set.of(), Optional.empty());
        List<DailyPoint> points = DuckDbQueries.query(store, rendered, rs -> point(metricId, rs));
        return new MetricSeries(tenant.businessUnit(), metricId, MetricRegistry.definition(metricId).unit(), window, points,
                MetricRegistry.CONTRACT_VERSION, store.catalog().dataVersion(), source);
    }

    private static DailyPoint point(MetricId metricId, ResultSet rs) throws SQLException {
        long numerator;
        long denominator;
        double factor = 100.0;
        switch (metricId) {
            case M01_DELAYED_TRIP_RATE -> { numerator = rs.getLong("delayed_trips"); denominator = rs.getLong("trips"); }
            case M17_EV_SHARE -> { numerator = rs.getLong("electric_trips"); denominator = rs.getLong("trips"); }
            case M08_OCCUPANCY_RATE -> { numerator = rs.getLong("occupancy_numerator"); denominator = rs.getLong("occupancy_denominator"); }
            case M02_DELAY_MINUTES -> { numerator = rs.getLong("capped_delay_minutes"); denominator = rs.getLong("capped_delay_trips"); factor = 1.0; }
            case M04_ON_TIME_PICKUP_RATE -> { numerator = rs.getLong("on_time_pickup_legs"); denominator = rs.getLong("pickup_eligible_legs"); }
            case M05_ON_TIME_DROP_RATE -> { numerator = rs.getLong("on_time_drop_legs"); denominator = rs.getLong("drop_eligible_legs"); }
            case M06_NO_SHOW_RATE -> { numerator = rs.getLong("no_show_legs"); denominator = rs.getLong("valid_legs"); }
            case M07_DASHBOARD_CANCELLATION_RATE -> { numerator = rs.getLong("dashboard_cancelled_legs"); denominator = rs.getLong("valid_legs"); }
            default -> throw new IllegalStateException(metricId.name());
        }
        BigDecimal value = denominator == 0 ? null : BigDecimal.valueOf(factor * numerator / denominator).setScale(2, RoundingMode.HALF_UP);
        return new DailyPoint(rs.getObject("trip_date", java.time.LocalDate.class), numerator, denominator, value);
    }
}
