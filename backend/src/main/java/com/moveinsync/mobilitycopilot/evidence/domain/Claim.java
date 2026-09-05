package com.moveinsync.mobilitycopilot.evidence.domain;

import java.util.Set;

/** Candidate claim; never render as verified until the deterministic verifier accepts it. */
public record Claim(String claimId, String text, Set<String> evidenceIds, VerifiedClaim.Kind kind) {}
