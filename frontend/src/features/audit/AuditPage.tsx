import { useState, useEffect } from 'react'
import { AGENT_COLORS, auditEvents, WORKFLOW_NODES } from '../../core/mockData'
import { useAppStore } from '../../core/store'

type AuditEvent = typeof auditEvents[number]

const EVENT_META: Record<string, { icon: string; color: string; label: string }> = {
  RUN_STARTED:            { icon: '▶', color: '#3C68D0', label: 'Run Started' },
  ANOMALY_DETECTED:       { icon: '⚡', color: '#f59e0b', label: 'Anomaly Detected' },
  INVESTIGATION_COMPLETE: { icon: '◉', color: '#06b6d4', label: 'Investigation Complete' },
  CRITIQUE_PASSED:        { icon: '✓', color: '#10b981', label: 'Critique Passed' },
  BRIEF_COMPOSED:         { icon: '◈', color: '#ec4899', label: 'Brief Composed' },
  AWAITING_APPROVAL:      { icon: '⏸', color: '#f59e0b', label: 'Awaiting Approval' },
}

const PIPELINE_STAGES = [
  { id: 'system',      label: 'System Init',    color: '#3C68D0', steps: ['INITIALIZE_RUN','AUTHORIZE_SCOPE','PROFILE_DATASET','BUILD_CAPABILITY_MATRIX','COMPUTE_METRIC_SNAPSHOT','DETECT_ANOMALIES','PRIORITIZE_ISSUE'] },
  { id: 'supervisor',  label: 'Supervisor',     color: '#3FA535', steps: ['SUPERVISOR_PLAN','VALIDATE_PLAN'] },
  { id: 'investigator',label: 'Investigator',   color: '#27D22E', steps: ['RUN_INVESTIGATIONS','MERGE_EVIDENCE'] },
  { id: 'critic',      label: 'Critic',         color: '#FF9D00', steps: ['EVIDENCE_CRITIC','VERIFY_EVIDENCE'] },
  { id: 'briefing',    label: 'Briefing',       color: '#C13D6D', steps: ['COMPOSE_DECISION_BRIEF','ACTION_POLICY_GATE','APPROVAL_INTERRUPT'] },
]

