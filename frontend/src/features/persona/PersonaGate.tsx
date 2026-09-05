import { useState } from 'react'
import { TENANTS } from '../../core/identity'
import { useAppStore, type Tenant } from '../../core/store'
import { MANAGER_ROLES, type ManagerRole } from '../../core/presentation'

const descriptions: Record<ManagerRole, string> = {
  TRANSPORT_MANAGER: 'Review delays, compare vendors and manage incident responses for daily transport operations.',
  FACILITIES_HEAD: 'Review performance, cost trends and operational risks, with a report ready for leadership.',
  LINE_MANAGER: 'Review arrival reliability and shift impact. This view is read-only; transport managers handle incident responses.',
}
export function PersonaGate({ onContinue }: { onContinue: () => void }) {
  const store = useAppStore()
  const [role, setRole] = useState<ManagerRole>(store.role)
  const [tenant, setTenant] = useState<Tenant>(store.tenant)
  return <main className="persona-screen">
    <div className="persona-welcome"><span className="hero-badge">MoveIn Sync Mobility Dashboard</span>
      <h1>How would you like to view your operations?</h1>
      <p>Choose your role and business unit to open a workspace tailored to your decisions.</p>
    </div>
    <form onSubmit={e => { e.preventDefault(); store.choosePersona(role, tenant); onContinue() }}>
      <fieldset className="persona-options"><legend>Select your persona</legend>
        {Object.entries(MANAGER_ROLES).map(([value, label]) => <label className={`card persona-option ${role === value ? 'selected' : ''}`} key={value}>
          <input type="radio" name="persona" value={value} checked={role === value} onChange={() => setRole(value as ManagerRole)} />
          <h2>{label}</h2><p>{descriptions[value as ManagerRole]}</p>
        </label>)}
      </fieldset>
      <div className="card card-body persona-scope"><label htmlFor="persona-business-unit">Business unit</label>
        <select id="persona-business-unit" className="tenant-select" value={tenant} onChange={e => setTenant(e.target.value as Tenant)}>{TENANTS.map(t => <option key={t}>{t}</option>)}</select>
        <p>This demo uses business-unit data. Individual manager and team assignments are not available in the supplied dataset.</p>
        <button className="btn btn-primary" type="submit">Open my dashboard →</button>
      </div>
    </form>
  </main>
}
