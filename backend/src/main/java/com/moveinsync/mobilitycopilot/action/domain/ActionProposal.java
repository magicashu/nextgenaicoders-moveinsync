package com.moveinsync.mobilitycopilot.action.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** WS3: validate policy, exact target, evidence, version and expiry before storing a draft. */
public record ActionProposal(UUID actionId, UUID runId, long proposalVersion, String type,
                             String title, String rationale, String status, ActionTarget target,
                             String dataVersion, String metricVersion, Set<String> evidenceIds,
                             Map<String, String> parameters, Instant createdAt, Instant expiresAt) {}
