package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricSeries;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;

/** Daily snapshot reads for trend display and persistence checks. Supported: M01, M04, M05, M06, M07, M08, M17. */
public interface MetricSnapshotService {

    MetricSeries dailySeries(TenantContext tenant, MetricId metricId, MetricWindow window);
}
