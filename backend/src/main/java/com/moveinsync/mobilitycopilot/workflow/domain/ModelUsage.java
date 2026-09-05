package com.moveinsync.mobilitycopilot.workflow.domain;

/** Per-role model accounting. fallbackUsed = the deterministic implementation produced the output. */
public record ModelUsage(String role, String modelId, String promptVersion, long inputTokens, long outputTokens, long latencyMs, boolean fallbackUsed, String note) {
}
