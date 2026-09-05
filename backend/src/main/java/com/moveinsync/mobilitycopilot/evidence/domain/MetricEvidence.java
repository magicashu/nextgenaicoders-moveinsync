package com.moveinsync.mobilitycopilot.evidence.domain;

import com.moveinsync.mobilitycopilot.metrics.domain.*;
import java.math.BigDecimal;
import java.util.List;

/** WS1: return generic units; unavailable results carry no numeric value and explain why. */
public record MetricEvidence(String evidenceId, MetricRequest request, MetricStatus status,
                             BigDecimal value, MetricUnit unit, BigDecimal numerator,
                             BigDecimal denominator, long population, String metricVersion,
                             String sourceReference, List<String> warnings) {}
