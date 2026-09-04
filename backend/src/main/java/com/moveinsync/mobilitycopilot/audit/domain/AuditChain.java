package com.moveinsync.mobilitycopilot.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/** Tamper-evident hash chain: each event hash covers its content and the previous hash for the run. */
public final class AuditChain {

    public static final String GENESIS = "0".repeat(64);

    private AuditChain() {
    }

    public static String hash(AuditEvent event, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(previousHash.getBytes(StandardCharsets.UTF_8));
            digest.update(event.eventId().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(event.runId().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(event.businessUnit().getBytes(StandardCharsets.UTF_8));
            digest.update(event.eventType().getBytes(StandardCharsets.UTF_8));
            digest.update(event.occurredAt().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(event.traceId().getBytes(StandardCharsets.UTF_8));
            new TreeMap<>(event.payload()).forEach((k, v) -> digest.update((k + "=" + v + ";").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
