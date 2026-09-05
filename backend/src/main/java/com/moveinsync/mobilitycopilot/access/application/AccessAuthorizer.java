package com.moveinsync.mobilitycopilot.access.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

/** WS3: authorize before retrieval, cache lookup, resource lookup or action execution. */
public interface AccessAuthorizer {
    void require(ActorContext actor, TenantContext tenant, Permission permission);

    enum Permission { READ_ANALYTICS, EXPORT_REPORT, APPROVE_ACTION, CHANGE_CONFIGURATION }
}
