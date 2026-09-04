# Claude Parallel Work Packets

Use these packets only after Phase 0 in `docs/parallel-delivery-plan.md` is green.

1. `00-integration-owner.md`
2. `01-governed-analytics.md`
3. `02-agent-workflow.md`
4. `03-governance-actions.md`
5. `04-product-api.md`
6. `05-react-experience.md`
7. `06-quality-telemetry.md`

Replace `<BASELINE_COMMIT>` and `<WORKTREE_PATH>` before sending a packet. Each worker gets exclusive write ownership, commits only to its feature branch, and returns the required handoff. Workers never push or merge directly into `Java-branch`.
