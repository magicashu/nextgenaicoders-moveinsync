import { auditEvents, AGENT_COLORS } from '../../core/mockData'
import { useAppStore } from '../../core/store'
import { useState } from 'react'

const EVENT_ICONS: Record<string, string> = {
  RUN_STARTED: '▶', ANOMALY_DETECTED: '⚠', INVESTIGATION_COMPLETE: '◉',
  CRITIQUE_PASSED: '✓', BRIEF_COMPOSED: '◈', AWAITING_APPROVAL: '⏸',
}

export function AuditPage() {
  const { tenant } = useAppStore()
  const [filter, setFilter] = useState('')

  const filtered = auditEvents.filter(e =>
    e.tenant === tenant &&
    (filter === '' || e.event.toLowerCase().includes(filter.toLowerCase()) || e.agent.toLowerCase().includes(filter.toLowerCase()))
  )

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

      <div className="card">
        <div className="card-body" style={{ padding: 0 }}>
          {filtered.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              No events match the filter for <strong style={{ color: 'var(--text-dim)' }}>{tenant}</strong>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Event</th><th>Agent</th><th>Run ID</th><th>Timestamp</th><th>Tenant</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((e) => (
                  <tr key={e.id}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: '1rem' }}>{EVENT_ICONS[e.event] ?? '·'}</span>
                        <span style={{ fontWeight: 700 }}>{e.event.replace(/_/g, ' ')}</span>
                      </div>
                    </td>
                    <td>
                      <span className="agent-badge" style={{
                        color: AGENT_COLORS[e.agent] ?? 'var(--text-dim)',
                        borderColor: `${AGENT_COLORS[e.agent] ?? '#1e3a5f'}44`,
                        background: `${AGENT_COLORS[e.agent] ?? '#1e3a5f'}12`,
                      }}>
                        {e.agent}
                      </span>
                    </td>
                    <td className="td-mono">{e.runId}…</td>
                    <td className="td-mono">{e.ts}</td>
                    <td>
                      <span style={{ padding: '3px 8px', borderRadius: 6, background: 'rgba(6,182,212,0.1)', border: '1px solid rgba(6,182,212,0.2)', fontSize: '0.72rem', color: 'var(--accent)', fontWeight: 700 }}>
                        {e.tenant}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: '0.75rem', color: 'var(--text-muted)', textAlign: 'right' }}>
        {filtered.length} event{filtered.length !== 1 ? 's' : ''} · scoped to {tenant}
      </div>
    </div>
  )
}
