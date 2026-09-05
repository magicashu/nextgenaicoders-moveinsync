import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { OrbitControls, Html, Sphere, Line } from '@react-three/drei'
import { useRef, useState, useEffect, useMemo } from 'react'
import * as THREE from 'three'
import { WORKFLOW_NODES, NODE_AGENT, AGENT_COLORS } from '../../core/mockData'
import { useAppStore } from '../../core/store'
import type { WorkflowNode } from '../../core/mockData'

const NODE_POSITIONS: Record<WorkflowNode, [number, number, number]> = {
  INITIALIZE_RUN:           [-5.5,  3.5,  0],
  AUTHORIZE_SCOPE:          [-3.5,  3.5,  0],
  PROFILE_DATASET:          [-1.5,  3.5,  0],
  BUILD_CAPABILITY_MATRIX:  [ 0.5,  3.5,  0],
  COMPUTE_METRIC_SNAPSHOT:  [ 2.5,  3.5,  0],
  DETECT_ANOMALIES:         [ 4.5,  3.5,  0],
  PRIORITIZE_ISSUE:         [ 6.5,  3.5,  0],
  SUPERVISOR_PLAN:          [-3.0,  1.0,  0],
  VALIDATE_PLAN:            [-1.0,  1.0,  0],
  RUN_INVESTIGATIONS:       [-4.5, -1.5,  0],
  MERGE_EVIDENCE:           [-2.5, -1.5,  0],
  EVIDENCE_CRITIC:          [ 1.5, -1.5,  0],
  VERIFY_EVIDENCE:          [ 3.5, -1.5,  0],
  COMPOSE_DECISION_BRIEF:   [-1.5, -4.0,  0],
  ACTION_POLICY_GATE:       [ 0.5, -4.0,  0],
  APPROVAL_INTERRUPT:       [ 2.5, -4.0,  0],
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
]

function NodeSphere({ node, index, completedUpTo, selected, onSelect }: {
  node: WorkflowNode; index: number; completedUpTo: number
  selected: boolean; onSelect: () => void
}) {
  const meshRef = useRef<THREE.Mesh>(null)
  const agent = NODE_AGENT[node]
  const color = AGENT_COLORS[agent]
  const isActive = index === completedUpTo
  const isDone = index < completedUpTo
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

function Scene({ completedUpTo, selectedNode, onSelect }: {
  completedUpTo: number; selectedNode: string | null; onSelect: (n: string) => void
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
        const fromIdx = WORKFLOW_NODES.indexOf(from)
        const toIdx = WORKFLOW_NODES.indexOf(to)
        const active = fromIdx < completedUpTo && toIdx <= completedUpTo
        return <ParticleEdge key={i} from={from} to={to} active={active} />
      })}

      {WORKFLOW_NODES.map((node, i) => (
        <NodeSphere
          key={node}
          node={node}
          index={i}
          completedUpTo={completedUpTo}
          selected={selectedNode === node}
          onSelect={() => onSelect(node)}
        />
      ))}

      <OrbitControls enablePan={false} minDistance={5} maxDistance={18} />
    </>
  )
}

export function WorkflowGraph3D() {
  const { activeNode, setActiveNode, timelineStep, setTimelineStep, isLive, setLive } = useAppStore()
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const nodeDurations: Record<string, number> = {
    INITIALIZE_RUN: 12, AUTHORIZE_SCOPE: 8, PROFILE_DATASET: 34, BUILD_CAPABILITY_MATRIX: 21,
    COMPUTE_METRIC_SNAPSHOT: 45, DETECT_ANOMALIES: 28, PRIORITIZE_ISSUE: 15,
    SUPERVISOR_PLAN: 62, VALIDATE_PLAN: 18, RUN_INVESTIGATIONS: 88, MERGE_EVIDENCE: 31,
    EVIDENCE_CRITIC: 55, VERIFY_EVIDENCE: 22, COMPOSE_DECISION_BRIEF: 71,
    ACTION_POLICY_GATE: 9, APPROVAL_INTERRUPT: 5,
  }

  useEffect(() => {
    if (!isLive) return
    if (timelineStep >= WORKFLOW_NODES.length - 1) { setLive(false); return }
    const t = setTimeout(() => setTimelineStep(timelineStep + 1), 1200)
    return () => clearTimeout(t)
  }, [isLive, timelineStep])

  const handleNodeSelect = (n: string) => {
    setSelectedNode(prev => prev === n ? null : n)
    setActiveNode(n)
  }

  const selNode = selectedNode as WorkflowNode | null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <h2 className="page-title">3D Workflow Graph</h2>
        <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: '0.78rem' }}
          onClick={() => { setTimelineStep(0); setLive(true) }}>
          ▶ Replay
        </button>
        <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: '0.78rem' }}
          onClick={() => { setTimelineStep(15); setLive(false) }}>
          ⏭ Final state
        </button>
        {isLive && <span style={{ fontSize: '0.78rem', color: 'var(--accent)' }}><span className="live-dot" /> Animating…</span>}
      </div>

      <div className="timeline-scrubber">
        <div className="scrubber-track">
          <span className="scrubber-label">0</span>
          <input
            type="range" className="scrubber-input" min={0} max={15} value={timelineStep}
            onChange={(e) => { setLive(false); setTimelineStep(Number(e.target.value)) }}
          />
          <span className="scrubber-label">15</span>
        </div>
        <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textAlign: 'center' }}>
          Step {timelineStep}/15 · Active: <span style={{ color: 'var(--accent)', fontWeight: 700 }}>{WORKFLOW_NODES[timelineStep]?.replace(/_/g,' ')}</span>
        </div>
      </div>

      <div style={{ position: 'relative', flex: 1, borderRadius: 'var(--radius)', overflow: 'hidden', border: '1px solid var(--border)', minHeight: 420 }}>
        <Canvas camera={{ position: [0, 0, 14], fov: 55 }} style={{ background: '#060b18' }}>
          <Scene completedUpTo={timelineStep} selectedNode={selectedNode} onSelect={handleNodeSelect} />
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
                {WORKFLOW_NODES.indexOf(selNode) < timelineStep ? 'Completed' :
                  WORKFLOW_NODES.indexOf(selNode) === timelineStep ? 'Active' : 'Pending'}
              </span>
            </div>
            <div className="node-info-row">
              <span>Duration</span>
              <span>{nodeDurations[selNode]} ms</span>
            </div>
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
          <div key={n} className="node-chip" style={{
            background: i < timelineStep ? 'rgba(16,185,129,0.12)' : i === timelineStep ? 'rgba(6,182,212,0.15)' : 'var(--bg3)',
            borderColor: i < timelineStep ? 'rgba(16,185,129,0.4)' : i === timelineStep ? 'rgba(6,182,212,0.4)' : 'var(--border)',
            color: i < timelineStep ? 'var(--green)' : i === timelineStep ? 'var(--accent)' : 'var(--text-muted)',
            cursor: 'pointer',
          }} onClick={() => { setLive(false); setTimelineStep(i); handleNodeSelect(n) }}>
            {n.split('_').slice(0, 2).join(' ')}
          </div>
        ))}
      </div>
    </div>
  )
}
