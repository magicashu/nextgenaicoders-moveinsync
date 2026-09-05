# UI Requirements — MoveInSync AI Investigation Platform
**Version:** 1.0 | **Date:** 2026-09-05 | **Stack:** React + TypeScript + 3D.js (Three.js / React Three Fiber)

---

## 1. Overview

A single-page investigation dashboard that visualises the 4-agent, 18-node workflow as a live 3-D graph, exposes metric evidence, and lets an operations user review and approve/reject action proposals — all while keeping tenants strictly isolated.

---

## 2. Pages / Views

### 2.1 Landing — Tenant & Run Selector
| Element | Details |
|---|---|
| Tenant picker | Dropdown: pinnacle-Slc, vanta-Sea, vanta-Aus, catalyst-Sac, orbit-Slc |
| Date-range picker | Defaults to May–July 2026 dataset window |
| Active runs list | Card grid — runId, businessUnit, finalStep, timestamp |
| New investigation CTA | Opens investigation form (question + context) |

### 2.2 Investigation Form
- Plain-text question input (e.g. "Why did delays spike this week?")
- Optional audience selector (Ops / Finance / Executive)
- Submit → triggers `/api/run/start`; transitions to Run View

### 2.3 Run View (primary view)
Split layout:
- **Left (60%)** — 3-D workflow graph (see §4)
- **Right (40%)** — Evidence panel + agent log stream

### 2.4 Decision Brief
Full-width view once `finalStep = AWAITING_APPROVAL`:
- Structured brief rendered from Agent 4 output
- Action proposal card with Approve / Reject buttons
- Caveat badges (G2-style degraded-data warnings)

### 2.5 Audit Trail
Read-only table: runId, tenantScope, event, actor, timestamp — scoped per tenant.

### 2.6 Scorecard / Eval Dashboard
Displays `scorecard.json` gates and zero-tolerance metrics as live gauges.

---

## 3. Component Inventory

### 3.1 Navigation
- `TopNav` — logo, tenant badge, user avatar
- `SideNav` — Runs | Brief | Audit | Scorecard

### 3.2 Tenant Isolation Guard
- `TenantBoundary` wrapper — prevents any cross-tenant data render; mirrors SEC-TENANT-001/002/003 test expectations

### 3.3 Run Status Components
- `RunStatusBadge` — INITIALIZE_RUN → … → AWAITING_APPROVAL (maps 18 nodes to colour-coded chips)
- `AgentRoleBadge` — Supervisor / Investigator / Evidence Critic / Briefing & Action

### 3.4 Metric Cards (M01–M18)
Each card shows: metric ID, value, baseline, delta, eligibility note.
Special cases:
- M01 Delayed Trip Rate — highlighted when delta > 10 pp (G1 condition)
- M05 Cost-per-km — "UNSUPPORTED" banner when zero-km rows present (F05 condition)
- M12 Marshal Rating — note when 0-rating rows excluded (F10 condition)

### 3.5 Evidence Panel
- `EvidenceList` — ordered list of evidence items from run artifact
- `EvidenceItem` — metric name, value, source worker, caveat flag
- `CritiqueResultBadge` — passed / flagged / unsupported-claim

### 3.6 Action Proposal Card
- Template-rendered action text (no AI-free-form output shown raw)
- `ApproveButton` / `RejectButton` — calls `/api/run/{id}/approve` or `/api/run/{id}/reject`
- Confirmation modal before any state-mutating action

### 3.7 Audit Table
- Columns: Event, Agent Role, Tenant, Timestamp, Run ID
- Filter by tenant, date range, event type

### 3.8 Scorecard Gauges
- Gate pills: G1 / G2 / G3 / SEC / AUDIT — PASS / FAIL
- Zero-tolerance counters: cross-tenant leaks, unsupported numbers, unauthorized actions (must display 0)
- Latency meters: P50, P95, Max

---

## 4. 3-D Workflow Graph (Three.js / React Three Fiber)

### 4.1 Graph Structure
Render the 18 workflow nodes as 3-D spheres connected by directed edges.

**Nodes (18):**
```
INITIALIZE_RUN → SUPERVISOR_PLAN → INVESTIGATOR_EXECUTE →
VALIDATE_EVIDENCE → CRITIC_REVIEW → CORRECT_CYCLE (loop back) →
BUDGET_CHECK → TIMEOUT_GUARD → TENANT_SCOPE_CHECK →
WORKER_VENDOR → WORKER_SITE_SHIFT → WORKER_DELAY_REASON →
WORKER_COST_BILLING → WORKER_FEEDBACK → WORKER_TRACKING_SAFETY →
WORKER_NO_SHOW → BRIEF_DRAFT → APPROVAL_INTERRUPT
```

