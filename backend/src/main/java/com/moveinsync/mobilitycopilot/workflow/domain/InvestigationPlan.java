package com.moveinsync.mobilitycopilot.workflow.domain;

import java.util.List;
import java.util.Set;

/** WS2: validate allowed workers, required comparisons and shared run limits. */
public record InvestigationPlan(String planId, String issueId, List<InvestigationTask> tasks,
                                Set<String> requiredEvidence, List<String> stopConditions) {}
