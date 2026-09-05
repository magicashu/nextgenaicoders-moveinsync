import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Html, Line } from '@react-three/drei'
import { Component, useRef, useState, useEffect, type ReactNode } from 'react'
import * as THREE from 'three'
import { WORKFLOW_NODES, NODE_AGENT, AGENT_COLORS } from '../../core/workflowDesign'
import { useAppStore } from '../../core/store'
import type { WorkflowNode } from '../../core/workflowDesign'

const NODE_POSITIONS: Record<WorkflowNode, [number, number, number]> = {
  // System row — full width, high up
  INITIALIZE_RUN:           [ -9.0,  6.5,  0.8],
  AUTHORIZE_SCOPE:          [ -6.0,  6.5,  0.0],
  PROFILE_DATASET:          [ -3.0,  6.5,  1.0],
  BUILD_CAPABILITY_MATRIX:  [  0.0,  6.5, -0.4],
  COMPUTE_METRIC_SNAPSHOT:  [  3.0,  6.5,  0.9],
  DETECT_ANOMALIES:         [  6.0,  6.5,  0.2],
  PRIORITIZE_ISSUE:         [  9.0,  6.5,  0.7],
  // Supervisor
  SUPERVISOR_PLAN:          [ -2.5,  3.0, -1.0],
  VALIDATE_PLAN:            [  2.5,  3.0, -0.3],
  // Investigator + Critic
  RUN_INVESTIGATIONS:       [ -7.5, -0.5,  0.9],
  MERGE_EVIDENCE:           [ -2.5, -0.5,  0.0],
  EVIDENCE_CRITIC:          [  2.5, -0.5, -0.7],
  VERIFY_EVIDENCE:          [  7.5, -0.5,  0.5],
  // Briefing + post-approval (2 extra nodes from main)
  COMPOSE_DECISION_BRIEF:   [ -6.0, -4.0,  0.5],
  ACTION_POLICY_GATE:       [ -2.0, -4.0, -0.5],
  APPROVAL_INTERRUPT:       [  1.5, -4.0,  0.9],
  REVALIDATE_AND_EXECUTE:   [  5.0, -4.0,  0.0],
  APPEND_AUDIT_EVENT:       [  8.5, -4.0,  0.6],
}

const EDGES: [WorkflowNode, WorkflowNode][] = [
  ['INITIALIZE_RUN','AUTHORIZE_SCOPE'], ['AUTHORIZE_SCOPE','PROFILE_DATASET'],
  ['PROFILE_DATASET','BUILD_CAPABILITY_MATRIX'], ['BUILD_CAPABILITY_MATRIX','COMPUTE_METRIC_SNAPSHOT'],
  ['COMPUTE_METRIC_SNAPSHOT','DETECT_ANOMALIES'], ['DETECT_ANOMALIES','PRIORITIZE_ISSUE'],
  ['PRIORITIZE_ISSUE','SUPERVISOR_PLAN'], ['SUPERVISOR_PLAN','VALIDATE_PLAN'],
  ['VALIDATE_PLAN','RUN_INVESTIGATIONS'], ['RUN_INVESTIGATIONS','MERGE_EVIDENCE'],
  ['MERGE_EVIDENCE','EVIDENCE_CRITIC'], ['EVIDENCE_CRITIC','VERIFY_EVIDENCE'],
  ['VERIFY_EVIDENCE','COMPOSE_DECISION_BRIEF'], ['COMPOSE_DECISION_BRIEF','ACTION_POLICY_GATE'],
  ['ACTION_POLICY_GATE','APPROVAL_INTERRUPT'],
  ['APPROVAL_INTERRUPT','REVALIDATE_AND_EXECUTE'], ['REVALIDATE_AND_EXECUTE','APPEND_AUDIT_EVENT'],
]

