package com.moveinsync.mobilitycopilot.ingestion.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Reads reviewed SQL from the classpath. No SQL is ever assembled from model text. */
public final class SqlResources {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private SqlResources() {
    }

    public static String read(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(key)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing classpath SQL resource: " + key);
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read SQL resource " + key, e);
            }
        });
    }
}
