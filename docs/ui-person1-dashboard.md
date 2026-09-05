# UI Work Assignment — Person 1: Frontend Core & Dashboard
**Project:** MoveInSync Mobility Decision Copilot — React UI
**Your branch:** `ui/person1-dashboard`

---

## Your Responsibility Areas

You own: **App shell, global styles, Dashboard page, Metric components, and all charts.**

---

## What to install first

```bash
cd frontend
npm install
```

All packages are already in `package.json` after the initial setup. No extra installs needed.

### Full package list (for reference)

| Package | Version | Why |
|---|---|---|
| `react` | 19.x | Core framework |
| `react-dom` | 19.x | DOM rendering |
| `typescript` | 7.x | Type safety |
| `vite` | 8.x | Dev server & bundler |
| `@vitejs/plugin-react` | 6.x | Vite React plugin |
| `recharts` | latest | Bar, line, pie, donut charts |
| `zustand` | latest | Global state (tenant, node selection) |
| `three` | latest | 3D engine (used by Person 2) |
| `@react-three/fiber` | latest | React renderer for Three.js |
| `@react-three/drei` | latest | Three.js helpers (OrbitControls, Html, Line) |
| `@react-three/postprocessing` | latest | Bloom/glow effects |
| `@types/three` | latest | TypeScript types for Three.js |

---

## Files you own

```
frontend/src/
├── App.tsx                          ← top-level router (yours)
├── styles.css                       ← all CSS variables + shared classes (yours)
├── core/
│   ├── mockData.ts                  ← shared mock data (read-only for you)
│   └── store.ts                     ← Zustand store (yours to extend)
├── shared/
│   ├── Nav.tsx                      ← TopNav + SideNav (yours)
│   └── MetricCard.tsx               ← legacy card (can refactor or keep)
└── features/
    ├── dashboard/
    │   └── DashboardPage.tsx        ← YOURS — hero banner, metric chips, all charts
    ├── brief/
    │   └── DecisionBriefPage.tsx    ← YOURS — brief, approve/reject modal
    ├── audit/
    │   └── AuditPage.tsx            ← YOURS — audit trail table with filter
    └── scorecard/
        └── ScorecardPage.tsx        ← YOURS — gate pills, zero-tolerance, DS table
```

---

## How to run

```bash
cd frontend
VITE_USE_MOCKS=true npm run dev
# Opens at http://localhost:5173
```

---

## Dashboard page — what's built

### Hero banner
- Alert badge showing anomaly type (G1 / G2 / G3)
- Headline from run artifact
- Evidence confidence % in top-right

### Metric chips (3-column grid)
- Current Rate (21.88%) — red
- 4-Week Baseline (12.28%) — cyan
- Delta (+9.6 pp) — red

### Trend line chart
- 7-week delayed trip rate (red line)
- Baseline reference line (dashed yellow)
- Uses `recharts` LineChart with CartesianGrid

### Vendor bar chart
- Top 6 vendors — current vs baseline side by side
- Uses `recharts` BarChart

### Site donut chart
- 5 sites with delay share %
- Uses `recharts` PieChart with innerRadius (donut style)

### Vendor delta bars
- 10 vendors with animated gradient bars
- Colour-coded: red > 10pp, yellow > 7pp, cyan otherwise

### Investigation findings & Run metadata cards

---

## Charts reference (Recharts)

```tsx
import { BarChart, Bar, LineChart, Line, PieChart, Pie, Cell, 
         XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts'

// Always wrap in ResponsiveContainer with width="100%"
<ResponsiveContainer width="100%" height={200}>
  <BarChart data={myData}>
    <XAxis dataKey="name" tick={{ fill: '#7a97c0', fontSize: 11 }} />
    <Bar dataKey="value" fill="#06b6d4" />
  </BarChart>
</ResponsiveContainer>
```

---

## Decision Brief page — what's built

- Status badge: AWAITING APPROVAL / APPROVED / REJECTED
- Caveat ribbon (yellow warning)
- Hero banner with G1 badge
- Key findings list with numbered badges
- Action card with Approve / Reject buttons
- Confirmation modal before any state change
- Workflow steps completed chips
- Trust record table

---

## Global CSS design tokens

```css
--bg: #060b18          /* page background */
--bg2: #0d1526         /* nav / card background */
--bg3: #111d35         /* inner card background */
--accent: #06b6d4      /* cyan — primary highlight */
--accent2: #6366f1     /* indigo */
--green: #10b981       /* success */
--yellow: #f59e0b      /* warning */
--red: #ef4444         /* danger */
--pink: #ec4899        /* briefing agent colour */
```

---

## Zustand store — what's available

```ts
import { useAppStore } from '../../core/store'

const { tenant, setTenant } = useAppStore()       // selected tenant
const { approvalState, setApprovalState } = ...    // pending | approved | rejected
const { timelineStep, setTimelineStep } = ...      // 0-15, drives 3D graph
const { isLive, setLive } = ...                    // live animation toggle
const { activeNode, setActiveNode } = ...          // clicked node in 3D graph
```

---

## Known limitations (mock mode)

- All data is from `src/core/mockData.ts` — no real API calls
- Tenant selector changes the label but all data is from `pinnacle-Slc`
- When backend is connected: replace `g1RunArtifact` with `fetchDemoBrief()` call

---

## Coordination with Person 2

- **Do not edit** `features/workflow/WorkflowGraph3D.tsx` — that is Person 2's file
- You can read `core/mockData.ts` but don't add data — Person 2 may need to extend it too
- If you need a new CSS class, add it to `styles.css` but put a comment so Person 2 knows

---

## Definition of done for your tasks

- [ ] Dashboard loads with hero banner, 3 metric chips, trend line chart
- [ ] Vendor bar chart and site donut chart render side by side
- [ ] Vendor delta bars animate on load
- [ ] Decision Brief page shows correct status badge and Approve/Reject buttons
- [ ] Approve modal confirms then shows APPROVED state
- [ ] Audit Trail filters by event/agent text
- [ ] Scorecard shows all 5 gate pills, 6 zero-tolerance counters, 20 DS rows
- [ ] `npm run build` completes with no TypeScript errors
