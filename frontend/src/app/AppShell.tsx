import type { ReactNode } from 'react'
import type { Identity } from '../core/contracts'
import { ROLES, TENANTS } from '../core/identity'
import { useMocks } from '../core/api'

export type View = 'brief' | 'investigation' | 'approval' | 'audit' | 'trust'

type Props = {
  identity: Identity
  onIdentity: (identity: Identity) => void
  asOf: string
  onAsOf: (value: string) => void
  view: View
  onView: (view: View) => void
  onAsk: () => void
  onRun: () => void
  children: ReactNode
}

const VIEWS: Array<{ id: View; label: string }> = [
  { id: 'brief', label: 'Morning brief' },
  { id: 'investigation', label: 'Investigation' },
  { id: 'approval', label: 'Approval' },
  { id: 'audit', label: 'Audit' },
  { id: 'trust', label: 'Trust' },
]

export function AppShell({ identity, onIdentity, asOf, onAsOf, view, onView, onAsk, onRun, children }: Props) {
  return (
    <div className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">MoveIn Sync Mobility Dashboard</p>
          <h1>Proactive operations brief</h1>
        </div>
        <form className="identity" onSubmit={(e) => e.preventDefault()} aria-label="Trusted identity (demo stand-in for gateway claims)">
          <label>
            Tenant
            <select value={identity.businessUnit} onChange={(e) => onIdentity({ ...identity, businessUnit: e.target.value })}>
              {TENANTS.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            Persona
            <select value={identity.roles[0]} onChange={(e) => onIdentity({ ...identity, roles: [e.target.value] })}>
              {ROLES.map((r) => (
                <option key={r} value={r}>{r.replace('_', ' ').toLowerCase()}</option>
              ))}
            </select>
          </label>
          <label>
            As of
            <input type="date" value={asOf} min="2026-05-29" max="2026-08-01" onChange={(e) => onAsOf(e.target.value)} />
          </label>
          <button type="button" className="primary" onClick={onRun}>Run brief</button>
          <button type="button" className="ghost" onClick={onAsk}>Ask about this</button>
          <span className={`badge ${useMocks() ? 'badge--mock' : 'badge--live'}`}>{useMocks() ? 'typed fixtures' : 'live API'}</span>
        </form>
      </header>
      <nav className="tabs" aria-label="Sections">
        {VIEWS.map((v) => (
          <button key={v.id} type="button" className={view === v.id ? 'tab tab--active' : 'tab'} onClick={() => onView(v.id)} aria-current={view === v.id}>
            {v.label}
          </button>
        ))}
      </nav>
      <main>{children}</main>
      <footer className="footer">
        Every number on this screen resolves to a governed evidence item. Targets are configured per tenant, never organizer-supplied. Actions execute only after approval, revalidation and audit.
      </footer>
    </div>
  )
}
