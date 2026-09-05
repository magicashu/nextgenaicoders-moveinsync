package com.moveinsync.mobilitycopilot.evidence.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Merged, versioned evidence for one run. The bundle is the frozen shared shape; claims, gaps and
 * branch status are the workflow's typed additions. The evidence version is a content hash so a
 * post-approval revalidation can prove nothing changed.
 */
public record EvidencePackage(
        EvidenceBundle bundle,
        List<Claim> claims,
        List<String> capabilityGaps,
        List<String> dataQualityNotes,
        Map<String, String> branchStatus,
        List<String> contradictions,
        String evidenceVersion) {

    public EvidencePackage {
        Objects.requireNonNull(bundle);
        claims = claims == null ? List.of() : List.copyOf(claims);
        capabilityGaps = capabilityGaps == null ? List.of() : List.copyOf(capabilityGaps);
        dataQualityNotes = dataQualityNotes == null ? List.of() : List.copyOf(dataQualityNotes);
        branchStatus = branchStatus == null ? Map.of() : Map.copyOf(branchStatus);
        contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
        Objects.requireNonNull(evidenceVersion);
    }

    public Optional<EvidenceItem> item(String evidenceId) {
        return bundle.items().stream().filter(i -> i.evidenceId().equals(evidenceId)).findFirst();
    }

    public EvidencePackage withClaims(List<Claim> replacement) {
        return new EvidencePackage(bundle, replacement, capabilityGaps, dataQualityNotes, branchStatus, contradictions, evidenceVersion);
    }

    /** Content hash of evidence ids, values and data versions in a stable order. */
    public static String version(EvidenceBundle bundle) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            bundle.items().stream()
                    .sorted(java.util.Comparator.comparing(EvidenceItem::evidenceId))
                    .forEach(i -> digest.update((i.evidenceId() + "=" + i.value() + "|" + i.baselineValue() + "|" + i.dataVersion() + ";")
                            .getBytes(StandardCharsets.UTF_8)));
            return "evidence-" + HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
