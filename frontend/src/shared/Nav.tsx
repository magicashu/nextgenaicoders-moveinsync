import { TENANTS } from '../core/mockData'
import { useAppStore } from '../core/store'

const NAV_ITEMS = [
  { id: 'dashboard', icon: '◈', label: 'Dashboard' },
  { id: 'workflow', icon: '⬡', label: '3D Workflow' },
  { id: 'brief', icon: '◉', label: 'Decision Brief' },
  { id: 'audit', icon: '◎', label: 'Audit Trail' },
  { id: 'scorecard', icon: '◆', label: 'Scorecard' },
]

interface Props {
  page: string
  setPage: (p: string) => void
}

export function TopNav({ page, setPage }: Props) {
  const { tenant, setTenant } = useAppStore()
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
        <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>Mock mode</span>
        <select
          className="tenant-select"
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
      {NAV_ITEMS.map((item) => (
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
