package com.moveinsync.mobilitycopilot.metrics.application;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;

/** WS1 target boundary. No production implementation is registered by the M01 sample. */
public interface GovernedMetricService {
    MetricEvidence compute(MetricRequest request);
    default java.util.List<MetricEvidence> computeGrouped(MetricRequest request, String dimension) {
        if (dimension != null) throw new UnsupportedOperationException("Grouped analytics not implemented");
        return java.util.List.of(compute(request));
    }
}
