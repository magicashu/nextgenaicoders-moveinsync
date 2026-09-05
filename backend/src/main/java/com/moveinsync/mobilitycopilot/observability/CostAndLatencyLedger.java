package com.moveinsync.mobilitycopilot.observability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-run counters for model calls, tokens, estimated cost and tool latency. Prices are configurable
 * estimates for the demo trust panel; they are diagnostics, never business facts.
 */
public final class CostAndLatencyLedger {

    /** Default estimate: USD per 1,000 tokens, input and output. Override per deployment. */
    public static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.0030");
    public static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.0150");

    private int modelCalls;
    private int fallbackCalls;
    private long inputTokens;
    private long outputTokens;
    private long modelLatencyMs;
    private int toolCalls;
    private int toolFailures;
    private long toolLatencyMs;
    private final Map<String, Integer> toolCallsByWorker = new LinkedHashMap<>();
    private final BigDecimal inputPrice;
    private final BigDecimal outputPrice;

    public CostAndLatencyLedger() {
        this(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE);
    }

    public CostAndLatencyLedger(BigDecimal inputPricePer1k, BigDecimal outputPricePer1k) {
        this.inputPrice = inputPricePer1k;
        this.outputPrice = outputPricePer1k;
    }

    public synchronized void recordModelCall(String role, String modelId, long input, long output, long latencyMs, boolean fallback) {
        if (fallback) {
            fallbackCalls++;
            return;
        }
        modelCalls++;
        inputTokens += Math.max(0, input);
        outputTokens += Math.max(0, output);
        modelLatencyMs += Math.max(0, latencyMs);
    }

    public synchronized void recordToolCall(String worker, long latencyMs, boolean ok) {
        toolCalls++;
        toolLatencyMs += Math.max(0, latencyMs);
        if (!ok) {
            toolFailures++;
        }
        toolCallsByWorker.merge(worker, 1, Integer::sum);
    }

    public synchronized BigDecimal estimatedCostUsd() {
        return BigDecimal.valueOf(inputTokens).multiply(inputPrice).add(BigDecimal.valueOf(outputTokens).multiply(outputPrice))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    public synchronized Map<String, String> attributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("modelCalls", String.valueOf(modelCalls));
        attributes.put("fallbackCalls", String.valueOf(fallbackCalls));
        attributes.put(TraceAttributes.INPUT_TOKENS, String.valueOf(inputTokens));
        attributes.put(TraceAttributes.OUTPUT_TOKENS, String.valueOf(outputTokens));
        attributes.put("modelLatencyMs", String.valueOf(modelLatencyMs));
        attributes.put("toolCalls", String.valueOf(toolCalls));
        attributes.put("toolFailures", String.valueOf(toolFailures));
        attributes.put("toolLatencyMs", String.valueOf(toolLatencyMs));
        attributes.put(TraceAttributes.ESTIMATED_COST_USD, estimatedCostUsd().toPlainString());
        toolCallsByWorker.forEach((worker, count) -> attributes.put("toolCalls." + worker, String.valueOf(count)));
        return attributes;
    }

    public int modelCalls() { return modelCalls; }
    public int fallbackCalls() { return fallbackCalls; }
    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
    public int toolCalls() { return toolCalls; }
    public int toolFailures() { return toolFailures; }
}
