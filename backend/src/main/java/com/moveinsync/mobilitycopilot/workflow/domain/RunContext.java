package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** WS3 authenticates/authorizes this context; WS2 propagates it to every node and tool. */
public record RunContext(UUID runId, ActorContext actor, TenantContext tenant, String persona,
                         LocalDate asOfDate, RunVersions versions, WorkflowBudget budget,
                         Instant deadline) {}
