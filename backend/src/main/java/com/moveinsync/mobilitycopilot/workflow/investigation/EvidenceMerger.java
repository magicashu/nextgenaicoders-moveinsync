package com.moveinsync.mobilitycopilot.workflow.investigation;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Node 11 — merge evidence from all completed investigation tasks.
 *
 * Deduplicates by evidenceId, retains partial failures with their warnings,
 * and surfaces quality/coverage gaps as merged warnings.
 */
@Component
public final class EvidenceMerger {

    public MergeResult merge(InvestigationResult result) {
        Map<String, MetricEvidence> byId = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>(result.warnings());

        for (MetricEvidence e : result.evidence()) {
            byId.putIfAbsent(e.evidenceId(), e);
        }

        // Surface any UNAVAILABLE evidence as explicit gaps
        for (MetricEvidence e : byId.values()) {
            if (MetricStatus.UNAVAILABLE.equals(e.status())) {
                warnings.add("gap: metric=" + e.request().metricId()
                        + " unavailable — " + String.join("; ", e.warnings()));
            }
        }

        if (!result.pendingTasks().isEmpty()) {
            warnings.add("incomplete investigation: " + result.pendingTasks().size()
                    + " task(s) not executed — evidence may be partial");
        }

        return new MergeResult(List.copyOf(byId.values()), List.copyOf(warnings));
    }

    public record MergeResult(List<MetricEvidence> evidence, List<String> warnings) {}
}
