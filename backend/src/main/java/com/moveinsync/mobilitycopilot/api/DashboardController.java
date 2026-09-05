package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.reporting.application.SnapshotCache;
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
    private final AnalyticsStore analytics;
    private final AccessAuthorizer authorizer;
    private final SnapshotCache<Key, DashboardCharts> cache = new SnapshotCache<>(256);
    private record Key(String tenant, LocalDate asOf, String dataVersion, String contractVersion) {}
    private final MetricService metrics;
    private final MetricSnapshotService snapshots;
    private final ContributionService contributions;

    public DashboardController(RequestContext context, MetricService metrics,
                               MetricSnapshotService snapshots, ContributionService contributions,
                               AnalyticsStore analytics, AccessAuthorizer authorizer) {
        this.analytics = analytics; this.authorizer = authorizer;
        this.context = context; this.metrics = metrics; this.snapshots = snapshots; this.contributions = contributions;
    }

    @GetMapping
    public DashboardCharts charts(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var actor = context.actor(actorId, businessUnit, roles);
        var tenant = context.tenant(actor);
        authorizer.require(actor, tenant, Permission.READ_TENANT_METRICS);
        var key = new Key(tenant.businessUnit(), asOf, analytics.catalog().dataVersion(), MetricRegistry.CONTRACT_VERSION);
        var current = MetricWindow.trailingWeek(asOf);
        var baseline = MetricWindow.priorFourWeeks(current);
        return cache.get(key, false, () -> new DashboardCharts(
                metrics.delayedTripRate(tenant, asOf),
                metrics.query(MetricQuery.trailingWeekWithFourWeekBaseline(tenant, MetricId.M04_ON_TIME_PICKUP_RATE, asOf)),
                contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, Dimension.VENDOR_ID, current, baseline, Map.of()),
                contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, Dimension.SITE_ID, current, baseline, Map.of()),
                snapshots.dailySeries(tenant, MetricId.M01_DELAYED_TRIP_RATE, current),
                contributions.rankContributors(tenant, MetricId.M01_DELAYED_TRIP_RATE, Dimension.SHIFT_ID, current, baseline, Map.of()),
                metrics.query(MetricQuery.trailingWeekWithFourWeekBaseline(tenant, MetricId.M06_NO_SHOW_RATE, asOf)),
                metrics.query(MetricQuery.trailingWeekWithFourWeekBaseline(tenant, MetricId.M09_MEDIAN_COST_PER_TRIP, asOf))));
    }

    public record DashboardCharts(MetricResult metric, MetricResult onTime,
                                  ContributionRanking vendors, ContributionRanking sites, MetricSeries trend,
                                  ContributionRanking shifts, MetricResult noShow, MetricResult cost) {}
}
