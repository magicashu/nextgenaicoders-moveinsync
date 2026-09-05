import { TENANTS } from '../core/workflowDesign'
import { useAppStore } from '../core/store'
import { diagnosticsEnabled, MANAGER_ROLES } from '../core/presentation'

const NAV_ICONS: Record<string, React.ReactElement> = {
  dashboard: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="1" width="6" height="6" rx="1" /><rect x="9" y="1" width="6" height="6" rx="1" />
      <rect x="1" y="9" width="6" height="6" rx="1" /><rect x="9" y="9" width="6" height="6" rx="1" />
    </svg>
  ),
  incidents: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 10.5c0 .83-.67 1.5-1.5 1.5H4l-3 3V3.5C1 2.67 1.67 2 2.5 2h10c.83 0 1.5.67 1.5 1.5v7z" />
    </svg>
  ),
  trust: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M8 1L2 4v4c0 3.31 2.69 6 6 6s6-2.69 6-6V4L8 1z" /><path d="M5.5 8l2 2 3-3" />
    </svg>
  ),
  workflow: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="3" cy="8" r="2" /><circle cx="13" cy="3" r="2" /><circle cx="13" cy="13" r="2" />
      <path d="M5 8h3.5M11 4.5l-2.5 3M11 11.5l-2.5-3" />
    </svg>
  ),
  brief: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 1H4a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V5l-3-4z" />
      <path d="M10 1v4h4M6 9h4M6 12h3" />
    </svg>
  ),
  audit: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M2 12V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v8" /><path d="M1 12h14" />
      <path d="M5 7h6M5 9.5h4" />
    </svg>
  ),
  scorecard: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M2 12l3-4 3 2 3-5 3 3" /><rect x="1" y="1" width="14" height="14" rx="2" />
    </svg>
  ),
}

const NAV_ITEMS = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'incidents', label: 'Incidents' },
  { id: 'trust', label: 'LLM & Trust' },
  { id: 'workflow', label: '3D Workflow' },
  { id: 'brief', label: 'Decision Brief' },
  { id: 'audit', label: 'Audit Trail' },
  { id: 'scorecard', label: 'Scorecard' },
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
        MoveIn Sync Mobility Dashboard
      </div>
      <div className="topnav-right">
        <span className="live-dot" />
        <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
          Captured {lastRefreshStr}
        </span>
        <button className="refresh-btn" onClick={handleRefresh} title="Capture a new investigation" type="button" disabled={!run || spinning}>
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
      {NAV_ITEMS.filter(item => diagnosticsEnabled || ['dashboard', 'brief', 'incidents', 'workflow', 'audit'].includes(item.id)).map((item) => {
        const isActive = page === item.id
        return (
          <button
            key={item.id}
            className={`sidenav-item ${isActive ? 'active' : ''}`}
            aria-current={isActive ? 'page' : undefined}
            onClick={() => setPage(item.id)}
            type="button"
            style={{
              borderLeft: isActive ? '3px solid var(--mis-green)' : '3px solid transparent',
              paddingLeft: 13,
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', opacity: isActive ? 1 : 0.55 }}>
              {NAV_ICONS[item.id]}
            </span>
            <span style={{ fontSize: '0.93rem' }}>{item.label}</span>
          </button>
        )
      })}
    </aside>
  )
}
