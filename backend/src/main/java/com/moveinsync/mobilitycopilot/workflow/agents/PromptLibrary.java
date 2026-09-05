package com.moveinsync.mobilitycopilot.workflow.agents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Loads versioned prompts from the classpath. Prompts are runtime resources, never string literals. */
public final class PromptLibrary {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptLibrary() {
    }

    public static String load(String version, String role) {
        String key = "prompts/" + version + "/" + role + ".md";
        return CACHE.computeIfAbsent(key, k -> {
            try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(k)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing prompt resource " + k);
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read prompt " + k, e);
            }
        });
    }
}