function NodeSphere({ node, recorded, outcome, selected, onSelect }: {
  node: WorkflowNode; recorded: boolean; outcome?: string
  selected: boolean; onSelect: () => void
}) {
  const meshRef = useRef<THREE.Mesh>(null)
  const agent = NODE_AGENT[node]
  const color = AGENT_COLORS[agent]
  const isActive = selected && recorded
  const isDone = recorded
  const pos = NODE_POSITIONS[node]

  useFrame((state) => {
    if (!meshRef.current) return
    const t = state.clock.getElapsedTime()
    if (isActive) {
      meshRef.current.scale.setScalar(1 + Math.sin(t * 3) * 0.08)
    } else {
      meshRef.current.scale.setScalar(selected ? 1.25 : 1)
    }
  })

  const failed = /fail|error|denied|blocked/i.test(outcome ?? '')
  const paused = /paused|approval required/i.test(outcome ?? '')
  const nodeColor = isDone ? failed ? '#ef4444' : paused ? '#f59e0b' : '#10b981' : '#cbd5e1'
  const emissive = isActive ? color : isDone ? nodeColor : '#000'

  return (
    <group position={pos}>
      <mesh ref={meshRef} onClick={onSelect}>
        <sphereGeometry args={[0.32, 32, 32]} />
        <meshStandardMaterial
          color={nodeColor}
          emissive={emissive}
          emissiveIntensity={isActive ? 0.25 : 0.08}
          roughness={0.55}
          metalness={0.1}
        />
      </mesh>
      {selected && (
        <mesh>
          <sphereGeometry args={[0.42, 32, 32]} />
          <meshStandardMaterial color={color} transparent opacity={0.15} side={THREE.BackSide} />
        </mesh>
      )}
      {isActive && (
        <mesh>
          <sphereGeometry args={[0.50, 32, 32]} />
          <meshStandardMaterial color={color} transparent opacity={0.08} side={THREE.BackSide} />
        </mesh>
      )}
      <Html center distanceFactor={12} style={{ pointerEvents: 'none' }}>
        <div style={{
          background: 'rgba(255,255,255,0.96)', border: `1px solid ${isActive || selected ? color : '#1e3a5f'}`,
          borderRadius: 6, padding: '2px 6px', fontSize: 11, fontWeight: 700,
          color: '#1e293b',
          whiteSpace: 'nowrap', maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis',
          transform: 'translateY(-28px)', pointerEvents: 'none',
        }}>
          {node.replace(/_/g, ' ')}
        </div>
      </Html>
    </group>
  )
}

function ParticleEdge({ from, to, active }: { from: WorkflowNode; to: WorkflowNode; active: boolean }) {
  const start = new THREE.Vector3(...NODE_POSITIONS[from])
  const end = new THREE.Vector3(...NODE_POSITIONS[to])
  const particleRef = useRef<THREE.Mesh>(null)
  const tRef = useRef(0)

  useFrame((_, delta) => {
    if (!particleRef.current || !active) return
    tRef.current = (tRef.current + delta * 0.8) % 1
    particleRef.current.position.lerpVectors(start, end, tRef.current)
  })

  return (
    <>
      <Line points={[start, end]} color={active ? '#06b6d4' : '#1e3a5f'} lineWidth={active ? 1.5 : 0.8} transparent opacity={active ? 0.85 : 0.65} />
      {active && (
        <mesh ref={particleRef} position={start.clone()}>
          <sphereGeometry args={[0.08, 8, 8]} />
          <meshStandardMaterial color="#2563eb" emissive="#2563eb" emissiveIntensity={0.4} />
        </mesh>
      )}
    </>
  )
}

function AgentClusterLabel({ label, pos, color }: { label: string; pos: [number, number, number]; color: string }) {
  return (
    <Html position={pos} center distanceFactor={14} style={{ pointerEvents: 'none' }}>
      <div style={{ fontSize: 10, fontWeight: 900, color, textTransform: 'uppercase', letterSpacing: '0.12em', opacity: 0.7 }}>
        {label}
      </div>
    </Html>
  )
}

