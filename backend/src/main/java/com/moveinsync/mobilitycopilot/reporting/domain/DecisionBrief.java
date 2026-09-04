package com.moveinsync.mobilitycopilot.reporting.domain;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DecisionBrief(
        UUID runId,
        String businessUnit,
        LocalDate asOfDate,
        String headline,
        MetricResult metric,
        List<String> findings,
        ActionProposal recommendedAction,
        EvidenceBundle evidence,
        String status) {
}