### 4.2 Visual Design
| Property | Value |
|---|---|
| Background | Deep space black `#050510` |
| Node resting colour | Muted blue `#1e3a5f` |
| Node active colour | Bright cyan `#00e5ff` with bloom glow |
| Node completed colour | Green `#00c853` |
| Node failed colour | Red `#ff1744` |
| Edge colour | Semi-transparent white `rgba(255,255,255,0.15)` |
| Active edge | Animated particle flow (cyan dots travelling along edge) |
| Agent grouping | 4 cluster rings: Supervisor (top), Investigator (mid-left), Critic (mid-right), Briefing (bottom) |

### 4.3 Interactions
- Orbit controls — mouse drag to rotate, scroll to zoom
- Click node → side panel shows node detail (inputs, outputs, latency, tool calls used)
- Hover node → tooltip: node name, agent responsible, status, duration
- Timeline scrubber — replay a completed run step-by-step
- Live mode toggle — real-time SSE updates move the active node highlight

### 4.4 Animated Transitions
- Node state change → scale pulse + colour lerp
- New edge traversal → particle burst at source, trail to target
- Correction cycle (CORRECT_CYCLE node) → red warning flash + loop arrow animation
- APPROVAL_INTERRUPT → golden shimmer + pause indicator

### 4.5 Performance
- Max nodes rendered: 18 (fixed); max edges: ~30
- Use `InstancedMesh` for particles on edges
- LOD: reduce geometry detail below 10px screen size

---

## 5. Data Contracts (API ↔ UI)

### 5.1 Endpoints consumed
| Method | Path | Used by |
|---|---|---|
| GET | `/api/runs?tenant={t}` | Landing run list |
| POST | `/api/run/start` | Investigation form |
| GET | `/api/run/{id}` | Run view polling / SSE |
| POST | `/api/run/{id}/approve` | Decision brief |
| POST | `/api/run/{id}/reject` | Decision brief |
| GET | `/api/run/{id}/audit` | Audit trail |
| GET | `/api/scorecard` | Scorecard page |

### 5.2 Key response shapes (from existing artifacts)
```typescript
interface RunArtifact {
  runId: string;
  businessUnit: string;           // tenant — NEVER mix across tenants
  finalStep: WorkflowNode;
  transitions: NodeTransition[];  // 18 nodes
  toolCallsUsed: number;          // max 12
  correctionCycles: number;       // max 1
  evidence: EvidenceItem[];
  brief?: DecisionBrief;
  actionProposal?: ActionProposal;
}

interface MetricResult {
  id: string;                     // M01–M18
  value: number | "UNSUPPORTED";
  baseline: number;
  delta: number;
  eligibilityNote?: string;
  caveats?: string[];
}
```

---

## 6. Security & Tenant Isolation Requirements

- All API calls MUST include `X-Tenant-Id` header matching the selected tenant
- UI must refuse to render data whose `businessUnit` ≠ active tenant (mirrors SEC-TENANT-001)
- Approve/Reject buttons disabled if `runId` tenant ≠ active tenant
- No tenant identifier stored in `localStorage` — session-scoped only
- Audit trail filtered server-side AND client-side by tenant

---

## 7. Golden Case Visual Indicators

| Case | Trigger | UI Indicator |
|---|---|---|
| G1 — Delay spike | M01 delta ≥ 20 pp | Red banner on metric card + node WORKER_SITE_SHIFT glows amber |
| G2 — Degraded data | `coverage < threshold` caveat present | Yellow degraded-data ribbon across brief panel |
| G3 — False alert | regime-change classification in evidence | Blue "correctly classified" badge — no escalation styling |

---

## 8. Evaluation / Scorecard View Details

Display results from `scorecard.json` live:

- **Gate summary strip** — G1 / G2 / G3 / SEC / AUDIT pills (green = PASS)
- **Zero-tolerance panel** — 6 counters must all read `0`
- **Performance panel** — model calls, fallback calls, tool calls (bar chart), latency histogram
- **DS scenario table** — DS-01 through DS-20 pass/fail grid (from `dataset-scenarios.md`)

---

## 9. Responsive & Accessibility

- Minimum viewport: 1280 × 800 (operations workstation)
- 3-D graph degrades gracefully: fallback 2-D force-directed graph if WebGL unavailable
- WCAG AA colour contrast for all text over dark backgrounds
- Keyboard navigation for approve/reject confirmation flow
- Screen-reader labels on all status badges

---

## 10. Tech Stack

| Layer | Choice |
|---|---|
| Framework | React 18 + TypeScript |
| 3-D | Three.js via `@react-three/fiber` + `@react-three/drei` |
| Post-processing | `@react-three/postprocessing` (bloom on active nodes) |
| State | Zustand (tenant context, run state) |
| Data fetching | TanStack Query v5 |
| Styling | Tailwind CSS (dark theme) |
| Charts (2-D) | Recharts (metric trend lines, latency histogram) |
| Testing | Vitest + React Testing Library |
| SSE / real-time | Native `EventSource` API |

---

## 11. Out of Scope (v1)

- Multi-tenant comparison view
- Historical trend beyond May–July 2026 dataset
- Mobile / tablet layout
- User authentication / RBAC (handled by backend)
- Raw SQL query viewer