function Scene({ recorded, outcomes, traversed, selectedNode, onSelect }: {
  recorded: Set<string>; outcomes: Map<string, string>; traversed: [WorkflowNode, WorkflowNode][]; selectedNode: string | null; onSelect: (n: string) => void
}) {
  return (
    <>
      <color attach="background" args={['#ffffff']} />
      <ambientLight intensity={1.6} />
      <directionalLight position={[0, 8, 8]} intensity={2} color="#ffffff" />
      <pointLight position={[-6, 0, 3]} intensity={0.5} color="#ffffff" />
      <pointLight position={[6, -4, 3]} intensity={0.5} color="#ffffff" />

      <AgentClusterLabel label="System" pos={[0, 7.4, 0]} color="#334155" />
      <AgentClusterLabel label="Supervisor" pos={[0, 3.9, 0]} color="#166534" />
      <AgentClusterLabel label="Investigator" pos={[-5, 0.4, 0]} color="#166534" />
      <AgentClusterLabel label="Critic" pos={[5, 0.4, 0]} color="#92400e" />
      <AgentClusterLabel label="Briefing" pos={[-2, -5.0, 0]} color="#9d174d" />

      {[...EDGES, ...traversed.filter(([from, to]) => !EDGES.some(([a, b]) => a === from && b === to))].map(([from, to]) =>
        <ParticleEdge key={from + ':' + to} from={from} to={to} active={traversed.some(([a, b]) => a === from && b === to)} />
      )}

      {WORKFLOW_NODES.map((node, i) => (
        <NodeSphere
          key={node}
          node={node}
          recorded={recorded.has(node)}
          outcome={outcomes.get(node)}
          selected={selectedNode === node}
          onSelect={() => onSelect(node)}
        />
      ))}

      <OrbitControls enablePan={false} target={[0, 1, 0]} minDistance={5} maxDistance={24} />
    </>
  )
}

class WorkflowCanvasBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    return this.state.failed
      ? <div className="card card-body" role="status">3D rendering is unavailable in this browser. Use the node buttons and recorded execution details below.</div>
      : this.props.children
  }
}

