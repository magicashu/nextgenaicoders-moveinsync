# UI Work Assignment — Person 2: 3D Workflow Graph
**Project:** MoveInSync Mobility Decision Copilot — React UI
**Your branch:** `ui/person2-workflow3d`

---

## Your Responsibility Area

You own: **The 3D Workflow Graph page** — the centrepiece of the UI.

---

## What to install first

```bash
cd frontend
npm install
```

All packages are already in `package.json`. No extra installs needed.

### Packages you will use most

| Package | Why you need it |
|---|---|
| `@react-three/fiber` | React wrapper for Three.js — use `Canvas`, `useFrame`, `useThree` |
| `@react-three/drei` | Helpers: `OrbitControls`, `Html`, `Line`, `Sphere`, `Text` |
| `@react-three/postprocessing` | Bloom glow effect on active nodes |
| `three` | Raw Three.js: `Vector3`, `Color`, `MeshStandardMaterial`, `InstancedMesh` |
| `@types/three` | TypeScript types |
| `zustand` | Read `timelineStep`, `activeNode`, `isLive` from shared store |

### Quick install reference (if you clone fresh)

```bash
npm install @react-three/fiber @react-three/drei @react-three/postprocessing three
npm install --save-dev @types/three
```

---

## Your file

```
frontend/src/features/workflow/WorkflowGraph3D.tsx   ← EVERYTHING you own
```

You may also read (but not change):
- `src/core/mockData.ts` — node list, agent colours, node positions
- `src/core/store.ts` — shared state (timelineStep, activeNode, isLive)
- `src/styles.css` — for `.node-info-panel`, `.timeline-scrubber`, `.node-transitions`, `.live-dot` classes

---

## How to run

```bash
cd frontend
VITE_USE_MOCKS=true npm run dev
# Opens at http://localhost:5173
# Click "⬡ 3D Workflow" in the sidebar
```

---

## What's already built (your starting point)

### 3D Canvas (React Three Fiber)

```tsx
import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Html, Line } from '@react-three/drei'
```

- `Canvas` with `camera={{ position: [0, 0, 14], fov: 55 }}`
- `OrbitControls` — mouse drag to rotate, scroll to zoom
- Fog for depth atmosphere

### 16 workflow nodes as 3D spheres

Each node is a `<NodeSphere>` component:
- Resting: dark blue `#1e3a5f`
- Completed: green `#10b981` with green emissive glow
- Active (current step): agent colour (cyan/indigo/green/amber/pink) with bloom
- Selected (clicked): scale 1.25 + halo ring
- Label floats above each sphere via `<Html>` from drei

### Node positions (3D layout in `mockData.ts`)

The 16 nodes are arranged in 5 agent cluster rows:
- **System** (row top y=3.5): 7 nodes left to right
- **Supervisor** (y=1.0): 2 nodes
- **Investigator** (y=-1.5, left): 2 nodes
- **Critic** (y=-1.5, right): 2 nodes
- **Briefing** (y=-4.0): 3 nodes

You can adjust `NODE_POSITIONS` in `mockData.ts` if you want a different layout.

### Animated particle edges

`<ParticleEdge>` draws a line between two nodes and animates a glowing cyan dot travelling along it when the edge is active (both nodes completed).

### Agent cluster labels

Floating `<Html>` labels in the 3D scene showing: System, Supervisor, Investigator, Critic, Briefing — colour-coded by agent.

### Timeline scrubber (2D overlay)

- Range slider 0–15 controls which nodes are "completed"
- Replay button resets to step 0 and auto-advances
- Final state button jumps to step 15

### Node info panel (2D overlay)

When a node is clicked:
- Panel appears bottom-left
- Shows: node name, agent role, status, duration (ms), step number

### Node transition chips

Row of clickable chips below the canvas — one per node. Clicking jumps timeline to that step.

---

## Agent colour reference

```ts
const AGENT_COLORS = {
  system:      '#6366f1',  // indigo
  supervisor:  '#06b6d4',  // cyan
  investigator:'#10b981',  // green
  critic:      '#f59e0b',  // amber
  briefing:    '#ec4899',  // pink
}
```

---

## Key Three.js / R3F patterns to know

### useFrame — animation loop

```tsx
import { useFrame } from '@react-three/fiber'
const meshRef = useRef<THREE.Mesh>(null)

useFrame((state) => {
  const t = state.clock.getElapsedTime()
  if (meshRef.current) {
    meshRef.current.scale.setScalar(1 + Math.sin(t * 3) * 0.08) // pulse
  }
})
```

### Html — 2D labels in 3D space

```tsx
import { Html } from '@react-three/drei'

<Html center distanceFactor={12} style={{ pointerEvents: 'none' }}>
  <div style={{ color: 'white', fontSize: 10 }}>Label text</div>
</Html>
```

### Line — edge between two points

```tsx
import { Line } from '@react-three/drei'
import * as THREE from 'three'

const start = new THREE.Vector3(-3, 1, 0)
const end = new THREE.Vector3(0, 1, 0)

<Line points={[start, end]} color="#06b6d4" lineWidth={1.5} />
```

### MeshStandardMaterial — glow effect

```tsx
<meshStandardMaterial
  color="#10b981"
  emissive="#10b981"
  emissiveIntensity={1.2}   // >1 = glowing
  roughness={0.3}
  metalness={0.6}
/>
```

---

## Ideas to improve / extend (optional stretch goals)

1. **Bloom post-processing** — add `@react-three/postprocessing` `<Bloom>` inside `<EffectComposer>`:
   ```tsx
   import { EffectComposer, Bloom } from '@react-three/postprocessing'
   // inside Canvas:
   <EffectComposer>
     <Bloom luminanceThreshold={0.4} intensity={1.2} />
   </EffectComposer>
   ```

2. **Correction cycle loop arrow** — red flashing arc when `correctionCycles > 0`

3. **Camera auto-focus on active node** — smooth `lerpVectors` to centre camera on the currently active node

4. **Node click panel animation** — animate the node-info-panel in with a CSS transition

5. **Particle count per edge** — more particles on edges that took longer (use `nodeDurations`)

6. **3D text instead of Html labels** — `import { Text } from '@react-three/drei'`

---

## Coordinate with Person 1

- Person 1 owns `styles.css` — if you need a new CSS class, ask them or add it with a comment
- **Do not edit** any files in `features/dashboard/`, `features/brief/`, `features/audit/`, `features/scorecard/`
- You can add fields to `mockData.ts` (e.g. more `nodeDurations`) but don't remove or rename existing exports
- The shared Zustand store (`core/store.ts`) is yours to read, but don't change types without coordinating

---

## Definition of done for your tasks

- [ ] 16 nodes render as 3D spheres in the correct agent-cluster layout
- [ ] Nodes colour correctly: blue (pending) → agent colour (active) → green (done)
- [ ] Edges draw between connected nodes with correct active/inactive styling
- [ ] Animated particle travels along active edges
- [ ] OrbitControls work: drag, zoom, reset
- [ ] Timeline scrubber moves the active node forward/backward
- [ ] Replay button auto-advances step by step with 1.2s interval
- [ ] Clicking a node shows the node-info-panel with correct data
- [ ] Agent cluster labels float in scene
- [ ] Node transition chips below canvas are clickable
- [ ] `npm run build` completes with no TypeScript errors

---

## Useful docs

- React Three Fiber docs: https://docs.pmnd.rs/react-three-fiber
- Drei helpers: https://github.com/pmndrs/drei
- Three.js docs: https://threejs.org/docs/
