package com.moveinsync.mobilitycopilot.evidence.domain;

import java.util.List;

public record EvidenceBundle(
        List<EvidenceItem> items,
        double confidence,
        long coverage,
        List<String> caveats) {
}