export function WorkflowGraph3D() {
  const { run, setActiveNode, timelineStep, setTimelineStep, isLive, setLive } = useAppStore()
  const [viewVersion, setViewVersion] = useState(0)
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const transitions = run?.trust.transitions.filter(t => !t.subNode) ?? []
  const finalStep = Math.max(0, transitions.length - 1)
  const visibleTransitions = transitions.slice(0, Math.min(timelineStep, finalStep) + 1)
  const recorded = new Set(visibleTransitions.map(t => t.node.toUpperCase()))
  const outcomes = new Map(visibleTransitions.map(t => [t.node.toUpperCase(), t.outcome]))
  const traversed: [WorkflowNode, WorkflowNode][] = []
  visibleTransitions.forEach((transition, index) => {
    if (index === 0) return
    const from = visibleTransitions[index - 1].node.toUpperCase() as WorkflowNode
    const to = transition.node.toUpperCase() as WorkflowNode
    if (from !== to && NODE_POSITIONS[from] && NODE_POSITIONS[to] && !traversed.some(([a, b]) => a === from && b === to)) traversed.push([from, to])
  })

  useEffect(() => { setTimelineStep(finalStep); setLive(false); setSelectedNode(null) }, [run, finalStep])
  useEffect(() => {
    if (!isLive) return
    if (timelineStep >= finalStep) { setLive(false); return }
    const timer = setTimeout(() => setTimelineStep(timelineStep + 1), 900)
    return () => clearTimeout(timer)
  }, [isLive, timelineStep, finalStep])
  const handleNodeSelect = (n: string) => {
    setSelectedNode(prev => prev === n ? null : n)
    setActiveNode(n)
  }

  const selNode = selectedNode as WorkflowNode | null
  const selectedRecords = visibleTransitions.filter(t => t.node.toUpperCase() === selectedNode)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <h2 className="page-title">3D Workflow Graph</h2>
        <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: '0.78rem' }}
          disabled={!transitions.length} onClick={() => { setTimelineStep(0); setLive(true) }}>
          ▶ Replay recorded run
        </button>
        <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: '0.78rem' }}
          disabled={!transitions.length} onClick={() => { setTimelineStep(finalStep); setLive(false) }}>
          ⏭ Final state
        </button>
        <button className="btn btn-secondary" onClick={() => setViewVersion(value => value + 1)}>Reset view</button>
        {isLive && <span style={{ fontSize: '0.78rem', color: 'var(--accent)' }}><span className="live-dot" /> Animating…</span>}
      </div>

      <p className="page-subtitle">LangGraph4j · 18 nodes · {run ? `Run ${run.runId} · ${run.trust.finalStep}` : 'Open Dashboard to load a report and its execution records.'} · Animation replays recorded results.</p>
      <div className="timeline-scrubber">
        <div className="scrubber-track">
          <span className="scrubber-label">0</span>
          <input
            type="range" aria-label="Recorded workflow step" disabled={!transitions.length} className="scrubber-input" min={0} max={finalStep} value={Math.min(timelineStep, finalStep)}
            onChange={(e) => { setLive(false); setTimelineStep(Number(e.target.value)) }}
          />
          <span className="scrubber-label">{finalStep}</span>
        </div>
        <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textAlign: 'center' }}>
          Record {transitions.length ? timelineStep + 1 : 0}/{transitions.length} · Node: <span style={{ color: 'var(--accent)', fontWeight: 700 }}>{transitions[timelineStep]?.node.replace(/_/g,' ') ?? 'No run'}</span>
        </div>
      </div>

      <div style={{ position: 'relative', height: 480, flexShrink: 0, borderRadius: 'var(--radius)', overflow: 'hidden', border: '1px solid var(--border)', minHeight: 420 }}>
        <WorkflowCanvasBoundary key={viewVersion}><Canvas camera={{ position: [0, 1, 16], fov: 55 }} style={{ background: '#ffffff' }}>
          <Scene recorded={recorded} outcomes={outcomes} traversed={traversed} selectedNode={selectedNode} onSelect={handleNodeSelect} />
        </Canvas></WorkflowCanvasBoundary>

        {selNode && (
          <div className="node-info-panel">
            <h4>{selNode.replace(/_/g, ' ')}</h4>
            <div className="node-info-row">
              <span>Agent</span>
              <span style={{ color: AGENT_COLORS[NODE_AGENT[selNode]] }}>{NODE_AGENT[selNode]}</span>
            </div>
            <div className="node-info-row">
              <span>Status</span>
              <span>
                {selectedRecords.at(-1)?.outcome ?? 'Not executed'}
              </span>
            </div>
            <div className="node-info-row">
              <span>Duration</span>
              <span>{selectedRecords.length ? selectedRecords.reduce((n, t) => n + t.durationMs, 0) + ' ms' : '—'}</span>
            </div>
            {selectedRecords.map((record, index) => <details key={index}><summary>{record.outcome} · {record.durationMs} ms</summary><pre>{JSON.stringify(record.attributes, null, 2)}</pre></details>)}
            <div className="node-info-row">
              <span>Step</span>
              <span>{WORKFLOW_NODES.indexOf(selNode) + 1} / {WORKFLOW_NODES.length}</span>
            </div>
          </div>
        )}

        <div style={{ position: 'absolute', top: 12, right: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
          {Object.entries(AGENT_COLORS).map(([a, c]) => (
            <div key={a} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--text-dim)' }}>
              <div style={{ width: 10, height: 10, borderRadius: '50%', background: c }} />
              {a}
            </div>
          ))}
        </div>
      </div>

      <p className="page-subtitle">Green: recorded · Amber: awaiting approval · Red: failed or blocked · Grey: not reached at this replay step. Select any node for its recorded decisions.</p>
      <div className="node-transitions">
        {WORKFLOW_NODES.map((n, i) => (
          <button type="button" key={n} aria-pressed={n === selectedNode} className="node-chip" style={{
            background: recorded.has(n) ? 'rgba(16,185,129,0.12)' : n === selectedNode ? 'rgba(6,182,212,0.15)' : 'var(--bg3)',
            borderColor: recorded.has(n) ? 'rgba(16,185,129,0.4)' : n === selectedNode ? 'rgba(6,182,212,0.4)' : 'var(--border)',
            color: recorded.has(n) ? 'var(--green)' : n === selectedNode ? 'var(--accent)' : 'var(--text-muted)',
            cursor: 'pointer',
          }} onClick={() => { setLive(false); handleNodeSelect(n) }}>
            {n.split('_').slice(0, 2).join(' ')}
          </button>
        ))}
      </div>
      <details className="card card-body workflow-records"><summary>Recorded execution details · {run?.trust.transitions.length ?? 0} records</summary>
        {run?.trust.transitions.map((record, index) => <details key={index} style={{ marginTop: 12 }}>
          <summary>{record.node.replaceAll('_', ' ')}{record.subNode ? ' / ' + record.subNode : ''} · {record.outcome} · {record.durationMs} ms</summary>
          <p>{new Date(record.startedAt).toLocaleString()}</p>
          <pre>{JSON.stringify(record.attributes ?? {}, null, 2)}</pre>
        </details>)}
        {!run && <p>No execution records are available until a report is loaded.</p>}
      </details>
    </div>
  )
}
