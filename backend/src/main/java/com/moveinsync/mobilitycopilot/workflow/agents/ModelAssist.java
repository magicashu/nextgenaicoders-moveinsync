package com.moveinsync.mobilitycopilot.workflow.agents;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.ModelCallException;
import com.moveinsync.mobilitycopilot.workflow.domain.ModelUsage;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;

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

    Optional<JsonNode> ask(String role, Object payload, WorkflowRun run) {
        var output = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        var observationNumber = new java.util.concurrent.atomic.AtomicInteger();
        Consumer<ModelUsage> usage = value -> {
            run.addModelUsage(value);
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("businessUnit", run.state().tenant().businessUnit());
            attributes.put("model.role", role);
            attributes.put("gen_ai.request.model", value.modelId());
            attributes.put("gen_ai.provider.name", value.modelId().equals("none") ? "none" : "sarvam");
            attributes.put("gen_ai.usage.input_tokens", String.valueOf(value.inputTokens()));
            attributes.put("gen_ai.usage.output_tokens", String.valueOf(value.outputTokens()));
            attributes.put("langfuse.observation.usage_details", "{\"input\":" + value.inputTokens() + ",\"output\":" + value.outputTokens() + "}");
            attributes.put("model.fallback", String.valueOf(value.fallbackUsed()));
            attributes.put("model.note", value.note());
            attributes.put("promptVersion", value.promptVersion());
            attributes.put("model.attempt", String.valueOf(observationNumber.incrementAndGet()));
            if (payload instanceof Map<?, ?> inputs) {
                attributes.put("langfuse.observation.input", MAPPER.writeValueAsString(Map.of("fields", inputs.keySet(), "dataVersion", String.valueOf(run.context().dataVersion()))));
                if (inputs.get("task") instanceof Map<?, ?> task) attributes.put("worker", String.valueOf(task.get("worker")));
            }
            attributes.put("langfuse.observation.output", decisionSummary(output.get()));
            String prefix = value.modelId().equals("none") ? "fallback." : "llm.";
            run.emitTransition(new TransitionEvent(run.state().runId(), run.context().traceId(), run.currentNode(), prefix + role + "." + observationNumber.get(),
                    run.state().step(), run.state().step(), Instant.now().minusMillis(value.latencyMs()), value.latencyMs(),
                    value.fallbackUsed() ? "fallback" : "completed", com.moveinsync.mobilitycopilot.observability.Redaction.attributes(attributes)));
        };
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
            long started = System.nanoTime();
            Optional<LanguageModelPort.Completion> completion;
            try {
                completion = model.complete(new LanguageModelPort.Request(role, RunContext.PROMPT_VERSION, system, userJson, maxOutputTokens, timeout));
            } catch (RuntimeException e) {
                String reason = e instanceof ModelCallException failure ? failure.reason().name() : e.getClass().getSimpleName();
                org.slf4j.LoggerFactory.getLogger(ModelAssist.class).warn("Model fallback: role={} model={} reason={}", role, model.modelId(), reason);
                usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, 0, 0,
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), true, "model call failed: " + reason));
                return Optional.empty();
            }
            if (completion.isEmpty()) {
                usage.accept(new ModelUsage(role, model.modelId(), RunContext.PROMPT_VERSION, 0, 0, 0, true, "model unavailable"));
                return Optional.empty();
            }
            LanguageModelPort.Completion c = completion.get();
            Optional<JsonNode> parsed = parseJson(c.text());
            output.set(parsed.orElse(null));
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
        try {
            JsonNode node = MAPPER.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(trimmed);
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String decisionSummary(JsonNode output) {
        if (output == null) return "{\"status\":\"no usable model output\"}";
        var safe = MAPPER.createObjectNode();
        for (String key : java.util.List.of("action", "tasks", "overclaimClaimIds", "leadershipClaimIds")) {
            if (!output.has(key)) continue;
            if (key.equals("tasks")) {
                var workers = safe.putArray("proposedWorkers");
                output.path(key).forEach(task -> workers.add(task.path("worker").asText().substring(0, Math.min(80, task.path("worker").asText().length()))));
            } else safe.set(key, output.get(key));
        }
        return com.moveinsync.mobilitycopilot.observability.Redaction.text(MAPPER.writeValueAsString(safe));
    }
}
