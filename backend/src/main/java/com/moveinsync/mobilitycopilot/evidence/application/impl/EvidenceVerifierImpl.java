package com.moveinsync.mobilitycopilot.evidence.application.impl;

import com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier;
import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Node 13 — deterministic evidence verifier.
 *
 * Checks every candidate claim against its cited evidence:
 *   V1 — Evidence existence: all evidenceIds in the claim must exist in the evidence list.
 *   V2 — Tenant match: cited evidence must belong to context.tenant().
 *   V3 — Data version match: cited evidence data version must match context.versions().data().
 *   V4 — Status coherence: a claim citing only UNAVAILABLE evidence is unverifiable.
 *   V5 — Window sanity: evidence window must not be null or inverted.
 *   V6 — Value presence: AVAILABLE evidence supporting a DIRECT claim must have a non-null value.
 *
 * At most one correction cycle: rejects only the failing claims, keeps the rest.
 * A failed verify never authorises an action — callers must check status != REJECTED.
 */
@Service
public final class EvidenceVerifierImpl implements EvidenceVerifier {

    @Override
    public VerificationResult verify(RunContext context, List<Claim> candidates,
                                     List<MetricEvidence> evidence) {
        Map<String, MetricEvidence> evidenceById = new LinkedHashMap<>();
        for (MetricEvidence e : evidence) evidenceById.put(e.evidenceId(), e);

        List<VerifiedClaim> verified = new ArrayList<>();
        Set<String> rejectedIds = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        for (Claim claim : candidates) {
            List<String> failures = checkClaim(claim, evidenceById, context);
            if (failures.isEmpty()) {
                verified.add(toVerified(claim, context));
            } else {
                rejectedIds.add(claim.claimId());
                failures.forEach(f -> warnings.add("verify rejected claim=" + claim.claimId() + ": " + f));
            }
        }

        VerificationResult.Status status;
        if (candidates.isEmpty()) {
            status = VerificationResult.Status.QUALIFIED;
        } else if (verified.isEmpty()) {
            status = VerificationResult.Status.REJECTED;
        } else if (!rejectedIds.isEmpty()) {
            status = VerificationResult.Status.QUALIFIED;
        } else {
            status = VerificationResult.Status.VERIFIED;
        }

        return new VerificationResult(status, List.copyOf(verified), rejectedIds, List.copyOf(warnings));
    }

    // -------------------------------------------------------------------------

    private List<String> checkClaim(Claim claim, Map<String, MetricEvidence> byId, RunContext ctx) {
        List<String> failures = new ArrayList<>();

        if (claim.evidenceIds() == null || claim.evidenceIds().isEmpty()) {
            failures.add("V1: no evidence IDs cited");
            return failures;
        }

        List<MetricEvidence> cited = new ArrayList<>();
        for (String eid : claim.evidenceIds()) {
            MetricEvidence e = byId.get(eid);
            if (e == null) {
                failures.add("V1: evidenceId=" + eid + " not found in evidence package");
            } else {
                cited.add(e);
            }
        }
        if (!failures.isEmpty()) return failures;

        for (MetricEvidence e : cited) {
            // V2: tenant
            if (!ctx.tenant().businessUnit().equals(e.request().tenant().businessUnit())) {
                failures.add("V2: tenant mismatch evidenceId=" + e.evidenceId()
                        + " expected=" + ctx.tenant().businessUnit()
                        + " got=" + e.request().tenant().businessUnit());
            }

            // V3: data version
            String expectedVer = ctx.versions().data();
            if (expectedVer != null && !expectedVer.equals(e.request().dataVersion())) {
                failures.add("V3: data version mismatch evidenceId=" + e.evidenceId()
                        + " expected=" + expectedVer + " got=" + e.request().dataVersion());
            }

            // V5: window sanity
            var w = e.request().window();
            if (w == null || w.start() == null || w.end() == null) {
                failures.add("V5: null window on evidenceId=" + e.evidenceId());
            } else if (w.start().isAfter(w.end())) {
                failures.add("V5: inverted window on evidenceId=" + e.evidenceId());
            }
        }

        // V4: all cited evidence UNAVAILABLE → unverifiable
        boolean allUnavailable = cited.stream()
                .allMatch(e -> MetricStatus.UNAVAILABLE.equals(e.status()));
        if (allUnavailable) {
            failures.add("V4: all cited evidence is UNAVAILABLE — claim cannot be verified");
        }

        // V6: DIRECT claim needs at least one AVAILABLE evidence with a value
        if (VerifiedClaim.Kind.DIRECT.equals(claim.kind())) {
            boolean hasValue = cited.stream().anyMatch(e ->
                    MetricStatus.AVAILABLE.equals(e.status()) && e.value() != null);
            if (!hasValue) {
                failures.add("V6: DIRECT claim has no AVAILABLE evidence with a numeric value");
            }
        }

        return failures;
    }

    private VerifiedClaim toVerified(Claim claim, RunContext ctx) {
        return new VerifiedClaim(
                claim.claimId(),
                ctx.tenant(),
                ctx.versions().data(),
                ctx.versions().metrics(),
                claim.text(),
                claim.evidenceIds(),
                claim.kind());
    }
}
