package com.moveinsync.mobilitycopilot.evidence.application;

import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic claim verification (architecture plan section 13). It never consults a model:
 * numbers must exist in cited evidence, causal language is rejected, single-vendor blame requires a
 * ranking in which not every qualified vendor deteriorated, and known gaps must be caveated.
 */
@Component
public class EvidenceVerifier {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\w-])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?");
    private static final Pattern CAUSAL = Pattern.compile("\\b(caused|causes|because of|due to|responsible for|to blame)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VENDOR_BLAME = Pattern.compile("\\b(vendor|travel)\\b.*\\b(is the (main|primary|sole) (driver|cause|reason)|blame|escalat)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> IGNORED_NUMBERS = Set.of("1", "2", "3", "4", "5", "7", "10", "28", "500", "300", "1000", "1,000", "600", "1,440", "2026");

    public VerificationResult verify(EvidencePackage evidence, List<WorkerEvidenceDto.Ranking> vendorRankings, List<String> knownCaveats) {
        List<VerificationResult.Violation> violations = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Set<String> allowedNumbers = allowedNumbers(evidence);

        for (Claim claim : evidence.claims()) {
            if (claim.kind() == Claim.Kind.CAVEAT) {
                continue;
            }
            if (claim.kind() != Claim.Kind.RECOMMENDATION && claim.evidenceIds().isEmpty()) {
                violations.add(new VerificationResult.Violation("NO_EVIDENCE", claim.claimId(), "Claim cites no evidence: " + claim.text()));
                removed.add(claim.claimId());
                continue;
            }
            for (String id : claim.evidenceIds()) {
                if (evidence.item(id).isEmpty() && !isBundleLevelReference(id, evidence)) {
                    violations.add(new VerificationResult.Violation("UNKNOWN_EVIDENCE", claim.claimId(), "Unknown evidence id " + id));
                    removed.add(claim.claimId());
                }
            }
            Matcher numbers = NUMBER.matcher(claim.text());
            while (numbers.find()) {
                String token = numbers.group();
                String normalised = token.replace(",", "");
                if (IGNORED_NUMBERS.contains(token) || IGNORED_NUMBERS.contains(normalised) || isDateFragment(claim.text(), numbers.start())) {
                    continue;
                }
                if (!allowedNumbers.contains(normalised) && !allowedNumbers.contains(stripTrailingZero(normalised))) {
                    violations.add(new VerificationResult.Violation("UNSUPPORTED_NUMBER", claim.claimId(),
                            "Number " + token + " does not resolve to cited evidence"));
                    if (!removed.contains(claim.claimId())) {
                        removed.add(claim.claimId());
                    }
                }
            }
            if (CAUSAL.matcher(claim.text()).find()) {
                violations.add(new VerificationResult.Violation("CAUSAL_LANGUAGE", claim.claimId(),
                        "Use 'contributed', 'associated' or 'coincided' instead of causal wording"));
                if (!removed.contains(claim.claimId())) {
                    removed.add(claim.claimId());
                }
            }
            if (VENDOR_BLAME.matcher(claim.text()).find() && !singleVendorBlameSupported(vendorRankings)) {
                violations.add(new VerificationResult.Violation("VENDOR_BLAME", claim.claimId(),
                        "Single-vendor blame is not supported: every qualified vendor moved together or no vendor is qualified"));
                if (!removed.contains(claim.claimId())) {
                    removed.add(claim.claimId());
                }
            }
        }
        for (String gap : evidence.capabilityGaps()) {
            boolean caveated = evidence.claims().stream().anyMatch(c -> c.kind() == Claim.Kind.CAVEAT && c.text().contains(gap))
                    || knownCaveats.stream().anyMatch(c -> c.contains(gap)) || evidence.bundle().caveats().stream().anyMatch(c -> c.contains(gap));
            if (!caveated) {
                violations.add(new VerificationResult.Violation("WARN_MISSING_CAVEAT", null, "Capability gap without caveat: " + gap));
            }
        }
        BigDecimal confidence = confidence(evidence, violations);
        List<String> components = List.of(
                "evidence items=" + evidence.bundle().items().size(),
                "failed branches=" + evidence.branchStatus().values().stream().filter(s -> !s.equals("COMPLETE")).count(),
                "capability gaps=" + evidence.capabilityGaps().size(),
                "removed claims=" + removed.size());
        return new VerificationResult(violations.stream().noneMatch(v -> !v.code().startsWith("WARN")), violations, removed, confidence, components);
    }

    public static boolean singleVendorBlameSupported(List<WorkerEvidenceDto.Ranking> vendorRankings) {
        if (vendorRankings == null || vendorRankings.isEmpty()) {
            return false;
        }
        WorkerEvidenceDto.Ranking ranking = vendorRankings.getFirst();
        List<WorkerEvidenceDto.Ranking.Row> qualified = ranking.qualifiedRows();
        if (qualified.size() < 2) {
            return false;
        }
        long deteriorated = qualified.stream().filter(r -> r.delta() != null && r.delta().signum() > 0).count();
        return deteriorated == 1;
    }

    private static boolean isBundleLevelReference(String id, EvidencePackage evidence) {
        return id.equals(evidence.evidenceVersion());
    }

    private static Set<String> allowedNumbers(EvidencePackage evidence) {
        Set<String> allowed = new HashSet<>();
        for (EvidenceItem item : evidence.bundle().items()) {
            for (BigDecimal value : new BigDecimal[] {item.value(), item.baselineValue(), item.delta(), item.numerator(), item.denominator()}) {
                if (value != null) {
                    addForms(allowed, value);
                }
            }
            addForms(allowed, BigDecimal.valueOf(item.supportingCount()));
            if (item.delta() != null) {
                addForms(allowed, item.delta().abs());
            }
        }
        return allowed;
    }

    private static void addForms(Set<String> allowed, BigDecimal value) {
        BigDecimal abs = value.abs();
        allowed.add(abs.stripTrailingZeros().toPlainString());
        allowed.add(abs.setScale(0, RoundingMode.HALF_UP).toPlainString());
        allowed.add(abs.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        allowed.add(abs.setScale(2, RoundingMode.HALF_UP).toPlainString());
        allowed.add(abs.setScale(1, RoundingMode.HALF_UP).toPlainString());
        allowed.add(abs.toPlainString());
        if (abs.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            // rounded to the nearest hundred for narrative use, e.g. "about 1,900"
            allowed.add(abs.setScale(-2, RoundingMode.HALF_UP).toPlainString());
        }
    }

    private static String stripTrailingZero(String number) {
        try {
            return new BigDecimal(number).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return number;
        }
    }

    private static boolean isDateFragment(String text, int position) {
        int end = Math.min(text.length(), position + 12);
        String window = text.substring(position, end);
        return window.matches("\\d{4}-\\d{2}-\\d{2}.*") || (position >= 5 && text.substring(position - 5, position).matches(".*\\d{4}-$"))
                || (position >= 8 && text.substring(position - 8, position).matches(".*\\d{4}-\\d{2}-$"))
                || window.matches("\\d{2}:\\d{2}.*") || (position >= 3 && text.substring(position - 3, position).matches("\\d{2}:"));
    }

    private static BigDecimal confidence(EvidencePackage evidence, List<VerificationResult.Violation> violations) {
        BigDecimal base = BigDecimal.valueOf(evidence.bundle().confidence());
        long failed = evidence.branchStatus().values().stream().filter(s -> !s.equals("COMPLETE")).count();
        BigDecimal penalty = BigDecimal.valueOf(0.1).multiply(BigDecimal.valueOf(failed))
                .add(BigDecimal.valueOf(0.05).multiply(BigDecimal.valueOf(evidence.capabilityGaps().size())))
                .add(BigDecimal.valueOf(0.05).multiply(BigDecimal.valueOf(violations.stream().filter(v -> !v.code().startsWith("WARN")).count())));
        return base.subtract(penalty).max(BigDecimal.valueOf(0.05)).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
    }
}
