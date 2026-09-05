package com.moveinsync.mobilitycopilot.ingestion.domain;

import java.util.List;
import java.util.Map;

/** WS1: distinguish source counts, canonical rows, unmatched rows and overlapping quality flags. */
public record DatasetProfile(String dataVersion, String parserVersion, List<FileProfile> files,
                             List<String> warnings) {
    public record FileProfile(String sourceId, String sha256, long sourceRows, long parsedRows,
                              long parseRejectedRows, long canonicalRows, long duplicateRows,
                              long unmatchedRows, Map<String, Long> qualityReasonCounts) {}
}
