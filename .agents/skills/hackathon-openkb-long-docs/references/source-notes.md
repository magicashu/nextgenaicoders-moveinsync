# OpenKB and PageIndex source notes

## Sources reviewed

- [VectifyAI OpenKB](https://github.com/VectifyAI/OpenKB)
- [PageIndex documentation](https://docs.pageindex.ai/)
- [Agentic vectorless RAG cookbook](https://docs.pageindex.ai/cookbook/agentic-vectorless-rag-pageindex)

## Decisions extracted

- OpenKB compiles sources into Markdown, summaries, concepts, and links; long PDFs use PageIndex above a configurable threshold.
- PageIndex uses hierarchical reasoning-based retrieval without a vector DB. This is a different method, not proof of superiority.
- OpenKB exposes CLI, REST, WebSocket, and MCP including writes; expose an allowlisted read-only subset.
- Local mode avoids cloud transfer but may have weaker OCR; cloud mode adds a privacy/vendor dependency.
- Generated summaries/concepts may compound errors. Original pages remain authoritative.
- Updates need invalidation/recompilation and provenance. Test versions, deletion, partial reindexing, stale concepts, and injected content.

Use only when long-document evaluation shows a rubric-relevant gain. Never use it for governed SQL, live state, authorization, approval, or audit.
