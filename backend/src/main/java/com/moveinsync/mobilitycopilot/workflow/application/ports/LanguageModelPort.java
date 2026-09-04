package com.moveinsync.mobilitycopilot.workflow.application.ports;

import java.time.Duration;
import java.util.Optional;

/**
 * Bounded access to a language model for the four roles. The model receives only versioned prompts
 * and typed JSON payloads, returns text that the caller must parse and validate, and may be absent.
 * Deterministic behaviour must never depend on it.
 */
public interface LanguageModelPort {

    /** Returns empty when no model is configured or the call failed; callers fall back deterministically. */
    Optional<Completion> complete(Request request);

    String modelId();

    record Request(String role, String promptVersion, String systemPrompt, String userPayloadJson, int maxOutputTokens, Duration timeout) {
    }

    record Completion(String text, long inputTokens, long outputTokens, long latencyMs) {
    }

    /** Default: no model. Every role uses its deterministic implementation. */
    final class Unavailable implements LanguageModelPort {
        @Override
        public Optional<Completion> complete(Request request) {
            return Optional.empty();
        }

        @Override
        public String modelId() {
            return "none";
        }
    }
}
