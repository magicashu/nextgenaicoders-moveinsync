---
name: hackathon-openkb-long-docs
description: Evaluate or integrate OpenKB/PageIndex as an optional long-document retrieval specialist behind LangGraph. Use only for structurally rich PDFs, policies, contracts, SOPs, or cross-section questions—not operational metrics or authorization.
---

# Hackathon OpenKB Long Documents

This skill is conditional. Do not replace primary hybrid RAG unless corpus-specific evaluation proves a material improvement.

## Workflow

1. Confirm the corpus contains long structured documents and section-aware questions.
2. Run locally first. Treat cloud OCR/indexing as an external processor needing data-boundary review.
3. Isolate KBs by tenant or authorize before any MCP/REST query can name a KB.
4. Wrap query as a narrow typed LangGraph tool. Do not expose write, sync, filesystem, or generic MCP operations without explicit need.
5. Preserve source ID, checksum/version, page/section, ingestion time, and retrieval path. Label summaries/concepts as derived.
6. Trace indexing/queries in Langfuse and provide a hybrid-RAG fallback.
7. Compare evidence recall, citations, groundedness, latency, cost, updates, isolation, and recovery. Keep it only if it wins.

Read [references/source-notes.md](references/source-notes.md) before ingestion, MCP/REST exposure, or judging vectorless claims.