function PipelineTimeline({ timelineStep }: { timelineStep: number }) {
  const completedNodes = WORKFLOW_NODES.slice(0, timelineStep + 1)

  return (
    <div style={{ display: 'flex', gap: 0, marginBottom: 20, borderRadius: 12, overflow: 'hidden', border: '1px solid var(--border)' }}>
      {PIPELINE_STAGES.map((stage, si) => {
        const doneCount = stage.steps.filter(s => completedNodes.includes(s as typeof WORKFLOW_NODES[number])).length
        const allDone = doneCount === stage.steps.length
        const partial = doneCount > 0 && !allDone
        return (
          <div key={stage.id} style={{
            flex: stage.steps.length,
            padding: '12px 10px',
            background: allDone ? `${stage.color}14` : partial ? `${stage.color}08` : 'var(--bg2)',
            borderRight: si < PIPELINE_STAGES.length - 1 ? '1px solid var(--border)' : 'none',
            transition: 'background 0.3s',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: allDone ? stage.color : partial ? stage.color : 'var(--border)', opacity: allDone ? 1 : partial ? 0.6 : 0.3 }} />
              <span style={{ fontSize: '0.72rem', fontWeight: 800, color: allDone ? stage.color : partial ? stage.color : 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.08em', opacity: allDone ? 1 : partial ? 0.8 : 0.5 }}>
                {stage.label}
              </span>
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', fontWeight: 600 }}>
              {doneCount}/{stage.steps.length} steps
            </div>
            {allDone && (
              <div style={{ fontSize: '0.68rem', color: stage.color, fontWeight: 700, marginTop: 2 }}>COMPLETE ✓</div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function EventRow({ ev, isNew }: { ev: AuditEvent; isNew: boolean }) {
  const meta = EVENT_META[ev.event] ?? { icon: '·', color: 'var(--text-dim)', label: ev.event }
  const agentColor = AGENT_COLORS[ev.agent] ?? 'var(--text-dim)'
  const ts = new Date(ev.ts)
  const timeStr = ts.toLocaleTimeString('en', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '28px 160px 1fr 100px 110px',
      alignItems: 'center', gap: 12, padding: '10px 16px',
      borderBottom: '1px solid var(--border)',
      background: isNew ? 'rgba(6,182,212,0.05)' : 'transparent',
      animation: isNew ? 'fadeIn 0.4s ease' : 'none',
      transition: 'background 0.5s',
    }}>
      <div style={{ fontSize: 14, color: meta.color, textAlign: 'center' }}>{meta.icon}</div>
      <div>
        <div style={{ fontSize: '0.78rem', fontWeight: 800, color: meta.color }}>{meta.label}</div>
        <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', marginTop: 1 }}>{ev.event}</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', fontFamily: 'monospace', background: 'var(--bg3)', padding: '2px 6px', borderRadius: 4 }}>
          {ev.runId}
        </span>
        <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>·</span>
        <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>{ev.tenant}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
        <div style={{ width: 6, height: 6, borderRadius: '50%', background: agentColor, flexShrink: 0 }} />
        <span style={{ fontSize: '0.75rem', color: agentColor, fontWeight: 700 }}>{ev.agent}</span>
      </div>
      <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', fontFamily: 'monospace', textAlign: 'right' }}>{timeStr}</div>
    </div>
  )
}

export function AuditPage() {
  const { tenant, timelineStep } = useAppStore()
  const [filter, setFilter] = useState('')
  const [visibleCount, setVisibleCount] = useState(3)

  // Simulate events appearing as the pipeline progresses
  useEffect(() => {
    if (timelineStep >= 6)  setVisibleCount(6)
    else if (timelineStep >= 4) setVisibleCount(5)
    else if (timelineStep >= 3) setVisibleCount(4)
    else if (timelineStep >= 2) setVisibleCount(3)
    else if (timelineStep >= 1) setVisibleCount(2)
    else setVisibleCount(1)
  }, [timelineStep])

  const tenantEvents = auditEvents.filter(e => e.tenant === tenant || tenant !== 'pinnacle-Slc')
  const filtered = tenantEvents.filter(e =>
    !filter || e.event.toLowerCase().includes(filter.toLowerCase()) || e.agent.toLowerCase().includes(filter.toLowerCase())
  )
  const visible = filtered.slice(0, visibleCount)

  const passedAll = timelineStep >= WORKFLOW_NODES.length - 1
  const runDuration = (() => {
    if (!passedAll) return null
    const t0 = new Date(auditEvents[0].ts).getTime()
    const t1 = new Date(auditEvents[auditEvents.length - 1].ts).getTime()
    return Math.round((t1 - t0) / 1000)
  })()

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
        <h1 className="page-title">Audit Trail</h1>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '4px 12px', borderRadius: 999, fontSize: '0.72rem', fontWeight: 800,
          background: passedAll ? 'rgba(16,185,129,0.12)' : 'rgba(6,182,212,0.12)',
          color: passedAll ? 'var(--green)' : 'var(--accent)',
          border: '1px solid',
          borderColor: passedAll ? 'rgba(16,185,129,0.3)' : 'rgba(6,182,212,0.3)',
        }}>
          <div style={{ width: 6, height: 6, borderRadius: '50%', background: 'currentColor', animation: passedAll ? 'none' : 'pulse 1.5s ease-in-out infinite' }} />
          {passedAll ? `RUN COMPLETE · ${runDuration}s` : `PIPELINE ACTIVE · Step ${timelineStep}/15`}
        </div>
      </div>
      <p className="page-subtitle">Tenant-scoped, read-only event log · {tenant}</p>

      <PipelineTimeline timelineStep={timelineStep} />

      {/* Stats row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
        {[
          { label: 'Events Captured', value: visible.length, color: 'var(--accent)' },
          { label: 'Run ID', value: auditEvents[0].runId.slice(0, 8) + '…', color: 'var(--text-dim)' },
          { label: 'Tenant', value: tenant, color: 'var(--text)' },
          { label: 'Security', value: 'TENANT-SCOPED', color: 'var(--green)' },
        ].map(s => (
          <div key={s.label} className="card" style={{ padding: '12px 16px' }}>
            <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 4 }}>{s.label}</div>
            <div style={{ fontSize: '0.9rem', fontWeight: 800, color: s.color, fontFamily: 'monospace' }}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* Filter + log */}
      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', borderBottom: '1px solid var(--border)' }}>
          <div className="card-title" style={{ margin: 0, flex: 1 }}>Event Log</div>
          <input
            style={{ width: 240, background: 'var(--bg3)', border: '1px solid var(--border)', color: 'var(--text)', padding: '6px 12px', borderRadius: 8, fontSize: '0.8rem', outline: 'none' }}
            placeholder="Filter by event or agent…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            onFocus={(e) => { e.target.style.borderColor = 'var(--accent)' }}
            onBlur={(e) => { e.target.style.borderColor = 'var(--border)' }}
          />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '28px 160px 1fr 100px 110px', gap: 12, padding: '8px 16px', borderBottom: '1px solid var(--border)', background: 'var(--bg2)' }}>
          {['', 'Event', 'Run · Tenant', 'Agent', 'Timestamp'].map((h, i) => (
            <div key={i} style={{ fontSize: '0.68rem', fontWeight: 800, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.08em', textAlign: i === 4 ? 'right' : 'left' }}>{h}</div>
          ))}
        </div>
        {visible.length === 0 ? (
          <div style={{ padding: '32px', textAlign: 'center', color: 'var(--text-dim)', fontSize: '0.85rem' }}>
            No events match your filter
          </div>
        ) : (
          visible.map((ev, i) => (
            <EventRow key={ev.id} ev={ev} isNew={i === visible.length - 1 && visibleCount <= tenantEvents.length} />
          ))
        )}

        {visible.length < filtered.length && (
          <div style={{ padding: '12px 16px', textAlign: 'center', fontSize: '0.78rem', color: 'var(--text-dim)' }}>
            {filtered.length - visible.length} more events · advance the workflow timeline to reveal
          </div>
        )}
      </div>

      {/* Security note */}
      <div style={{ marginTop: 12, padding: '10px 16px', borderRadius: 10, background: 'rgba(16,185,129,0.07)', border: '1px solid rgba(16,185,129,0.2)', display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{ fontSize: 16 }}>🔒</span>
        <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
          All events are tenant-scoped to <strong style={{ color: 'var(--green)' }}>{tenant}</strong>. Cross-tenant requests are refused at the actor-context layer — the server identity is hardcoded and never sourced from HTTP headers.
        </span>
      </div>
    </div>
  )
}
