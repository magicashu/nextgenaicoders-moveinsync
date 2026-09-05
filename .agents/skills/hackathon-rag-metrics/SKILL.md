---
name: hackathon-rag-metrics
description: "Build or review the hackathon's evidence pipeline: governed SQL metrics, ACL-filtered hybrid retrieval, reranking, citations, and cross-domain synthesis. Use for RAG, search quality, metric contracts, or answers combining policy and operational data."
---

# Hackathon RAG and Governed Metrics

Keep two authorities separate: relational data computes governed operational metrics; document retrieval supplies policy evidence. The LLM may not invent either.

## Retrieval

1. Retain tenant, document ID, version, page/section, timestamps, and access metadata.
2. Apply tenant and ACL filters before candidates enter model context.
3. Run BM25 and vector retrieval, then fuse results with explicit evaluated configuration.
4. Retrieve broadly, rerank against the original query, and pass only measured best evidence to generation.
5. Generate claims with precise citations. Abstain on absent, stale, conflicting, unauthorized, or weak evidence.
6. Evaluate retrieval separately from generation.

## Metrics

1. Define versioned contracts: meaning, formula, grain, dimensions, units, owner, freshness, access policy, and edge-case rules.
2. Resolve metric and dimensions deterministically, validate compatibility, then run parameterized read-only SQL.
3. Return metric version, provenance, freshness, filters, and quality warnings.
4. Never ask an LLM to recreate a governed formula from prose.

Read [references/source-notes.md](references/source-notes.md) when tuning retrieval/reranking, designing evaluations, or defining semantic metrics.
