import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Html, Line } from '@react-three/drei'
import { useRef, useState, useEffect } from 'react'
import * as THREE from 'three'
import { WORKFLOW_NODES, NODE_AGENT, AGENT_COLORS } from '../../core/mockData'
import { useAppStore } from '../../core/store'
import type { WorkflowNode } from '../../core/mockData'

// ── Layout ────────────────────────────────────────────────────────────────────

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

const NODE_DURATIONS: Record<string, number> = {
  INITIALIZE_RUN: 12, AUTHORIZE_SCOPE: 8, PROFILE_DATASET: 34, BUILD_CAPABILITY_MATRIX: 21,
  COMPUTE_METRIC_SNAPSHOT: 45, DETECT_ANOMALIES: 28, PRIORITIZE_ISSUE: 15,
  SUPERVISOR_PLAN: 62, VALIDATE_PLAN: 18, RUN_INVESTIGATIONS: 88, MERGE_EVIDENCE: 31,
  EVIDENCE_CRITIC: 55, VERIFY_EVIDENCE: 22, COMPOSE_DECISION_BRIEF: 71,
  ACTION_POLICY_GATE: 9, APPROVAL_INTERRUPT: 5,
  REVALIDATE_AND_EXECUTE: 18, APPEND_AUDIT_EVENT: 6,
}

const NODE_DESCRIPTIONS: Record<string, string> = {
  INITIALIZE_RUN:           'Boot run context and tenant scope',
  AUTHORIZE_SCOPE:          'Verify actor permissions for this tenant',
  PROFILE_DATASET:          'Scan CSV data and build trip-log profile',
  BUILD_CAPABILITY_MATRIX:  'Map available metrics to investigation queries',
  COMPUTE_METRIC_SNAPSHOT:  'Calculate delayed trip rate for the window',
  DETECT_ANOMALIES:         'Compare current rate vs rolling baseline',
  PRIORITIZE_ISSUE:         'Score anomaly severity, decide to escalate',
  SUPERVISOR_PLAN:          'Supervisor agent scopes the investigation plan',
  VALIDATE_PLAN:            'Validate plan feasibility against available evidence',
  RUN_INVESTIGATIONS:       'Investigator queries by vendor, site, and shift',
  MERGE_EVIDENCE:           'Consolidate all evidence items into one record',
  EVIDENCE_CRITIC:          'Critic cross-validates claims against evidence',
  VERIFY_EVIDENCE:          'Assign VERIFIED / QUALIFIED / REJECTED status',
  COMPOSE_DECISION_BRIEF:   'Briefing agent writes summary and findings',
  ACTION_POLICY_GATE:       'Evaluate proposed actions against policy rules',
  APPROVAL_INTERRUPT:       'Pause pipeline — await human approval',
  REVALIDATE_AND_EXECUTE:   'Re-check approved action then execute it',
  APPEND_AUDIT_EVENT:       'Write immutable audit record for the run',
}

// ── 3D scene ──────────────────────────────────────────────────────────────────

