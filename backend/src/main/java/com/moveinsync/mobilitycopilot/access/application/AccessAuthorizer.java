package com.moveinsync.mobilitycopilot.access.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

public interface AccessAuthorizer {

    void require(ActorContext actor, TenantContext tenant, Permission permission);
}
