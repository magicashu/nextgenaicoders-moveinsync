# Evaluation source notes

## Sources reviewed

- [Langfuse datasets](https://langfuse.com/docs/evaluation/experiments/datasets)
- [Experiments via UI](https://langfuse.com/docs/evaluation/experiments/experiments-via-ui)
- [Code evaluators](https://langfuse.com/docs/evaluation/evaluation-methods/code-evaluators)
- [LLM-as-a-Judge](https://langfuse.com/docs/evaluation/evaluation-methods/llm-as-a-judge)

## Decisions extracted

- Datasets hold inputs plus optional expected outputs and metadata; version/tag them for reproducible comparisons.
- UI experiments suit prompt/model comparisons. Use SDK experiments for full application logic.
- Code evaluators are preferred for deterministic rules and structured outputs/tool arguments.
- LLM judges need a score type, rubric, mappings, representative tests, and monitoring. Their reasoning is diagnostic, not ground truth.
- Evaluate observations when a trace-level score would hide the failing step.
- Sanitize production-derived examples before adding them to datasets.

## Required categories

- Governed metrics and edge cases.
- Evidence and claim-supporting citations.
- Ambiguity, absent/conflicting/stale evidence.
- Malformed output, timeout, retries, and partial branch failure.
- Cross-tenant access, injection, unauthorized tools, and leakage.
- Approval rejection/expiry/edit, duplicate action, and crash/resume.

No cross-tenant leak, unauthorized action, unsupported citation, or wrong governed metric may pass.
