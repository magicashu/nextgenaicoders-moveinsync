---
name: mobility-react-experience
description: Change the React/TypeScript judge flow of the Mobility Decision Copilot (packet 05, feat/react-experience) - brief, evidence drawer, investigation, approval, audit, trust panel, ask drawer, fixtures and tests. Use for any frontend/** work.
---

# React experience (WS5)

Owned: `frontend/**` only. Dependencies stay React 19 + Vite + Vitest + Testing Library; no router, no UI kit, no chart library without an Integration Owner request.

## Structure
`src/core/contracts.ts` mirrors `ApiDtos`; `src/core/api.ts` exposes one `CopilotApi` with `httpApi` and `mockApi` (`VITE_USE_MOCKS=true`); `src/app/ApiContext.tsx` lets tests inject `mockApi`. Fixtures in `src/mocks/fixtures.ts` hold only hand-reconciled numbers (G1, G2, healthy, audit). Views are state-driven tabs in `App.tsx`: brief, investigation, approval, audit, trust, plus the evidence drawer and ask drawer.

## Rules
- No metric or business calculation in the browser: `shared/format.ts` formats only; expiry and status come from the API (`ApprovalPage` reflects `status === 'EXPIRED'`, never wall-clock, because the demo replays past dates).
- Every KPI/finding opens `EvidenceDrawer` (definition text from `shared/metricDefinitions.ts`, filters, population, window, contract/data version, source, caveats). Unsupported KPIs render greyed with the reason.
- Approval preview must show scope, evidence version/timestamp, consequence and expiry; results cover EXECUTED, REJECTED, EXPIRED and APPROVED_NOT_EXECUTED.
- Never render model text as HTML; audit payload keys like prompt/reasoning are hidden.
- Tests: `src/features/morning-brief/MorningBriefPage.test.tsx` covers the judge flow with `mockApi`; `src/test/setup.ts` registers `cleanup` (no vitest globals). Query headings by role because headline text also appears in the leadership narrative.

## Commands
`npm ci`, `npx tsc -b`, `npm test`, `npm run build` (run from `frontend/` or the root workspace).
