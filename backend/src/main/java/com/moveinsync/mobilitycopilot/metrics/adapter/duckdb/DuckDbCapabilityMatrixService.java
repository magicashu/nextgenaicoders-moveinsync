package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityReport;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import com.moveinsync.mobilitycopilot.metrics.application.CapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus.Support;
import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Derives the D-030 capability matrix from the loaded data instead of hard-coding tenant names. */
@Service
public class DuckDbCapabilityMatrixService implements CapabilityMatrixService {

    static final double LOW_FEEDBACK_COVERAGE = 0.30;
    static final double ZERO_KM_UNSUPPORTED_SHARE = 0.90;
    static final long LOW_ALERT_VOLUME = 500;

    private final AnalyticsStore store;
    private final Map<String, CapabilityMatrix> cache = new ConcurrentHashMap<>();

    public DuckDbCapabilityMatrixService(AnalyticsStore store) {
        this.store = store;
    }

    @Override
    public CapabilityMatrix matrix(TenantContext tenant) {
        String key = store.catalog().dataVersion() + ":" + tenant.businessUnit();
        return cache.computeIfAbsent(key, k -> compute(tenant));
    }

    private CapabilityMatrix compute(TenantContext tenant) {
        DataQualityReport report = store.qualityReport();
        String dataVersion = report.dataVersion();
        TenantProfile profile = profile(tenant);
        List<CapabilityStatus> statuses = new ArrayList<>();
        String bu = tenant.businessUnit();

        if (profile.trips() == 0) {
            statuses.add(new CapabilityStatus("tenant", Support.UNSUPPORTED, "No trips for business unit " + bu, List.of(MetricId.values())));
            return new CapabilityMatrix(bu, dataVersion, statuses);
        }
        statuses.add(new CapabilityStatus("delay", Support.SUPPORTED, "Ride files present", List.of(MetricId.M01_DELAYED_TRIP_RATE, MetricId.M02_DELAY_MINUTES, MetricId.M03_DELAY_REASON_MIX)));
        statuses.add(new CapabilityStatus("occupancy", Support.SUPPORTED, "Ride files present; occupancy capped at 100%", List.of(MetricId.M08_OCCUPANCY_RATE)));
        statuses.add(new CapabilityStatus("ev_share", Support.SUPPORTED, "Fuel type present", List.of(MetricId.M17_EV_SHARE)));

        boolean legs = report.isPresent(DatasetFile.LEGS) && profile.legs() > 0;
        statuses.add(new CapabilityStatus("punctuality_legs", legs ? Support.SUPPORTED : Support.UNSUPPORTED,
                legs ? "Employee legs present" : "emp_Data.csv missing or empty: leg-level punctuality unavailable",
                List.of(MetricId.M04_ON_TIME_PICKUP_RATE, MetricId.M05_ON_TIME_DROP_RATE)));
        statuses.add(new CapabilityStatus("no_show_roster", legs ? Support.SUPPORTED : Support.UNSUPPORTED,
                legs ? "Employee legs present" : "emp_Data.csv missing or empty: no-show and cancellation unavailable",
                List.of(MetricId.M06_NO_SHOW_RATE, MetricId.M07_DASHBOARD_CANCELLATION_RATE)));

        boolean bills = report.isPresent(DatasetFile.BILLS) && profile.billLines() > 0;
        statuses.add(new CapabilityStatus("cost_per_trip", bills ? Support.SUPPORTED : Support.UNSUPPORTED,
                bills ? "Bill lines present; negative lines excluded as adjustments" : "bill_data.csv missing or empty: cost branch disabled",
                List.of(MetricId.M09_MEDIAN_COST_PER_TRIP)));
        double zeroKm = report.zeroBilledKmShareByTenant().getOrDefault(bu, 0.0);
        if (!bills) {
            statuses.add(new CapabilityStatus("cost_per_km", Support.UNSUPPORTED, "bill_data.csv missing or empty", List.of(MetricId.M10_COST_PER_BILLED_KM)));
        } else if (zeroKm >= ZERO_KM_UNSUPPORTED_SHARE) {
            statuses.add(new CapabilityStatus("cost_per_km", Support.UNSUPPORTED,
                    "%.1f%% of billed lines have zero km (Q2); cost per km is not computable".formatted(zeroKm * 100),
                    List.of(MetricId.M10_COST_PER_BILLED_KM)));
        } else {
            statuses.add(new CapabilityStatus("cost_per_km", Support.SUPPORTED, "Billed km present", List.of(MetricId.M10_COST_PER_BILLED_KM)));
        }

        boolean feedback = report.isPresent(DatasetFile.FEEDBACK) && profile.feedbackRows() > 0;
        double coverage = report.feedbackCoverageByTenant().getOrDefault(bu, 0.0);
        if (!feedback) {
            statuses.add(new CapabilityStatus("feedback", Support.UNSUPPORTED, "trip_feedback.csv missing or empty", List.of(MetricId.M11_LOW_DRIVER_RATING_RATE, MetricId.M12_MEAN_DRIVER_SAFETY_RATING)));
        } else if (coverage < LOW_FEEDBACK_COVERAGE) {
            statuses.add(new CapabilityStatus("feedback", Support.DERIVABLE,
                    "Low feedback coverage: %.1f%% of trips rated".formatted(coverage * 100),
                    List.of(MetricId.M11_LOW_DRIVER_RATING_RATE, MetricId.M12_MEAN_DRIVER_SAFETY_RATING)));
        } else {
            statuses.add(new CapabilityStatus("feedback", Support.SUPPORTED, "Feedback coverage %.1f%%".formatted(coverage * 100),
                    List.of(MetricId.M11_LOW_DRIVER_RATING_RATE, MetricId.M12_MEAN_DRIVER_SAFETY_RATING)));
        }

        boolean alerts = report.isPresent(DatasetFile.ALERTS);
        if (!alerts) {
            statuses.add(new CapabilityStatus("safety_alerts", Support.UNSUPPORTED, "alerts_data.csv missing", List.of(MetricId.M13_ALERT_RATE, MetricId.M14_SEVERE_ALERT_RATE, MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90)));
            statuses.add(new CapabilityStatus("tracking_gap", Support.UNSUPPORTED, "alerts_data.csv missing", List.of(MetricId.M16_TRACKING_GAP_RATE)));
            statuses.add(new CapabilityStatus("escort_present", Support.UNSUPPORTED, "alerts_data.csv missing", List.of(MetricId.M18_ESCORT_PRESENT_RATE)));
        } else {
            statuses.add(new CapabilityStatus("safety_alerts", profile.alerts() >= LOW_ALERT_VOLUME ? Support.SUPPORTED : Support.DERIVABLE,
                    profile.alerts() >= LOW_ALERT_VOLUME ? "Alerts present (sign-off violations excluded)" : "Low alert volume: " + profile.alerts() + " alerts",
                    List.of(MetricId.M13_ALERT_RATE)));
            statuses.add(new CapabilityStatus("severe_alerts", profile.severeAlerts() > 0 ? Support.SUPPORTED : Support.DERIVABLE,
                    profile.severeAlerts() > 0 ? "Sev-1/2 alerts present" : "No Sev-1/2 alerts recorded for this tenant; rate is zero by construction",
                    List.of(MetricId.M14_SEVERE_ALERT_RATE)));
            statuses.add(new CapabilityStatus("severe_ack", profile.severeAlerts() >= 20 ? Support.SUPPORTED : Support.UNSUPPORTED,
                    profile.severeAlerts() >= 20 ? "Sev-1/2 acknowledgement durations present" : "Fewer than 20 Sev-1/2 alerts; P90 not meaningful",
                    List.of(MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90)));
            statuses.add(new CapabilityStatus("tracking_gap", profile.deviceUnreachable() > 0 ? Support.SUPPORTED : Support.UNSUPPORTED,
                    profile.deviceUnreachable() > 0 ? "DEVICE_NOT_REACHABLE alerts present" : "No DEVICE_NOT_REACHABLE events for this tenant",
                    List.of(MetricId.M16_TRACKING_GAP_RATE)));
            statuses.add(new CapabilityStatus("escort_present", profile.womanAlone() >= 20 ? Support.SUPPORTED : (profile.womanAlone() > 0 ? Support.DERIVABLE : Support.UNSUPPORTED),
                    profile.womanAlone() >= 20 ? "WOMAN_TRAVELLING_ALONE alerts present; descriptive only" : (profile.womanAlone() > 0 ? "Very few WOMAN_TRAVELLING_ALONE alerts (" + profile.womanAlone() + ")" : "No WOMAN_TRAVELLING_ALONE events for this tenant"),
                    List.of(MetricId.M18_ESCORT_PRESENT_RATE)));
        }
        statuses.add(new CapabilityStatus("site_shift_direction", profile.sitesWithVolume() > 1 ? Support.SUPPORTED : Support.DERIVABLE,
                profile.sitesWithVolume() > 1 ? profile.sitesWithVolume() + " sites with at least 300 trips" : "Single office with volume; site contribution is not discriminating",
                List.of()));
        statuses.add(new CapabilityStatus("vendor_peer", profile.vendorsWithVolume() > 1 ? Support.SUPPORTED : Support.DERIVABLE,
                profile.vendorsWithVolume() + " vendors with at least 500 trips", List.of()));
        statuses.add(new CapabilityStatus("gps_location", Support.UNSUPPORTED, "Dataset has no coordinates", List.of()));
        statuses.add(new CapabilityStatus("budget_variance", Support.UNSUPPORTED, "Dataset has no budget or quoted cost", List.of()));
        return new CapabilityMatrix(bu, dataVersion, statuses);
    }

    private TenantProfile profile(TenantContext tenant) {
        var rendered = GovernedSqlTemplate.render(SqlResources.read("sql/snapshots/tenant_profile.sql"), tenant.businessUnit(),
                new MetricWindow(LocalDate.MIN.plusDays(1), LocalDate.MAX.minusDays(1)), Map.of(), Set.of(Dimension.values()), Set.of(), Optional.empty());
        return DuckDbQueries.query(store, rendered, rs -> new TenantProfile(
                rs.getLong("trips"), rs.getLong("sites_with_volume"), rs.getLong("vendors_with_volume"), rs.getLong("alerts"),
                rs.getLong("severe_alerts"), rs.getLong("device_unreachable_alerts"), rs.getLong("woman_alone_alerts"),
                rs.getLong("feedback_rows"), rs.getLong("bill_lines"), rs.getLong("legs"))).getFirst();
    }

    record TenantProfile(long trips, long sitesWithVolume, long vendorsWithVolume, long alerts, long severeAlerts,
                         long deviceUnreachable, long womanAlone, long feedbackRows, long billLines, long legs) {
    }
}
