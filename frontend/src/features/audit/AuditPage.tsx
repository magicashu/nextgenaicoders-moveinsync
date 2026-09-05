import { AGENT_COLORS } from '../../core/mockData'
import { useAppStore } from '../../core/store'
import { useState } from 'react'

const EVENT_ICONS: Record<string, string> = {
  RUN_STARTED: '▶', ANOMALY_DETECTED: '⚠', INVESTIGATION_COMPLETE: '◉',
  CRITIQUE_PASSED: '✓', BRIEF_COMPOSED: '◈', AWAITING_APPROVAL: '⏸',
}

export function AuditPage() {
  const { tenant } = useAppStore()
  const [filter, setFilter] = useState('')

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
        <div className="card-body" style={{ padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text)', marginBottom: 8 }}>Audit events appear after running an investigation</div>
          <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
            Go to Dashboard and click <strong>Apply</strong> to generate a run. Events will be captured here.
          </div>
        </div>
      </div>
    </div>
  )
}
