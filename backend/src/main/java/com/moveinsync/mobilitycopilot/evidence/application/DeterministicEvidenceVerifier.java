package com.moveinsync.mobilitycopilot.evidence.application;

import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Final authority for the scope, version, unit, value and citation checks. */
@Service
public final class DeterministicEvidenceVerifier implements EvidenceVerifier {
    private static final Pattern CAUSAL_LANGUAGE = Pattern.compile(
            "\\b(caused|cause|because of|due to|resulted in|led to)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUSIVE_VENDOR_LANGUAGE = Pattern.compile(
            "\\b(only|sole|single)\\s+vendor\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public VerificationResult verify(RunContext context, List<Claim> candidates,
                                     List<MetricEvidence> evidence) {
        Map<String, MetricEvidence> byId = index(evidence);
        List<VerifiedClaim> accepted = new ArrayList<>();
        Set<String> rejected = new HashSet<>();
        List<String> warnings = new ArrayList<>();

        for (Claim claim : candidates == null ? List.<Claim>of() : candidates) {
            List<MetricEvidence> cited = citedEvidence(context, claim, byId, rejected, warnings);
            if (cited.isEmpty()) {
                continue;
            }
            if (claim.kind() == VerifiedClaim.Kind.QUALIFIED_INFERENCE && cited.size() < 2) {
                rejected.add(claim.claimId());
                warnings.add("Claim " + claim.claimId()
                        + " needs at least current and comparison evidence for an inference.");
                continue;
            }
            if (CAUSAL_LANGUAGE.matcher(claim.text()).find()
                    || EXCLUSIVE_VENDOR_LANGUAGE.matcher(claim.text()).find()) {
                rejected.add(claim.claimId());
                warnings.add("Claim " + claim.claimId()
                        + " uses causal or exclusive vendor language that governed evidence cannot establish.");
                continue;
            }
            String metricVersion = cited.getFirst().metricVersion();
            if (cited.stream().anyMatch(item -> !metricVersion.equals(item.metricVersion()))) {
                rejected.add(claim.claimId());
                warnings.add("Claim " + claim.claimId() + " cites incompatible metric versions.");
                continue;
            }
            boolean partial = cited.stream().anyMatch(item -> item.status() == MetricStatus.PARTIAL);
            cited.stream().flatMap(item -> item.warnings() == null ? java.util.stream.Stream.<String>empty()
                            : item.warnings().stream())
                    .forEach(warning -> warnings.add("Claim " + claim.claimId() + " evidence caveat: " + warning));
            if (partial) {
                warnings.add("Claim " + claim.claimId() + " is qualified because its evidence is partial.");
            }
            accepted.add(new VerifiedClaim(claim.claimId(), context.tenant(), context.versions().data(),
                    metricVersion, claim.text(), claim.evidenceIds(),
                    partial ? VerifiedClaim.Kind.QUALIFIED_INFERENCE : claim.kind()));
        }
        VerificationResult.Status status = rejected.isEmpty()
                ? (warnings.isEmpty() ? VerificationResult.Status.VERIFIED : VerificationResult.Status.QUALIFIED)
                : (accepted.isEmpty() ? VerificationResult.Status.REJECTED : VerificationResult.Status.QUALIFIED);
        return new VerificationResult(status, List.copyOf(accepted), Set.copyOf(rejected), List.copyOf(warnings));
    }

    private Map<String, MetricEvidence> index(List<MetricEvidence> evidence) {
        Map<String, MetricEvidence> result = new HashMap<>();
        for (MetricEvidence item : evidence == null ? List.<MetricEvidence>of() : evidence) {
            if (item != null && item.evidenceId() != null && result.putIfAbsent(item.evidenceId(), item) != null) {
                throw new IllegalArgumentException("Duplicate evidence id: " + item.evidenceId());
            }
        }
        return result;
    }

    private List<MetricEvidence> citedEvidence(RunContext context, Claim claim,
                                                Map<String, MetricEvidence> byId, Set<String> rejected,
                                                List<String> warnings) {
        if (claim == null || blank(claim.claimId()) || blank(claim.text()) || claim.kind() == null
                || claim.evidenceIds() == null || claim.evidenceIds().isEmpty()) {
            rejected.add(claim == null ? "<missing>" : claim.claimId());
            warnings.add("A claim is missing its required text, type, or evidence references.");
            return List.of();
        }
        List<MetricEvidence> cited = new ArrayList<>();
        for (String id : claim.evidenceIds()) {
            MetricEvidence item = byId.get(id);
            if (item == null || !compatible(context, item)) {
                rejected.add(claim.claimId());
                warnings.add("Claim " + claim.claimId() + " has an invalid, unavailable, or scope-mismatched evidence reference.");
                return List.of();
            }
            cited.add(item);
        }
        return cited;
    }

    private boolean compatible(RunContext context, MetricEvidence item) {
        return item.request() != null && item.request().tenant() != null
                && item.request().tenant().equals(context.tenant())
                && context.versions() != null && context.versions().data() != null
                && context.versions().data().equals(item.request().dataVersion())
                && item.request().window() != null && item.request().window().start() != null
                && item.request().window().end() != null && !item.request().window().end().isAfter(context.asOfDate())
                && item.metricVersion() != null && context.versions().metrics() != null
                && context.versions().metrics().equals(item.metricVersion())
                && item.status() != MetricStatus.UNAVAILABLE && item.value() != null
                && item.unit() == item.request().metricId().unit();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
