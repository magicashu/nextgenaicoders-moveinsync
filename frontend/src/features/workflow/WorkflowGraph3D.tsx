import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Html, Line } from '@react-three/drei'
import { useRef, useState, useEffect } from 'react'
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

function NodeSphere({ node, recorded, selected, onSelect }: {
  node: WorkflowNode; recorded: boolean
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

  const nodeColor = isDone ? '#10b981' : isActive ? color : '#1e3a5f'
  const emissive = isDone ? '#052' : isActive ? color : '#000'

  return (
    <group position={pos}>
      <mesh ref={meshRef} onClick={onSelect}>
        <sphereGeometry args={[0.32, 32, 32]} />
        <meshStandardMaterial
          color={nodeColor}
          emissive={emissive}
          emissiveIntensity={isActive ? 1.2 : isDone ? 0.4 : 0.05}
          roughness={0.3}
          metalness={0.6}
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
          background: 'rgba(6,11,24,0.9)', border: `1px solid ${isActive || selected ? color : '#1e3a5f'}`,
          borderRadius: 6, padding: '2px 6px', fontSize: 9, fontWeight: 700,
          color: isActive || selected ? color : '#7a97c0',
          whiteSpace: 'nowrap', maxWidth: 100, overflow: 'hidden', textOverflow: 'ellipsis',
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
      <Line points={[start, end]} color={active ? '#06b6d4' : '#1e3a5f'} lineWidth={active ? 1.5 : 0.8} transparent opacity={active ? 0.9 : 0.3} />
      {active && (
        <mesh ref={particleRef} position={start.clone()}>
          <sphereGeometry args={[0.08, 8, 8]} />
          <meshStandardMaterial color="#06b6d4" emissive="#06b6d4" emissiveIntensity={2} />
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

function Scene({ recorded, selectedNode, onSelect }: {
  recorded: Set<string>; selectedNode: string | null; onSelect: (n: string) => void
}) {
  return (
    <>
      <ambientLight intensity={0.3} />
      <pointLight position={[0, 8, 5]} intensity={1.2} color="#06b6d4" />
      <pointLight position={[-6, 0, 3]} intensity={0.8} color="#6366f1" />
      <pointLight position={[6, -4, 3]} intensity={0.8} color="#10b981" />
      <fog attach="fog" args={['#060b18', 12, 22]} />

      <AgentClusterLabel label="System" pos={[-3, 4.5, 0]} color="#6366f1" />
      <AgentClusterLabel label="Supervisor" pos={[-2, 2.1, 0]} color="#06b6d4" />
      <AgentClusterLabel label="Investigator" pos={[-3.5, -0.4, 0]} color="#10b981" />
      <AgentClusterLabel label="Critic" pos={[2.5, -0.4, 0]} color="#f59e0b" />
      <AgentClusterLabel label="Briefing" pos={[0.5, -5.0, 0]} color="#ec4899" />

      {EDGES.map(([from, to], i) => {
        const active = recorded.has(from) && recorded.has(to)
        return <ParticleEdge key={i} from={from} to={to} active={active} />
      })}

      {WORKFLOW_NODES.map((node, i) => (
        <NodeSphere
          key={node}
          node={node}
          recorded={recorded.has(node)}
          selected={selectedNode === node}
          onSelect={() => onSelect(node)}
        />
      ))}

      <OrbitControls enablePan={false} minDistance={5} maxDistance={18} />
    </>
  )
}

export function WorkflowGraph3D() {
  const { run, setActiveNode, timelineStep, setTimelineStep, isLive, setLive } = useAppStore()
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const transitions = run?.trust.transitions.filter(t => !t.subNode) ?? []
  const finalStep = Math.max(0, transitions.length - 1)
  const recorded = new Set(transitions.slice(0, timelineStep + 1).map(t => t.node.toUpperCase()))

  useEffect(() => { setTimelineStep(finalStep); setLive(false) }, [run, finalStep])
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
  const selectedRecords = transitions.filter(t => t.node.toUpperCase() === selectedNode)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <h2 className="page-title">3D Workflow Graph</h2>
        <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: '0.78rem' }}
          onClick={() => { setTimelineStep(0); setLive(true) }}>
          ▶ Replay recorded run
        </button>
        <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: '0.78rem' }}
          onClick={() => { setTimelineStep(finalStep); setLive(false) }}>
          ⏭ Final state
        </button>
        {isLive && <span style={{ fontSize: '0.78rem', color: 'var(--accent)' }}><span className="live-dot" /> Animating…</span>}
      </div>

      <p className="page-subtitle">LangGraph4j · 18 nodes · {run ? `Run ${run.runId} · ${run.trust.finalStep}` : 'Run an investigation to see execution records.'} · Animation replays recorded results.</p>
      <div className="timeline-scrubber">
        <div className="scrubber-track">
          <span className="scrubber-label">0</span>
          <input
            type="range" className="scrubber-input" min={0} max={finalStep} value={timelineStep}
            onChange={(e) => { setLive(false); setTimelineStep(Number(e.target.value)) }}
          />
          <span className="scrubber-label">{finalStep}</span>
        </div>
        <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textAlign: 'center' }}>
          Record {transitions.length ? timelineStep + 1 : 0}/{transitions.length} · Node: <span style={{ color: 'var(--accent)', fontWeight: 700 }}>{transitions[timelineStep]?.node.replace(/_/g,' ') ?? 'No run'}</span>
        </div>
      </div>

      <div style={{ position: 'relative', flex: 1, borderRadius: 'var(--radius)', overflow: 'hidden', border: '1px solid var(--border)', minHeight: 420 }}>
        <Canvas camera={{ position: [0, 0, 14], fov: 55 }} style={{ background: '#060b18' }}>
          <Scene recorded={recorded} selectedNode={selectedNode} onSelect={handleNodeSelect} />
        </Canvas>

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

      <div className="node-transitions">
        {WORKFLOW_NODES.map((n, i) => (
          <button type="button" key={n} className="node-chip" style={{
            background: recorded.has(n) ? 'rgba(16,185,129,0.12)' : n === selectedNode ? 'rgba(6,182,212,0.15)' : 'var(--bg3)',
            borderColor: recorded.has(n) ? 'rgba(16,185,129,0.4)' : n === selectedNode ? 'rgba(6,182,212,0.4)' : 'var(--border)',
            color: recorded.has(n) ? 'var(--green)' : n === selectedNode ? 'var(--accent)' : 'var(--text-muted)',
            cursor: 'pointer',
          }} onClick={() => { setLive(false); handleNodeSelect(n) }}>
            {n.split('_').slice(0, 2).join(' ')}
          </button>
        ))}
      </div>
    </div>
  )
}
