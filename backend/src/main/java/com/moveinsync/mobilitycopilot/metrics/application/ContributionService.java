package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;

import java.util.List;
import java.util.Map;

/**
 * Governed contribution tools used by the seven investigation workers. Every call is tenant-scoped,
 * dimension-allowlisted and returns provenance. This is the deterministic half of
 * {@code rank_contributors}, {@code get_distribution} and {@code compare_metric}.
 */
public interface ContributionService {

    ContributionRanking rankContributors(TenantContext tenant, MetricId metricId, Dimension dimension,
                                         MetricWindow current, MetricWindow baseline, Map<String, String> filters);

    /** Delay-reason mix (M03) for the current and baseline windows. */
    Distribution delayReasonMix(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters);

    /** Alert mix by event type including excluded types so regime changes remain visible. */
    Distribution alertTypeMix(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters);

    /** Trip and rider-leg counts on delayed trips; input to the deterministic impact estimate. */
    ImpactCounts delayedRiderLegs(TenantContext tenant, MetricWindow window, Map<String, String> filters);

    /** Cross-tenant M01 peers for one window. Callers must restrict this to the facilities-head persona. */
    List<PeerValue> crossTenantDelayedTripRate(MetricWindow window);

    record ImpactCounts(long trips, long delayedTrips, long delayedRiderLegs, long riderLegs) {
    }

    record PeerValue(String businessUnit, long numerator, long denominator, java.math.BigDecimal value) {
    }
}
