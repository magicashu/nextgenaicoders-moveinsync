# UI Work Assignment — Person 1: Frontend Core & Dashboard
**Project:** MoveInSync Mobility Decision Copilot
**Branch:** `ui/person1-dashboard`
**Last updated:** 2026-09-05

---

## Brand & Theme

We are building for **MoveInSync Fleet** (`fleet.moveinsync.com`). Use their exact brand palette — do not use generic blues or purples.

### Official MoveInSync color tokens

| Token | Hex | Use |
|---|---|---|
| `--mis-auth-accent` | `#3FA535` | Primary CTA, active nav, highlights |
| `--mis-auth-success` | `#2E7D3E` | Success states, PASS gates |
| `--sec-bright-green` | `#27D22E` | Live indicator, glow effects |
| `--pmry-900` | `#010F55` | Dark primary (headers) |
| `--pmry-800` | `#021567` | Deep navy |
| `--pmry-700` | `#041F80` | Navy accent |
| `--pmry-600` | `#062A99` | Medium blue |
| `--pmry-400` | `#3C68D0` | Interactive blue |
| `--pmry-300` | `#638FE7` | Light blue |
| Left panel bg | `#0F1A14` | Deep forest dark (main bg) |
| Sign-in button | `#3FA535` | CTA green |
| `--mis-auth-text` | `#16211B` | Body text (dark mode: `#E8F5E2`) |
| `--mis-auth-border` | `#DDE3DE` | Border (dark mode: `rgba(63,165,53,0.2)`) |
| `--mis-auth-muted` | `#6B7A70` | Muted text |
| `--sem-error` | `#B00020` | Error / danger |
| `--sem-warning` | `#FF9D00` | Warning / anomaly |
| `--sem-info` | `#0091FF` | Info |

### Dark theme mapping (what to use in `styles.css`)

```css
:root {
  --bg:           #0A1210;   /* deep forest black */
  --bg2:          #0F1A14;   /* panel / nav background */
  --bg3:          #152018;   /* inner card */
  --border:       rgba(63, 165, 53, 0.18);
  --border-bright:rgba(63, 165, 53, 0.4);
  --accent:       #3FA535;   /* MoveInSync green */
  --accent-glow:  #27D22E;   /* bright green for glow */
  --accent2:      #3C68D0;   /* MoveInSync primary blue */
  --green:        #2E7D3E;   /* success */
  --yellow:       #FF9D00;   /* warning */
  --red:          #B00020;   /* error */
  --text:         #E8F5E2;   /* light green-white */
  --text-dim:     #6B7A70;
  --text-muted:   #3D4A43;
}
```

---

## Your Files

```
frontend/src/
├── App.tsx                              ← page router (yours)
├── styles.css                           ← all CSS — update to MoveInSync theme
├── core/
│   ├── mockData.ts                      ← shared data (read-only)
│   └── store.ts                         ← Zustand store (yours to extend)
├── shared/
│   └── Nav.tsx                          ← TopNav + SideNav
└── features/
    ├── dashboard/   DashboardPage.tsx   ← hero, metrics, charts
    ├── brief/       DecisionBriefPage.tsx
    ├── audit/       AuditPage.tsx
    └── scorecard/   ScorecardPage.tsx
```

**Do not touch:** `features/workflow/WorkflowGraph3D.tsx` — Person 2 owns that.

---

## Setup

```bash
# Clone or pull main
git clone https://github.com/magicashu/nextgenaicoders-moveinsync.git
cd nextgenaicoders-moveinsync/frontend

npm install          # installs all packages

VITE_USE_MOCKS=true npm run dev
# → http://localhost:5173
```

### All packages (already in package.json — no extra installs)

| Package | Version | Purpose |
|---|---|---|
| `react` + `react-dom` | 19.x | UI framework |
| `typescript` | 7.x | Type safety |
| `vite` | 8.x | Dev server & build |
| `@vitejs/plugin-react` | 6.x | Vite + React |
| `recharts` | latest | All 2D charts (line, bar, pie, donut) |
| `zustand` | latest | Global state store |
| `three` | latest | 3D engine (Person 2 uses this) |
| `@react-three/fiber` | latest | React wrapper for Three.js |
| `@react-three/drei` | latest | drei helpers (OrbitControls, Html, Line) |
| `@react-three/postprocessing` | latest | Bloom glow effects |
| `@types/three` | latest | TypeScript types for Three.js |

---

## Pages You Own

### 1. Dashboard (`/`)
The main operations view — loads on startup.

| Section | Component / element | Status |
|---|---|---|
| Hero banner | Anomaly badge, headline, 94% confidence | Built |
| Metric chips | Current 21.9%, Baseline 12.3%, Delta +9.6pp | Built |
| Trend line chart | 7-week delayed-trip rate vs baseline | Built |
| Vendor bar chart | Top 6 vendors current vs baseline | Built |
| Site donut chart | 5 sites by delay share % | Built |
| Vendor delta bars | 10 vendors, animated gradient bars | Built |
| Findings card | Numbered finding items | Built |
| Run metadata card | Run ID, tool calls, status | Built |
| Caveat ribbon | Yellow warning for data issues | Built |

**What to improve next:**
- Update all accent colors to MoveInSync green (`#3FA535`)
- Add a tenant-aware title (changes when you switch tenant in dropdown)

### 2. Decision Brief
- Status badge: AWAITING APPROVAL / APPROVED / REJECTED
- Approve and Reject buttons with confirmation modal
- Findings list, action proposal, trust record table

