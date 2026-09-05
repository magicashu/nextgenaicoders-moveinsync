package com.moveinsync.mobilitycopilot.anomaly.tools;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.application.CapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.application.ContributionService;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRow;
import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.DistributionRow;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The seven allowlisted investigation worker tools (D-030). Each returns typed, tenant-scoped,
 * provenance-carrying evidence. Direct findings are produced by deterministic rules only.
 */
@Service
public class WorkerContributionTools {

    public static final List<String> WORKERS = List.of(
            "vendor", "site_shift_direction", "delay_reason", "cost_billing", "feedback", "tracking_safety_alerts", "noshow_roster");

    private final MetricService metrics;
    private final ContributionService contributions;
    private final CapabilityMatrixService capabilities;

    public WorkerContributionTools(MetricService metrics, ContributionService contributions, CapabilityMatrixService capabilities) {
        this.metrics = metrics;
        this.contributions = contributions;
        this.capabilities = capabilities;
    }

    public WorkerEvidence run(String worker, TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        return switch (worker) {
            case "vendor" -> vendor(tenant, current, baseline, filters);
            case "site_shift_direction" -> siteShiftDirection(tenant, current, baseline, filters);
            case "delay_reason" -> delayReason(tenant, current, baseline, filters);
            case "cost_billing" -> costBilling(tenant, current, baseline, filters);
            case "feedback" -> feedback(tenant, current, baseline, filters);
            case "tracking_safety_alerts" -> trackingSafetyAlerts(tenant, current, baseline, filters);
            case "noshow_roster" -> noShowRoster(tenant, current, baseline, filters);
            default -> throw new IllegalArgumentException("Unknown investigation worker: " + worker);
        };
    }

