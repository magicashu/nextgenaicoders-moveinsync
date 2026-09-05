package com.moveinsync.mobilitycopilot.access.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TrustedIdentity;

/** Turns a trusted edge identity into an actor context bound to exactly one registered tenant. */
public interface IdentityResolver {

    ActorContext resolve(TrustedIdentity identity);
}