### 3. Audit Trail
- Tenant-scoped event log (only shows events for selected tenant)
- Live text filter by event or agent name

### 4. Scorecard
- Acceptance gate pills: G1 / G2 / G3 / SEC / AUDIT (all green PASS)
- Six zero-tolerance counters (all must show 0)
- Latency and call-count bar charts
- DS-01 to DS-20 scenario table (19/20 passing)

---

## Recharts — Quick Reference

Always wrap in `<ResponsiveContainer width="100%" height={N}>`.

### Line chart (trend)
```tsx
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
         ReferenceLine, ResponsiveContainer } from 'recharts'

<ResponsiveContainer width="100%" height={180}>
  <LineChart data={trendData}>
    <CartesianGrid stroke="rgba(63,165,53,0.08)" />
    <XAxis dataKey="week" tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
    <YAxis tickFormatter={(v: number) => `${v}%`} tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
    <Tooltip contentStyle={{ background: '#0F1A14', border: '1px solid rgba(63,165,53,0.3)', borderRadius: 8 }} />
    <ReferenceLine y={12.28} stroke="#FF9D00" strokeDasharray="4 4" />
    <Line type="monotone" dataKey="delayed" stroke="#B00020" strokeWidth={2.5} />
    <Line type="monotone" dataKey="baseline" stroke="#3FA535" strokeWidth={1.5} strokeDasharray="5 3" />
  </LineChart>
</ResponsiveContainer>
```

### Bar chart (vendors)
```tsx
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts'

<ResponsiveContainer width="100%" height={200}>
  <BarChart data={vendorData}>
    <CartesianGrid stroke="rgba(63,165,53,0.08)" />
    <XAxis dataKey="name" tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} />
    <YAxis tickFormatter={(v: number) => `${v}%`} tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
    <Tooltip contentStyle={{ background: '#0F1A14', border: '1px solid rgba(63,165,53,0.3)', borderRadius: 8 }} />
    <Bar dataKey="current" fill="#B00020" name="Current" radius={[3, 3, 0, 0]} />
    <Bar dataKey="baseline" fill="#3FA535" name="Baseline" radius={[3, 3, 0, 0]} />
  </BarChart>
</ResponsiveContainer>
```

### Donut chart (sites)
```tsx
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

const COLORS = ['#3FA535','#3C68D0','#FF9D00','#27D22E','#0091FF']

<ResponsiveContainer width="100%" height={200}>
  <PieChart>
    <Pie data={siteData} cx="45%" cy="50%" innerRadius={55} outerRadius={80}
         dataKey="value" nameKey="name" paddingAngle={3}>
      {siteData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
    </Pie>
    <Tooltip contentStyle={{ background: '#0F1A14', border: '1px solid rgba(63,165,53,0.3)', borderRadius: 8 }} />
    <Legend iconSize={10} wrapperStyle={{ fontSize: 11, color: '#6B7A70' }} />
  </PieChart>
</ResponsiveContainer>
```

---

## Zustand Store — Available State

```ts
import { useAppStore } from '../../core/store'

// Selected tenant (changes with the top-right dropdown)
const { tenant, setTenant } = useAppStore()

// Approval flow for Decision Brief
const { approvalState, setApprovalState } = useAppStore()
// approvalState: 'pending' | 'approved' | 'rejected'

// Shared with Person 2's 3D graph
const { timelineStep, setTimelineStep } = useAppStore() // 0–15
const { isLive, setLive } = useAppStore()               // auto-advance animation
const { activeNode, setActiveNode } = useAppStore()     // clicked 3D node name
```

---

## Connecting to the Real Backend

When the Spring Boot backend is running at `localhost:8080`, replace mock data calls:

```ts
// Before (mock)
import { g1RunArtifact } from '../../core/mockData'
const run = g1RunArtifact

// After (real API)
const [run, setRun] = useState(null)
useEffect(() => {
  fetch('/api/v1/demo/brief?asOf=2026-06-08', {
    headers: { 'X-Business-Unit': tenant }
  })
    .then(r => r.json())
    .then(setRun)
}, [tenant])
```

Remove `VITE_USE_MOCKS=true` from the dev command once backend is up.

---

## Coordination with Person 2

| Rule | Detail |
|---|---|
| Don't edit | `features/workflow/WorkflowGraph3D.tsx` |
| Can read | `core/mockData.ts`, `core/store.ts` |
| Add CSS | Put new classes in `styles.css` with a short comment |
| Extend store | Coordinate before adding new fields to `store.ts` |
| Package installs | Tell Person 2 before adding new npm packages |

---

## Definition of Done

- [ ] All CSS variables updated to MoveInSync green theme (`#3FA535` accent)
- [ ] Dashboard: hero banner, 3 metric chips, trend chart, vendor bar, site donut, delta bars all render
- [ ] Trend chart uses MoveInSync green for baseline line, red for current rate
- [ ] Decision Brief: Approve/Reject modal works, shows correct status badge
- [ ] Audit Trail: filters correctly by event and agent for selected tenant
- [ ] Scorecard: all 5 gate pills show PASS in green, all 6 zero counters show 0
- [ ] Tenant dropdown switches the tenant label on all pages
- [ ] `npm run build` — zero TypeScript errors
- [ ] `VITE_USE_MOCKS=true npm run dev` — all 5 pages load without console errors
