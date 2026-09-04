---
name: hackathon-langfuse-observability
description: Instrument or audit the hackathon agent with Langfuse and OpenTelemetry. Use for trace design, nested agent/tool/retrieval spans, metadata, cost and latency capture, propagation, privacy, or demo observability.
---

# Hackathon Langfuse Observability

Treat one request as one root trace and each meaningful operation as a nested span. Observability explains execution; it does not replace evaluation or the business audit log.

## Workflow

1. Initialize instrumentation before model/framework clients and verify current SDK compatibility.
2. Use stable span names for authorization, supervisor, specialists, retrieval, reranking, SQL, verifier, approval, report, and action.
3. Propagate context through async work, parallel branches, queues, and service boundaries.
4. Attach safe metadata: environment, release, workflow/prompt/model/metric versions, tenant-safe ID, role, retries, retrieval configuration, and outcome.
5. Record latency, usage, cost, tool I/O, provenance/scores, errors, and evaluation scores. Redact secrets, PII, and unnecessary content.
6. Group multi-turn work with a session/thread ID. Flush exporters in short-lived scripts.
7. Verify the trace tree and rehearse opening one representative trace during the demo.

Read [references/source-notes.md](references/source-notes.md) for trace boundaries, metadata, propagation, or privacy behavior.
