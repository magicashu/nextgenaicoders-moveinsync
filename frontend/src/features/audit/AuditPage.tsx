import { identityFor, useAppStore } from '../../core/store'
import { useState, useEffect } from 'react'
import { httpApi } from '../../core/api'
import type { AuditResponse } from '../../core/contracts'

const EVENT_ICONS: Record<string, string> = {
  RUN_STARTED: '▶', ANOMALY_DETECTED: '⚠', INVESTIGATION_COMPLETE: '◉',
  CRITIQUE_PASSED: '✓', BRIEF_COMPOSED: '◈', AWAITING_APPROVAL: '⏸',
}

export function AuditPage() {
  const { tenant, run } = useAppStore()
  const [filter, setFilter] = useState('')
  const [audit, setAudit] = useState<AuditResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    setAudit(null); setError(null)
    if (run) httpApi.audit(identityFor(tenant), run.runId)
      .then(value => { if (active) setAudit(value) })
      .catch(e => { if (active) setError(e.message) })
    return () => { active = false }
  }, [tenant, run])
  const events = audit?.events.filter(e => (e.eventType + JSON.stringify(e.payload)).toLowerCase().includes(filter.toLowerCase())) ?? []

  return (
    <div>
      <h1 className="page-title">Audit Trail</h1>
      <p className="page-subtitle">Tenant-scoped, read-only event log for {tenant}</p>

      <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
        <input
          style={{ flex: 1, background: 'var(--bg3)', border: '1px solid var(--border)', color: 'var(--text)', padding: '8px 14px', borderRadius: 10, fontSize: '0.85rem', outline: 'none' }}
          placeholder="Filter by event or agent…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          onFocus={(e) => { e.target.style.borderColor = 'var(--accent)' }}
          onBlur={(e) => { e.target.style.borderColor = 'var(--border)' }}
        />
      </div>

      {error && <div className="caveat-ribbon" role="alert">{error}</div>}
      {run && <p className="page-subtitle">Run {run.runId} · {audit ? audit.count + ' recorded events' : 'Loading audit…'}</p>}
      <div className="card">
        {audit && <div className="card-body" style={{ overflowX: 'auto' }}><table className="data-table">
          <thead><tr><th>Time</th><th>Event</th><th>Details</th><th>Trace</th></tr></thead>
          <tbody>{events.map(event => <tr key={event.eventId}>
            <td className="td-mono">{new Date(event.occurredAt).toLocaleString()}</td>
            <td>{EVENT_ICONS[event.eventType] ?? '◉'} {event.eventType}</td>
            <td><details><summary>Event payload</summary><pre>{JSON.stringify(event.payload, null, 2)}</pre></details></td>
            <td className="td-mono">{event.traceId}</td>
          </tr>)}</tbody></table>{events.length === 0 && <p>No matching events.</p>}</div>}
        {!run && <>
        <div className="card-body" style={{ padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text)', marginBottom: 8 }}>Audit events appear after running an investigation</div>
          <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
            Go to Dashboard and click <strong>Analyse</strong> to generate a run. Events will be captured here.
          </div>
        </div>
        </>}
      </div>
    </div>
  )
}
