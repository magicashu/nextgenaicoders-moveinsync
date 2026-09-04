package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.time.LocalDate;

public interface MetricService {

    MetricResult delayedTripRate(TenantContext tenant, LocalDate asOfDate);
}
