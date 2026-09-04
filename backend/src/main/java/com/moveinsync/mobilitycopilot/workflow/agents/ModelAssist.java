package com.moveinsync.mobilitycopilot.workflow.agents;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.domain.ModelUsage;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shared bounded model call: one attempt plus one parse retry, strict JSON extraction, usage
 * accounting. Returns empty whenever the model is absent, times out, or produces unusable output,
 * so every role degrades to its deterministic implementation.
 */
final class ModelAssist {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LanguageModelPort model;
    private final Duration timeout;
    private final int maxOutputTokens;

    ModelAssist(LanguageModelPort model, Duration timeout, int maxOutputTokens) {
        this.model = model;
        this.timeout = timeout;
        this.maxOutputTokens = maxOutputTokens;
    }

    Optional<JsonNode> ask(String role, Object payload, Consumer<ModelUsage> usage) {
        if (model instanceof LanguageModelPort.Unavailable) {
            usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, 0, 0, 0, true, "no model configured"));
            return Optional.empty();
        }
        String system = PromptLibrary.load("v1", role);
        String userJson;
        try {
            userJson = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, 0, 0, 0, true, "payload serialisation failed"));
            return Optional.empty();
        }
        for (int attempt = 1; attempt <= 2; attempt++) {
            Optional<LanguageModelPort.Completion> completion;
            try {
                completion = model.complete(new LanguageModelPort.Request(role, RunContext.PROMPT_VERSION, system, userJson, maxOutputTokens, timeout));
            } catch (RuntimeException e) {
                usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, 0, 0, 0, true, "model call failed: " + e.getClass().getSimpleName()));
                return Optional.empty();
            }
            if (completion.isEmpty()) {
                usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, 0, 0, 0, true, "model unavailable"));
                return Optional.empty();
            }
            LanguageModelPort.Completion c = completion.get();
            Optional<JsonNode> parsed = parseJson(c.text());
            usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, c.inputTokens(), c.outputTokens(), c.latencyMs(),
                    parsed.isEmpty(), parsed.isEmpty() ? "malformed JSON on attempt " + attempt : "ok"));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    static Optional<JsonNode> parseJson(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.strip();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed.substring(start, end + 1));
            return node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
