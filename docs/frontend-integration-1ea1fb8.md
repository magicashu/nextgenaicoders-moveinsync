# Frontend integration on try1

Adopted the visual frontend from main commit `1ea1fb8de5e46ab84196ebada960a5b3a1e41467` while preserving the current LangGraph4j, Sarvam and Langfuse backend implementation. The original palette, typography, dashboard/chart layouts, navigation, date picker, decision brief, audit/scorecard layouts and 3D view are retained. Added matching Ask Copilot and LLM & Trust pages.

The imported frontend's incompatible `/investigate` calls and simulated IDs, approvals, timings, confidence formula and evaluation passes were replaced. One shared tenant/run state connects the views. The new read-only `/api/v1/dashboard?asOf=...` uses the existing governed metric, contribution and daily-snapshot services, without additional LLM investigations. M04 supplies the pickup KPI; evidence confidence replaces the unsupported fleet-health score. Unavailable baselines stay null. Chart comparisons use the prior four weeks; supported investigation windows are seven days.

## Verification

- Backend package build passed with `-DskipTests`; frontend TypeScript and production Vite build passed. Test suites were not run, as requested.
- Browser verification on isolated local ports used the unchanged official dataset and explicit `LANGUAGE_MODEL=none`. It does not establish a live Sarvam call for this integration.
- Dashboard displayed M01 21.88%, baseline 12.28%, M04 74.23%, 17 vendor rows, eight sites and seven daily points for pinnacle-Slc, 1–7 June 2026.
- Run `69f649c5-0b0e-4402-98b2-b62fd7e0e80e` was visible in the dashboard, decision brief and 3D view. It initially had 16 recorded main nodes and awaited approval.
- Approving the explicitly local mock watchlist through the UI produced an EXECUTED receipt and all 18 main node records. Its audit view displayed 35 events. An attempt to read that run under vanta-Sea returned 404.
- Ask Copilot returned the supported high-volume-vendor explanation, evidence references and its own question trace.
- The 3D bundle loads on demand. Vite still reports a large-chunk warning for chart/3D dependencies; this is a build warning, not a failed build.

Start the updated backend and frontend using [the setup guide](try1-setup.md#dashboard-frontend-from-main). The user subsequently authorized publishing this implementation as the authoritative main-branch tree (D-048).
