package com.moveinsync.mobilitycopilot.ingestion.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Data-version scoped profile of the loaded dataset. */
public record DataQualityReport(
        String dataVersion,
        List<FileProfile> files,
        List<DataQualityFinding> findings,
        Map<String, Double> joinCoverage,
        Map<String, Double> feedbackCoverageByTenant,
        Map<String, Double> zeroBilledKmShareByTenant) {

    public DataQualityReport {
        Objects.requireNonNull(dataVersion, "dataVersion is required");
        files = files == null ? List.of() : List.copyOf(files);
        findings = findings == null ? List.of() : List.copyOf(findings);
        joinCoverage = joinCoverage == null ? Map.of() : Map.copyOf(joinCoverage);
        feedbackCoverageByTenant = feedbackCoverageByTenant == null ? Map.of() : Map.copyOf(feedbackCoverageByTenant);
        zeroBilledKmShareByTenant = zeroBilledKmShareByTenant == null ? Map.of() : Map.copyOf(zeroBilledKmShareByTenant);
    }

    public boolean isPresent(DatasetFile file) {
        return files.stream().anyMatch(profile -> profile.file() == file && profile.present());
    }

    public long finding(String code) {
        return findings.stream().filter(f -> f.code().equals(code)).mapToLong(DataQualityFinding::affectedRows).findFirst().orElse(0L);
    }
}
