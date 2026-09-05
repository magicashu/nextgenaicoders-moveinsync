package com.moveinsync.mobilitycopilot.anomaly.domain;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import java.util.List;
import java.util.Map;

/** WS1: populate severity/category/impact only from approved deterministic rules. */
public record AnomalyIssue(String issueId, TenantContext tenant, String dataVersion,
                           String title, String severity, String category,
                           List<MetricEvidence> evidence, Map<String, String> impactComponents,
                           List<String> caveats) {}
