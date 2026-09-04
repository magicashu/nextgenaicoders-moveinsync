# Retrieval, reranking, and metric source notes

## Sources reviewed

- [Weaviate hybrid search](https://docs.weaviate.io/weaviate/search/hybrid)
- [Cohere end-to-end RAG](https://docs.cohere.com/v2/docs/rag-complete-example)
- [Cohere reranking tutorial](https://docs.cohere.com/docs/reranking-with-cohere)
- [Cohere Rerank models](https://docs.cohere.com/v2/docs/rerank)
- [dbt Semantic Layer](https://docs.getdbt.com/docs/use-dbt-semantic-layer/dbt-sl)
- [MetricFlow](https://docs.getdbt.com/docs/build/about-metricflow)

## Decisions extracted

- Hybrid search fuses BM25F and vector results. Weight and fusion method change outcomes; record and evaluate them.
- Component scores are diagnostic evidence, not calibrated probabilities.
- First-stage retrieval optimizes recall; reranking optimizes final ordering. Preserve full provenance after reranking.
- Rerank supports lexical, vector, and semi-structured results. Respect context limits and benchmark latency/quality variants.
- Check citation correctness independently from answer relevance.
- A semantic layer centralizes definitions; authorization, freshness, version, and quality handling remain application responsibilities.
- Reject incompatible metric groupings rather than generating dubious SQL.

## Minimum evaluations

- Retrieval: evidence recall, MRR/nDCG, citation precision, duplicate rate, latency, cost, and ACL violations.
- Generation: groundedness, claim support, completeness, and abstention.
- Metrics: zero denominator, nulls, duplicates, cancellations, timezones, late data, versions, and dimension compatibility.

Return separate operational and policy evidence with provenance. Label conclusions as direct, inferred, or unsupported.
