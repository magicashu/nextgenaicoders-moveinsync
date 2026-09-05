package com.moveinsync.mobilitycopilot.api.security;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;

import java.util.Set;

/**
 * Resolves the trusted identity attached by the server edge into an actor. The REST layer reads it
 * from trusted headers set by the gateway or session layer; request bodies and query text are never a
 * source of tenant or role.
 */
public interface ActorResolver {

    ActorContext resolve(String subject, String businessUnit, Set<String> roles);
}
