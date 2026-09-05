package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.metrics.application.ContributionService;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.application.MetricSnapshotService;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Read-only charts: bounded weekly windows, existing governed SQL, no additional LLM runs. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final RequestContext context;
    private final MetricService metrics;
    private final MetricSnapshotService snapshots;
    private final ContributionService contributions;

    public DashboardController(RequestContext context, MetricService metrics,
                               MetricSnapshotService snapshots, ContributionService contributions) {
        this.context = context; this.metrics = metrics; this.snapshots = snapshots; this.contributions = contributions;
    }

    @GetMapping
    public DashboardCharts charts(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var tenant = context.tenant(context.actor(actorId, businessUnit, roles));
        var current = MetricWindow.trailingWeek(asOf);
        var baseline = MetricWindow.priorFourWeeks(current);
        return new DashboardCharts(
                metrics.delayedTripRate(tenant, asOf),
                metrics.query(MetricQuery.trailingWeekWithFourWeekBaseline(tenant, MetricId.M04_ON_TIME_PICKUP_RATE, asOf)),
                contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, Dimension.VENDOR_ID, current, baseline, Map.of()),
                contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, Dimension.SITE_ID, current, baseline, Map.of()),
                snapshots.dailySeries(tenant, MetricId.M01_DELAYED_TRIP_RATE, current));
    }

    public record DashboardCharts(MetricResult metric, MetricResult onTime,
                                  ContributionRanking vendors, ContributionRanking sites, MetricSeries trend) {}
}