    public WorkerEvidence vendor(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        ContributionRanking ranking = contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, Dimension.VENDOR_ID, current, baseline, filters);
        List<String> findings = new ArrayList<>();
        List<String> caveats = new ArrayList<>(ranking.caveats());
        List<ContributionRow> qualified = ranking.qualifiedRows();
        if (qualified.isEmpty()) {
            caveats.add("No vendor reaches the 500-trip minimum in both windows; vendor comparison is qualified context only");
        } else if (ranking.allQualifiedIncreased()) {
            findings.add("Every vendor with at least 500 trips in both windows rose (%d vendors, range %s%% to %s%%); the change is not attributable to a single vendor."
                    .formatted(qualified.size(), min(qualified), max(qualified)));
        } else {
            long rose = qualified.stream().filter(r -> r.delta().signum() > 0).count();
            findings.add("%d of %d qualified vendors deteriorated; %d did not.".formatted(rose, qualified.size(), qualified.size() - rose));
        }
        return new WorkerEvidence("vendor", tenant.businessUnit(), List.of(), List.of(ranking), List.of(), findings, caveats, true);
    }

    public WorkerEvidence siteShiftDirection(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        CapabilityMatrix matrix = capabilities.matrix(tenant);
        List<ContributionRanking> rankings = new ArrayList<>();
        List<String> findings = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        for (Dimension dimension : List.of(Dimension.SITE_ID, Dimension.SHIFT_ID, Dimension.DIRECTION)) {
            ContributionRanking ranking = contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, dimension, current, baseline, filters);
            rankings.add(ranking);
            ranking.rows().stream().filter(ContributionRow::qualified).findFirst().ifPresent(top -> {
                if (top.shareOfCurrentNumerator() != null && top.delta() != null && top.delta().signum() > 0) {
                    findings.add("%s '%s' carries %s%% of delayed trips at %s%% (baseline %s%%)."
                            .formatted(label(dimension), top.member(), top.shareOfCurrentNumerator(), top.currentValue(), top.baselineValue()));
                }
            });
        }
        matrix.statuses().stream().filter(s -> s.analysis().equals("site_shift_direction")
                && s.support() != com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus.Support.SUPPORTED)
                .forEach(s -> caveats.add(s.reason()));
        return new WorkerEvidence("site_shift_direction", tenant.businessUnit(), List.of(), rankings, List.of(), findings, caveats, true);
    }

    public WorkerEvidence delayReason(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        Distribution mix = contributions.delayReasonMix(tenant, current, baseline, filters);
        List<String> findings = new ArrayList<>();
        for (DistributionRow row : mix.rows()) {
            if (row.share() != null && row.baselineShare() != null && row.share().subtract(row.baselineShare()).compareTo(new BigDecimal("3")) >= 0) {
                findings.add("Delay reason %s rose to %s%% of delayed trips from %s%%.".formatted(row.category(), row.share(), row.baselineShare()));
            }
        }
        return new WorkerEvidence("delay_reason", tenant.businessUnit(), List.of(), List.of(), List.of(mix), findings, mix.caveats(), true);
    }

    public WorkerEvidence costBilling(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        MetricResult m09 = query(tenant, MetricId.M09_MEDIAN_COST_PER_TRIP, current, baseline, filters);
        MetricResult m10 = query(tenant, MetricId.M10_COST_PER_BILLED_KM, current, baseline, filters);
        List<String> findings = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        if (m09.status() == MetricStatus.SUPPORTED && m09.delta() != null) {
            findings.add(m09.delta().signum() > 0
                    ? "Median billed cost per trip rose from %s to %s.".formatted(m09.baselineValue(), m09.value())
                    : "Median billed cost per trip did not rise (%s versus %s); no cost penalty is visible.".formatted(m09.value(), m09.baselineValue()));
        }
        caveats.addAll(m09.caveats().stream().filter(c -> c.startsWith("Unsupported") || c.startsWith("No baseline")).toList());
        caveats.addAll(m10.caveats().stream().filter(c -> c.startsWith("Unsupported")).toList());
        return new WorkerEvidence("cost_billing", tenant.businessUnit(), List.of(m09, m10), List.of(), List.of(), findings, caveats,
                m09.status() == MetricStatus.SUPPORTED);
    }

    public WorkerEvidence feedback(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        MetricResult m11 = query(tenant, MetricId.M11_LOW_DRIVER_RATING_RATE, current, baseline, filters);
        MetricResult m12 = query(tenant, MetricId.M12_MEAN_DRIVER_SAFETY_RATING, current, baseline, filters);
        List<String> findings = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        if (m11.status() == MetricStatus.SUPPORTED && m11.delta() != null) {
            findings.add(m11.delta().abs().compareTo(new BigDecimal("1")) < 0
                    ? "Low driver-rating rate is flat at %s%% (baseline %s%%).".formatted(m11.value(), m11.baselineValue())
                    : "Low driver-rating rate moved from %s%% to %s%%.".formatted(m11.baselineValue(), m11.value()));
        }
        m11.caveats().stream().filter(c -> c.startsWith("Low feedback") || c.startsWith("Unsupported")).forEach(caveats::add);
        return new WorkerEvidence("feedback", tenant.businessUnit(), List.of(m11, m12), List.of(), List.of(), findings, caveats,
                m11.status() == MetricStatus.SUPPORTED);
    }

    public WorkerEvidence trackingSafetyAlerts(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        List<MetricResult> results = new ArrayList<>();
        for (MetricId id : List.of(MetricId.M13_ALERT_RATE, MetricId.M14_SEVERE_ALERT_RATE, MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90,
                MetricId.M16_TRACKING_GAP_RATE, MetricId.M18_ESCORT_PRESENT_RATE)) {
            results.add(query(tenant, id, current, baseline, filters));
        }
        Distribution mix = contributions.alertTypeMix(tenant, current, baseline, filters);
        List<String> findings = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        results.stream().filter(r -> r.status() == MetricStatus.UNSUPPORTED).forEach(r -> caveats.addAll(r.caveats()));
        MetricResult m16 = results.get(3);
        if (m16.status() == MetricStatus.SUPPORTED && m16.baselineValue() != null && m16.baselineValue().signum() > 0
                && m16.value().compareTo(m16.baselineValue().multiply(new BigDecimal("2"))) >= 0) {
            findings.add("Device-unreachable alerts doubled: %s versus %s per 1,000 trips.".formatted(m16.value(), m16.baselineValue()));
        }
        boolean supported = results.stream().anyMatch(r -> r.status() == MetricStatus.SUPPORTED);
        return new WorkerEvidence("tracking_safety_alerts", tenant.businessUnit(), results, List.of(), List.of(mix), findings, caveats, supported);
    }

    public WorkerEvidence noShowRoster(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        MetricResult m06 = query(tenant, MetricId.M06_NO_SHOW_RATE, current, baseline, filters);
        MetricResult m07 = query(tenant, MetricId.M07_DASHBOARD_CANCELLATION_RATE, current, baseline, filters);
        MetricResult m04 = query(tenant, MetricId.M04_ON_TIME_PICKUP_RATE, current, baseline, filters);
        List<String> findings = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        if (m04.status() == MetricStatus.SUPPORTED && m04.delta() != null && m04.delta().signum() < 0) {
            findings.add("Leg-level on-time pickups fell from %s%% to %s%%, confirming the trip-level trend.".formatted(m04.baselineValue(), m04.value()));
        }
        for (MetricResult r : List.of(m06, m07, m04)) {
            r.caveats().stream().filter(c -> c.startsWith("Unsupported")).forEach(caveats::add);
        }
        return new WorkerEvidence("noshow_roster", tenant.businessUnit(), List.of(m06, m07, m04), List.of(), List.of(), findings, caveats,
                m06.status() == MetricStatus.SUPPORTED);
    }

    private MetricResult query(TenantContext tenant, MetricId id, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        return metrics.query(new MetricQuery(tenant, id, current.start(), current.end(), baseline.start(), baseline.end(), filters));
    }

    private static String label(Dimension dimension) {
        return switch (dimension) {
            case SITE_ID -> "Site";
            case SHIFT_ID -> "Shift";
            case DIRECTION -> "Direction";
            case VENDOR_ID -> "Vendor";
            case MODE -> "Mode";
            case FUEL_TYPE -> "Fuel type";
            case VEHICLE_ID -> "Vehicle";
        };
    }

    private static BigDecimal min(List<ContributionRow> rows) {
        return rows.stream().map(ContributionRow::currentValue).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal max(List<ContributionRow> rows) {
        return rows.stream().map(ContributionRow::currentValue).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
    }
}
