package com.moveinsync.mobilitycopilot.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic redaction applied to every span attribute, event payload and log line before it
 * leaves the process. It removes secrets, PII and free-form model text. Rider identifiers (stwid)
 * are tokenized rather than dropped so joins in diagnostics remain possible without exposing people.
 */
public final class Redaction {

    public static final String REDACTED = "[redacted]";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(\\+?\\d[\\d\\s().-]{8,}\\d)(?!\\d)");
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(sk|pk)-(lf|proj|live|test)?-?[A-Za-z0-9_-]{8,}|\\b(bearer|basic)\\s+[A-Za-z0-9+/=._-]{12,}|\\bAKIA[0-9A-Z]{16}\\b");
    private static final Pattern JDBC_PASSWORD = Pattern.compile("(?i)(password=)[^&\\s;]+");
    private static final Set<String> FORBIDDEN_KEYS = Set.of("prompt", "completion", "system_prompt", "user_payload", "chain_of_thought", "reasoning",
            "raw_output", "authorization", "password", "secret", "api_key", "apikey", "token", "gen_ai.prompt", "gen_ai.completion");
    private static final Set<String> TOKENIZED_KEYS = Set.of("stwid", "rider_id", "employee_id", "emp_id", "actor_email");

    private Redaction() {
    }

    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String out = SECRET.matcher(value).replaceAll(REDACTED);
        out = JDBC_PASSWORD.matcher(out).replaceAll("$1" + REDACTED);
        out = EMAIL.matcher(out).replaceAll(REDACTED);
        out = PHONE.matcher(out).replaceAll(REDACTED);
        return out.length() > 2_000 ? out.substring(0, 2_000) + "…[truncated]" : out;
    }

    /** Drops forbidden keys, tokenizes person identifiers and redacts every remaining value. */
    public static Map<String, String> attributes(Map<String, ?> raw) {
        Map<String, String> safe = new LinkedHashMap<>();
        if (raw == null) {
            return safe;
        }
        for (Map.Entry<String, ?> entry : raw.entrySet()) {
            String key = entry.getKey();
            String lower = key.toLowerCase(java.util.Locale.ROOT);
            if (FORBIDDEN_KEYS.stream().anyMatch(lower::endsWith)) {
                continue;
            }
            String value = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            if (TOKENIZED_KEYS.stream().anyMatch(lower::endsWith)) {
                safe.put(key, token(value));
            } else {
                safe.put(key, text(value));
            }
        }
        return safe;
    }

    /** Stable one-way token so the same person maps to the same opaque id inside one deployment. */
    public static String token(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("mobility-copilot|" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "tok_" + java.util.HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isForbiddenKey(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return FORBIDDEN_KEYS.stream().anyMatch(lower::endsWith);
    }
}
