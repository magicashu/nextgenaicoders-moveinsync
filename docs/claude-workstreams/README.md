# Claude Parallel Work Packets

Use these packets only after Phase 0 in `docs/parallel-delivery-plan.md` is green.

Current active integration branch: `Java-branch-2`. The first frozen metric-contract baseline is commit `d4aa14d`; use the latest Integration Owner-approved commit when starting a new worktree.

1. `00-integration-owner.md` — Codex Foundation & Integration coding series
2. `01-governed-analytics.md`
3. `02-agent-workflow.md`
4. `03-governance-actions.md`
5. `04-product-api.md`
6. `05-react-experience.md`
7. `06-quality-telemetry.md`

Codex follows packet `00` on `Java-branch-2`. Replace `<BASELINE_COMMIT>` and `<WORKTREE_PATH>` before sending packets `01`–`06` to Claude. Each Claude worker gets exclusive write ownership, commits only to its feature branch, and returns the required handoff. Workers never push or merge directly into `Java-branch-2`.

The user launches Claude sessions. Codex must not launch or direct Claude automatically. Codex continues packet `00`, reviews completed Claude branches when the user provides them, and performs integration on `Java-branch-2`.