function NodeSphere({ node, index, completedUpTo, totalCount, selected, onSelect }: {
  node: WorkflowNode; index: number; completedUpTo: number; totalCount: number
  selected: boolean; onSelect: () => void
}) {
  const meshRef = useRef<THREE.Mesh>(null)
  const agent = NODE_AGENT[node]
  const color = AGENT_COLORS[agent]
  const pipelineComplete = completedUpTo >= totalCount - 1
  const isActive = index === completedUpTo && !pipelineComplete
  const isDone = index < completedUpTo || (pipelineComplete && index === completedUpTo)

  useFrame((state) => {
    if (!meshRef.current) return
    const t = state.clock.getElapsedTime()
    meshRef.current.scale.setScalar(
      isActive ? 1 + Math.sin(t * 3) * 0.1 : selected ? 1.3 : 1
    )
  })

  // Done nodes use their agent color (tinted), active = bright agent color, pending = light grey
  const nodeColor = isDone ? color : isActive ? color : '#CBD5E1'
  const emissive  = isDone ? color  : isActive ? color : '#000000'
  const emissiveInt = isActive ? 0.8 : isDone ? 0.15 : 0

  return (
    <group position={NODE_POSITIONS[node]}>
      <mesh ref={meshRef} onClick={onSelect}>
        <sphereGeometry args={[0.32, 32, 32]} />
        <meshStandardMaterial
          color={nodeColor}
          emissive={emissive}
          emissiveIntensity={emissiveInt}
          roughness={0.3} metalness={0.2}
        />
      </mesh>
      {/* glow ring for active */}
      {isActive && (
        <mesh>
          <sphereGeometry args={[0.52, 32, 32]} />
          <meshStandardMaterial color={color} transparent opacity={0.14} side={THREE.BackSide} />
        </mesh>
      )}
      {/* selection ring */}
      {selected && (
        <mesh>
          <sphereGeometry args={[0.46, 32, 32]} />
          <meshStandardMaterial color={color} transparent opacity={0.2} side={THREE.BackSide} />
        </mesh>
      )}
      {/* label — only for active or selected, to keep graph clean */}
      {(isActive || selected) && (
        <Html center distanceFactor={12} style={{ pointerEvents: 'none' }}>
          <div style={{
            background: '#fff',
            border: `1.5px solid ${color}`,
            borderRadius: 5, padding: '3px 9px',
            fontSize: 9, fontWeight: 700,
            color: color,
            whiteSpace: 'nowrap',
            transform: 'translateY(-36px)',
            boxShadow: `0 2px 10px ${color}33`,
          }}>
            {node.replace(/_/g, ' ')}
          </div>
        </Html>
      )}
    </group>
  )
}

function ParticleEdge({ from, to, active }: { from: WorkflowNode; to: WorkflowNode; active: boolean }) {
  const start = new THREE.Vector3(...NODE_POSITIONS[from])
  const end   = new THREE.Vector3(...NODE_POSITIONS[to])
  const edgeColor = AGENT_COLORS[NODE_AGENT[from]]
  const particleRef = useRef<THREE.Mesh>(null)
  const tRef = useRef(0)

  useFrame((_, delta) => {
    if (!particleRef.current || !active) return
    tRef.current = (tRef.current + delta * 0.9) % 1
    particleRef.current.position.lerpVectors(start, end, tRef.current)
  })

  return (
    <>
      <Line
        points={[start, end]}
        color={active ? edgeColor : '#D1D9E0'}
        lineWidth={active ? 2.5 : 1.0}
        transparent opacity={active ? 0.85 : 0.4}
      />
      {active && (
        <mesh ref={particleRef} position={start.clone()}>
          <sphereGeometry args={[0.09, 8, 8]} />
          <meshStandardMaterial color={edgeColor} emissive={edgeColor} emissiveIntensity={4} />
        </mesh>
      )}
    </>
  )
}

// Stage band labels (HTML overlaid at fixed scene positions)
const STAGE_LABELS = [
  { label: 'SYSTEM',       pos: [ 0.0,  7.8, 0] as [number,number,number], color: AGENT_COLORS.system },
  { label: 'SUPERVISOR',   pos: [ 0.0,  4.2, 0] as [number,number,number], color: AGENT_COLORS.supervisor },
  { label: 'INVESTIGATOR', pos: [-6.0,  0.7, 0] as [number,number,number], color: AGENT_COLORS.investigator },
  { label: 'CRITIC',       pos: [ 5.5,  0.7, 0] as [number,number,number], color: AGENT_COLORS.critic },
  { label: 'BRIEFING',     pos: [ 0.0, -5.2, 0] as [number,number,number], color: AGENT_COLORS.briefing },
]

