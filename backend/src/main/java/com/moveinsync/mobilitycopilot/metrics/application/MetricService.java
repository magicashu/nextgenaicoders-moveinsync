package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.time.LocalDate;

public interface MetricService {

    MetricResult query(MetricQuery query);

    default MetricResult delayedTripRate(TenantContext tenant, LocalDate asOfDate) {
        return query(MetricQuery.trailingWeekWithFourWeekBaseline(
                tenant,
                MetricId.M01_DELAYED_TRIP_RATE,
                asOfDate));
    }
}
