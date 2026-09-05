package com.moveinsync.mobilitycopilot.evidence.domain;

import java.util.List;
import java.util.Set;

/** WS2: evidence presence alone does not imply claim support. */
public record VerificationResult(Status status, List<VerifiedClaim> claims,
                                 Set<String> rejectedClaimIds, List<String> warnings) {
    public enum Status { VERIFIED, QUALIFIED, REJECTED }
}