function Scene({ completedUpTo, totalCount, selectedNode, onSelect }: {
  completedUpTo: number; totalCount: number; selectedNode: string | null; onSelect: (n: string) => void
}) {
  return (
    <>
      {/* Bright, light-friendly lighting */}
      <ambientLight intensity={1.8} color="#ffffff" />
      <directionalLight position={[5, 8, 5]} intensity={1.2} color="#ffffff" />
      <pointLight position={[-6, 4, 4]} intensity={0.6} color="#3C68D0" />
      <pointLight position={[6, -4, 4]} intensity={0.5} color="#3FA535" />

      {/* Stage labels */}
      {STAGE_LABELS.map(({ label, pos, color }) => (
        <Html key={label} position={pos} center distanceFactor={14} style={{ pointerEvents: 'none' }}>
          <div style={{
            fontSize: 14, fontWeight: 900, color,
            textTransform: 'uppercase', letterSpacing: '0.15em',
            opacity: 0.9,
            textShadow: `0 1px 4px rgba(255,255,255,0.8)`,
            whiteSpace: 'nowrap',
          }}>
            {label}
          </div>
        </Html>
      ))}

      {EDGES.map(([from, to], i) => {
        const fromIdx = WORKFLOW_NODES.indexOf(from)
        const toIdx   = WORKFLOW_NODES.indexOf(to)
        const active  = fromIdx < completedUpTo && toIdx <= completedUpTo
        return <ParticleEdge key={i} from={from} to={to} active={active} />
      })}

      {WORKFLOW_NODES.map((node, i) => (
        <NodeSphere key={node} node={node} index={i}
          completedUpTo={completedUpTo}
          totalCount={totalCount}
          selected={selectedNode === node}
          onSelect={() => onSelect(node)} />
      ))}

      <OrbitControls enablePan={false} minDistance={12} maxDistance={36} target={[0, 1.2, 0]} />
    </>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

const PIPELINE_STAGES = [
  { id: 'system',       label: 'System Init',   color: AGENT_COLORS.system,       nodeCount: 7 },
  { id: 'supervisor',   label: 'Supervisor',    color: AGENT_COLORS.supervisor,   nodeCount: 2 },
  { id: 'investigator', label: 'Investigator',  color: AGENT_COLORS.investigator, nodeCount: 2 },
  { id: 'critic',       label: 'Critic',        color: AGENT_COLORS.critic,       nodeCount: 2 },
  { id: 'briefing',     label: 'Briefing',      color: AGENT_COLORS.briefing,     nodeCount: 5 },
]

export function WorkflowGraph3D() {
  const { setActiveNode, timelineStep, setTimelineStep, isLive, setLive } = useAppStore()
  const [selectedNode, setSelectedNode] = useState<string | null>(null)

  // Auto-start replay when page first mounts
  useEffect(() => {
    setTimelineStep(0)
    setLive(true)
  }, [])

  useEffect(() => {
    if (!isLive) return
    if (timelineStep >= WORKFLOW_NODES.length - 1) { setLive(false); return }
    const t = setTimeout(() => setTimelineStep(timelineStep + 1), 900)
    return () => clearTimeout(t)
  }, [isLive, timelineStep])

  const handleNodeSelect = (n: string) => {
    setSelectedNode(prev => prev === n ? null : n)
    setActiveNode(n)
  }

  const selNode = selectedNode as WorkflowNode | null
  const activeNodeName = WORKFLOW_NODES[timelineStep]
  const doneCount = timelineStep
  const totalNodes = WORKFLOW_NODES.length
  const totalMs = Object.entries(NODE_DURATIONS).slice(0, timelineStep).reduce((s, [, v]) => s + v, 0)
  const isComplete = timelineStep >= totalNodes - 1

  // Cumulative start index per stage
  let cumulative = 0
  const stageStarts = PIPELINE_STAGES.map(s => { const start = cumulative; cumulative += s.nodeCount; return start })

  return (
    <div>
      {/* ── Page header ── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
        <h1 className="page-title">Agent Pipeline</h1>
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '4px 12px', borderRadius: 999, fontSize: '0.72rem', fontWeight: 800,
          background: isComplete ? 'rgba(46,125,62,0.1)' : isLive ? 'rgba(63,165,53,0.1)' : 'rgba(60,104,208,0.08)',
          color: isComplete ? '#2E7D3E' : isLive ? '#3FA535' : '#3C68D0',
          border: '1px solid',
          borderColor: isComplete ? 'rgba(46,125,62,0.3)' : isLive ? 'rgba(63,165,53,0.3)' : 'rgba(60,104,208,0.2)',
        }}>
          {isLive && <span className="live-dot" />}
          {isComplete ? `Complete · ${totalMs}ms` : isLive ? `Running · node ${timelineStep + 1} of ${totalNodes}` : `Node ${timelineStep + 1} / ${totalNodes}`}
        </span>
      </div>
      <p className="page-subtitle">Supervisor → Investigator → Critic → Briefing · {totalNodes} nodes · click any node to inspect</p>

      {/* ── Main layout: graph LEFT, detail RIGHT ── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 260px', gap: 16, alignItems: 'start' }}>

        {/* LEFT: graph + controls in one card */}
        <div className="card" style={{ overflow: 'hidden' }}>
          {/* Stage progress header inside card */}
          <div style={{ padding: '14px 18px 12px', borderBottom: '1px solid #E0E0E0' }}>
            <div style={{ display: 'flex', gap: 6, marginBottom: 10, height: 6, borderRadius: 4, overflow: 'hidden', background: '#F0F3FA' }}>
              {PIPELINE_STAGES.map((stage, si) => {
                const start = stageStarts[si]
                const stageDone = Math.max(0, Math.min(stage.nodeCount, timelineStep - start))
                const pct = (stageDone / stage.nodeCount) * 100
                return (
                  <div key={stage.id} style={{ flex: stage.nodeCount, position: 'relative', background: '#E8ECF0', borderRight: si < PIPELINE_STAGES.length - 1 ? '2px solid #F5F5F5' : 'none' }}>
                    <div style={{ position: 'absolute', inset: 0, width: `${pct}%`, background: stage.color, transition: 'width 0.4s ease', borderRadius: si === 0 && stageDone === 0 ? 4 : 0 }} />
                  </div>
                )
              })}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              {PIPELINE_STAGES.map((stage, si) => {
                const start = stageStarts[si]
                const done   = timelineStep >= start + stage.nodeCount
                const active = timelineStep > start && timelineStep < start + stage.nodeCount
                return (
                  <div key={stage.id} style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                    <div style={{
                      width: 7, height: 7, borderRadius: '50%',
                      background: done || active ? stage.color : '#C8CDD3',
                      boxShadow: active ? `0 0 0 3px ${stage.color}25` : 'none',
                      transition: 'all 0.3s',
                    }} />
                    <span style={{ fontSize: '0.68rem', fontWeight: done || active ? 700 : 500, color: done || active ? stage.color : '#A8B2AB' }}>
                      {stage.label}
                    </span>
                    {done && <span style={{ fontSize: '0.62rem', color: stage.color }}>✓</span>}
                  </div>
                )
              })}
            </div>
          </div>

          {/* 3D Canvas — light background */}
          <div style={{ position: 'relative', background: '#F8FAFC' }}>
            <Canvas
              camera={{ position: [0, 1.2, 18], fov: 78 }}
              style={{ background: '#F8FAFC', width: '100%', height: '640px', display: 'block' }}
            >
              <Scene completedUpTo={timelineStep} totalCount={totalNodes} selectedNode={selectedNode} onSelect={handleNodeSelect} />
            </Canvas>

            {/* Active node overlay — bottom left */}
            <div style={{
              position: 'absolute', bottom: 12, left: 12,
              padding: '6px 12px', borderRadius: 8,
              background: 'rgba(255,255,255,0.95)',
              border: `1.5px solid ${AGENT_COLORS[NODE_AGENT[activeNodeName]] ?? '#E0E0E0'}`,
              boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
              pointerEvents: 'none',
            }}>
              <div style={{ fontSize: '0.6rem', color: '#A8B2AB', textTransform: 'uppercase', letterSpacing: '0.12em', marginBottom: 1 }}>Active</div>
              <div style={{ fontSize: '0.78rem', fontWeight: 700, color: '#16211B' }}>
                {activeNodeName?.replace(/_/g, ' ')}
              </div>
            </div>

            {/* Hint */}
            <div style={{ position: 'absolute', bottom: 12, right: 12, fontSize: '0.62rem', color: '#A8B2AB', pointerEvents: 'none' }}>
              Drag · Scroll to zoom · Click node
            </div>
          </div>

          {/* Controls row below canvas */}
          <div style={{ padding: '12px 18px', borderTop: '1px solid #E0E0E0', display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              onClick={() => { setTimelineStep(0); setLive(true) }}
              style={{ padding: '6px 16px', fontSize: '0.78rem', fontWeight: 600, background: '#3FA535', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontFamily: 'var(--font-ui)', display: 'flex', alignItems: 'center', gap: 6 }}
            >
              ▶ Replay
            </button>
            <button
              onClick={() => { setTimelineStep(17); setLive(false) }}
              style={{ padding: '6px 14px', fontSize: '0.78rem', fontWeight: 600, background: '#F0F3FA', color: '#16211B', border: '1px solid #E0E0E0', borderRadius: 6, cursor: 'pointer', fontFamily: 'var(--font-ui)' }}
            >
              ⏭ Final
            </button>
            <span style={{ fontSize: '0.72rem', color: '#A8B2AB' }}>Step</span>
            <input
              type="range" min={0} max={15} value={timelineStep}
              onChange={e => { setLive(false); setTimelineStep(Number(e.target.value)) }}
              style={{ flex: 1, accentColor: '#3FA535' }}
            />
            <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#3FA535', fontFamily: 'IBM Plex Mono, monospace', whiteSpace: 'nowrap' }}>
              {timelineStep + 1} / {totalNodes}
            </span>
          </div>
        </div>

        {/* RIGHT: detail panel */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

          {/* Node detail */}
          <div className="card">
            <div className="card-header">
              <div className="card-title">Node Detail</div>
              {selNode && (
                <span style={{
                  fontSize: '0.68rem', fontWeight: 700, padding: '2px 8px', borderRadius: 999,
                  background: WORKFLOW_NODES.indexOf(selNode) < timelineStep
                    ? 'rgba(46,125,62,0.1)' : WORKFLOW_NODES.indexOf(selNode) === timelineStep
                    ? 'rgba(63,165,53,0.1)' : '#F0F3FA',
                  color: WORKFLOW_NODES.indexOf(selNode) < timelineStep
                    ? '#2E7D3E' : WORKFLOW_NODES.indexOf(selNode) === timelineStep
                    ? '#3FA535' : '#A8B2AB',
                }}>
                  {WORKFLOW_NODES.indexOf(selNode) < timelineStep ? 'DONE' : WORKFLOW_NODES.indexOf(selNode) === timelineStep ? 'ACTIVE' : 'PENDING'}
                </span>
              )}
            </div>
            <div className="card-body">
              {selNode ? (
                <>
                  <div style={{ fontSize: '0.88rem', fontWeight: 700, color: '#16211B', marginBottom: 6, lineHeight: 1.3 }}>
                    {selNode.replace(/_/g, ' ')}
                  </div>
                  <div style={{ fontSize: '0.77rem', color: '#6B7A70', lineHeight: 1.6, marginBottom: 14, paddingBottom: 14, borderBottom: '1px solid #E0E0E0' }}>
                    {NODE_DESCRIPTIONS[selNode]}
                  </div>
                  {[
                    { label: 'Agent',    value: NODE_AGENT[selNode],                              color: AGENT_COLORS[NODE_AGENT[selNode]] },
                    { label: 'Duration', value: `${NODE_DURATIONS[selNode]}ms`,                   color: '#3C68D0' },
                    { label: 'Step',     value: `${WORKFLOW_NODES.indexOf(selNode) + 1} / ${totalNodes}`, color: '#6B7A70' },
                  ].map(row => (
                    <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '7px 0', borderBottom: '1px solid #F0F3FA' }}>
                      <span style={{ fontSize: '0.72rem', color: '#A8B2AB' }}>{row.label}</span>
                      <span style={{ fontSize: '0.75rem', fontWeight: 700, color: row.color, fontFamily: 'IBM Plex Mono, monospace' }}>{row.value}</span>
                    </div>
                  ))}
                </>
              ) : (
                <div style={{ textAlign: 'center', padding: '28px 0' }}>
                  <div style={{ fontSize: '2rem', marginBottom: 10, opacity: 0.15 }}>◎</div>
                  <div style={{ fontSize: '0.75rem', color: '#A8B2AB', lineHeight: 1.6 }}>
                    Click any sphere in the graph to inspect the node
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Agent legend */}
          <div className="card">
            <div className="card-header"><div className="card-title">Agents</div></div>
            <div className="card-body" style={{ padding: '10px 16px' }}>
              {Object.entries(AGENT_COLORS).map(([agent, color]) => (
                <div key={agent} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 0', borderBottom: '1px solid #F5F5F5' }}>
                  <div style={{ width: 10, height: 10, borderRadius: '50%', background: color, flexShrink: 0 }} />
                  <span style={{ fontSize: '0.78rem', fontWeight: 600, color: '#16211B', textTransform: 'capitalize', flex: 1 }}>{agent}</span>
                  <span style={{ fontSize: '0.68rem', color: '#A8B2AB', fontFamily: 'IBM Plex Mono, monospace' }}>
                    {WORKFLOW_NODES.filter(n => NODE_AGENT[n] === agent).length} nodes
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* Run stats */}
          <div className="card">
            <div className="card-header"><div className="card-title">Run Stats</div></div>
            <div className="card-body" style={{ padding: '10px 16px' }}>
              {[
                { label: 'Completed',  value: `${Math.min(timelineStep + 1, totalNodes)} / ${totalNodes}`,   color: '#3FA535' },
                { label: 'Time',       value: `${totalMs}ms`,                   color: '#3C68D0' },
                { label: 'Status',     value: isComplete ? 'COMPLETE' : isLive ? 'RUNNING' : 'PAUSED', color: isComplete ? '#2E7D3E' : isLive ? '#3FA535' : '#6B7A70' },
              ].map(r => (
                <div key={r.label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid #F5F5F5' }}>
                  <span style={{ fontSize: '0.72rem', color: '#A8B2AB' }}>{r.label}</span>
                  <span style={{ fontSize: '0.75rem', fontWeight: 700, color: r.color, fontFamily: 'IBM Plex Mono, monospace' }}>{r.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* ── Pipeline step list ── */}
      <div className="card" style={{ marginTop: 14 }}>
        <div className="card-header">
          <div className="card-title">All Steps</div>
          <div style={{ fontSize: '0.72rem', color: '#A8B2AB' }}>{isComplete ? totalNodes : doneCount} of {totalNodes} complete · click to jump</div>
        </div>
        <div className="card-body" style={{ padding: '14px 18px' }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {WORKFLOW_NODES.map((n, i) => {
              const isDone   = i < timelineStep
              const isActive = i === timelineStep
              const color    = AGENT_COLORS[NODE_AGENT[n]]
              return (
                <button key={n}
                  onClick={() => { setLive(false); setTimelineStep(i); handleNodeSelect(n) }}
                  title={NODE_DESCRIPTIONS[n]}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 5,
                    padding: '5px 10px', borderRadius: 6,
                    fontSize: '0.7rem', fontWeight: isActive ? 700 : 500,
                    cursor: 'pointer',
                    border: `1px solid ${isDone ? color + '55' : isActive ? color : '#E0E0E0'}`,
                    background: isDone ? color + '0e' : isActive ? color + '18' : '#FAFAFA',
                    color: isDone ? color : isActive ? color : '#6B7A70',
                    fontFamily: 'IBM Plex Mono, monospace',
                    transition: 'all 0.15s',
                    boxShadow: isActive ? `0 0 0 2px ${color}33` : 'none',
                  }}
                >
                  <div style={{ width: 5, height: 5, borderRadius: '50%', background: isDone ? color : isActive ? color : '#C8CDD3', flexShrink: 0 }} />
                  {n.split('_').slice(0, 2).join(' ')}
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
