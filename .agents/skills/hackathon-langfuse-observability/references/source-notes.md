# Langfuse and OpenTelemetry source notes

## Sources reviewed

- [OpenTelemetry observability primer](https://opentelemetry.io/docs/concepts/observability-primer/)
- [Langfuse get started](https://langfuse.com/docs/observability/get-started)
- [Langfuse instrumentation](https://langfuse.com/docs/observability/sdk/instrumentation)
- [Langfuse trace best practices](https://langfuse.com/docs/observability/best-practices)

## Decisions extracted

- A trace is one end-to-end request; spans are individual operations with attributes, events, status, and parent context.
- Native and manual SDK/OpenTelemetry instrumentation can be mixed while preserving nesting.
- Use sessions to group turns or agent runs in a longer interaction.
- Name spans by operation type, not unique user content; put identifiers in attributes.
- Capture exceptions and status, but keep application recovery separate from telemetry export.
- Short-lived jobs must flush background batches.
- Redact or omit secrets/PII and honor the data boundary.
- Retention and sampling mean Langfuse is not the authoritative action ledger.

Required tree: `request -> authorize -> supervisor -> specialist(s) -> tool/retrieval/model -> verifier -> report -> approval -> execute`.

Include trace ID, safe tenant ID, role, workflow/metric/prompt versions, model, retrieval mode, evidence count, outcome, latency, usage, cost, retries, approval, and audit ID.
