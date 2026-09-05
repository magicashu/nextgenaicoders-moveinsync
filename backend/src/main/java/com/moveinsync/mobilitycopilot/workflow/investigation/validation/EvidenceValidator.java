package com.moveinsync.mobilitycopilot.workflow.investigation.validation;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates MetricEvidence returned by a worker before it is accepted into the investigation.
 * Checks tenant scope, data version, window integrity and status coherence.
 */
@Component
public final class EvidenceValidator {

    /**
     * Returns an empty list when evidence is valid.
     * Returns a non-empty list of rejection reasons when evidence must be rejected.
     */
    public List<String> validate(MetricEvidence evidence, RunContext context) {
        List<String> reasons = new ArrayList<>();

        if (evidence == null || evidence.request() == null || evidence.request().tenant() == null) {
            reasons.add("evidence is null");
            return reasons;
        }

        // Tenant scope — critical: evidence from another business unit must never be accepted
        String evidenceBu = evidence.request().tenant().businessUnit();
        String contextBu = context.tenant().businessUnit();
        if (!contextBu.equals(evidenceBu)) {
            reasons.add("tenant mismatch: expected=" + contextBu + " got=" + evidenceBu);
        }

        // Data version must match the run's pinned version
        String expectedDataVersion = context.versions().data();
        if (expectedDataVersion != null && !expectedDataVersion.equals(evidence.request().dataVersion())) {
            reasons.add("data version mismatch: expected=" + expectedDataVersion
                    + " got=" + evidence.request().dataVersion());
        }

        // Window must be non-null with a coherent date range
        var window = evidence.request().window();
        if (window == null) {
            reasons.add("metric window is null");
        } else if (window.start() == null || window.end() == null) {
            reasons.add("metric window has null start or end");
        } else if (window.start().isAfter(window.end())) {
            reasons.add("metric window start is after end: " + window.start() + " > " + window.end());
        }
        if (window != null && window.end() != null && window.end().isAfter(context.asOfDate())) reasons.add("Future evidence");
        if (evidence.request().metricId() == null || evidence.unit() != evidence.request().metricId().unit()) reasons.add("Metric unit mismatch");
        if (evidence.status() != MetricStatus.UNAVAILABLE && evidence.value() == null) reasons.add("Available evidence has no value");
        if (evidence.evidenceId() == null || evidence.evidenceId().isBlank()) reasons.add("Missing evidence identity");
        if (evidence.request().metricId() != null && !((evidence.request().metricId().contractId()+"-v1.1").equals(evidence.metricVersion())
                || context.versions().metrics().equals(evidence.metricVersion()))) reasons.add("Metric version mismatch");

        // UNAVAILABLE evidence must have no numeric value — a value on UNAVAILABLE is incoherent
        if (MetricStatus.UNAVAILABLE.equals(evidence.status()) && evidence.value() != null) {
            reasons.add("UNAVAILABLE evidence must not carry a numeric value");
        }

        // Status must be set
        if (evidence.status() == null) {
            reasons.add("evidence status is null");
        }

        // Warnings list must not be null (callers rely on iterating it)
        if (evidence.warnings() == null) {
            reasons.add("evidence warnings list is null");
        }

        return reasons;
    }

    public boolean isValid(MetricEvidence evidence, RunContext context) {
        return validate(evidence, context).isEmpty();
    }
}
