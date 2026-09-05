---
name: hackathon-agent-evaluation
description: Create or run hackathon agent evaluations using Langfuse datasets, experiments, deterministic evaluators, and carefully scoped LLM judges. Use for golden cases, regression gates, configuration comparisons, or failure analysis.
---

# Hackathon Agent Evaluation

Evaluate the full system and components. Do not use one LLM judge score as proof of correctness.

## Workflow

1. Convert rubric and risks into named dimensions with pass criteria.
2. Build a versioned dataset with input, expected result/evidence, tenant/role context, tags, and failure category.
3. Include ordinary, boundary, adversarial, and degraded-dependency cases.
4. Use code evaluators for schemas, arithmetic, SQL safety, access control, citations, tool paths, approvals, idempotency, and latency.
5. Use LLM judges only for semantic relevance, explanation clarity, or groundedness. Give them an explicit rubric and inspect disagreements.
6. Run one controlled change per experiment and record model, prompt, workflow, metric, retrieval, and dataset versions.
7. Report per-category results. Block on security, authorization, citation, metric, or action-safety regressions.

Read [references/source-notes.md](references/source-notes.md) when designing datasets, experiments, or evaluator choice.
