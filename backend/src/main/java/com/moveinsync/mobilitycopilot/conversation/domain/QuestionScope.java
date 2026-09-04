package com.moveinsync.mobilitycopilot.conversation.domain;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic classification of a contextual question. Only allowlisted analytical intents reach the
 * workflow; anything asking for SQL, other tenants, external systems, or direct execution is refused
 * with a typed reason before any tool runs.
 */
public record QuestionScope(String intent, List<String> workers, boolean refused, String refusalReason, String normalisedQuestion) {

    public QuestionScope {
        Objects.requireNonNull(intent);
        workers = workers == null ? List.of() : List.copyOf(workers);
        normalisedQuestion = normalisedQuestion == null ? "" : normalisedQuestion;
    }

    public static QuestionScope refused(String reason, String question) {
        return new QuestionScope("REFUSED", List.of(), true, reason, question);
    }
}
