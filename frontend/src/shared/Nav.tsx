import { TENANTS } from '../core/workflowDesign'
import { useAppStore } from '../core/store'
import { diagnosticsEnabled, MANAGER_ROLES } from '../core/presentation'

const NAV_ITEMS = [
  { id: 'dashboard', icon: '◈', label: 'Dashboard' },
  { id: 'incidents', icon: '⚑', label: 'Incidents' },
  { id: 'trust', icon: '◌', label: 'LLM & Trust' },
  { id: 'workflow', icon: '⬡', label: '3D Workflow' },
  { id: 'brief', icon: '◉', label: 'Reports' },
  { id: 'audit', icon: '◎', label: 'Audit Trail' },
  { id: 'scorecard', icon: '◆', label: 'Scorecard' },
]

interface Props {
  page: string
  setPage: (p: string) => void
}

export function TopNav({ page, setPage }: Props) {
  const { tenant, setTenant, role, changePersona, refresh, lastRefresh, busy: spinning, run } = useAppStore()
  const handleRefresh = () => { void refresh() }
  const lastRefreshStr = lastRefresh ? new Date(lastRefresh).toLocaleTimeString() : '—'

  return (
    <nav className="topnav">
      <div className="topnav-brand">
        <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
          <circle cx="11" cy="11" r="9" stroke="currentColor" strokeWidth="1.5" />
          <path d="M11 4v7l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="11" cy="11" r="2" fill="currentColor" />
        </svg>
        Mobility Decision Copilot
      </div>
      <div className="topnav-right">
        <span className="live-dot" />
        <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
          Captured {lastRefreshStr}
        </span>
        <button className="refresh-btn" onClick={handleRefresh} title="Capture a new investigation for the selected tenant and dates" type="button" disabled={!run || spinning}>
          <svg
            width="14" height="14" viewBox="0 0 14 14" fill="none"
            style={{ transition: 'transform 0.8s ease', transform: spinning ? 'rotate(360deg)' : 'rotate(0deg)' }}
          >
            <path d="M13 2v4H9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M1 12v-4h4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M2.34 8.5A6 6 0 0 1 12.6 5.5M11.66 5.5A6 6 0 0 1 1.4 8.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
          </svg>
          Refresh
        </button>
        <button className="btn btn-secondary" type="button" onClick={changePersona}>{MANAGER_ROLES[role]} · Switch persona</button>
        <select
          className="tenant-select" aria-label="Business unit"
          value={tenant}
          onChange={(e) => setTenant(e.target.value as typeof tenant)}
        >
          {TENANTS.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>
    </nav>
  )
}

export function SideNav({ page, setPage }: Props) {
  return (
    <aside className="sidenav">
      <div className="sidenav-section">Navigation</div>
      {NAV_ITEMS.filter(item => diagnosticsEnabled || ['dashboard', 'brief', 'incidents'].includes(item.id)).map((item) => (
        <button
          key={item.id}
          className={`sidenav-item ${page === item.id ? 'active' : ''}`}
          onClick={() => setPage(item.id)}
          type="button"
        >
          <span style={{ fontSize: '1rem', minWidth: 20, textAlign: 'center' }}>{item.icon}</span>
          <span>{item.label}</span>
        </button>
      ))}
    </aside>
  )
}
