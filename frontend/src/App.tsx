import { lazy, Suspense, useState } from 'react'
import { TopNav, SideNav } from './shared/Nav'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { SupervisorCopilotPage } from './features/copilot/SupervisorCopilotPage'
const WorkflowGraph3D = lazy(() => import('./features/workflow/WorkflowGraph3D').then(m => ({ default: m.WorkflowGraph3D })))
import { DecisionBriefPage } from './features/brief/DecisionBriefPage'
import { AuditPage } from './features/audit/AuditPage'
import { ScorecardPage } from './features/scorecard/ScorecardPage'
import { useAppStore } from './core/store'
import { TrustPanelView } from './features/trust-panel/TrustPanelView'
import { PersonaGate } from './features/persona/PersonaGate'
import { IncidentsPage } from './features/incidents/IncidentsPage'
import { diagnosticsEnabled } from './core/presentation'
import './styles.css'

export default function App() {
  const [page, setPage] = useState('dashboard')
  const { tenant, role, personaChosen, run, error } = useAppStore()

  if (!personaChosen) return <PersonaGate onContinue={() => setPage('dashboard')} />

  return (
    <div className="app-shell">
      <TopNav page={page} setPage={setPage} />
      <div className="main-area">
        <SideNav page={page} setPage={setPage} />
        <main className="page-content" key={`${tenant}:${role}`}>
          {error && <div className="caveat-ribbon" role="alert">{error}</div>}
          {diagnosticsEnabled && page === 'trust' && (run ? <TrustPanelView trust={run.trust} traceUrl={import.meta.env.VITE_LANGFUSE_URL ?? null} /> : <div className="card card-body">Run an investigation to inspect LLM calls, evidence and node decisions.</div>)}
          {page === 'incidents' && <IncidentsPage />}
          {page === 'dashboard' && <DashboardPage />}
          {diagnosticsEnabled && page === 'workflow' && <Suspense fallback={<div className="card card-body">Loading 3D workflow…</div>}><WorkflowGraph3D /></Suspense>}
          {page === 'brief' && <DecisionBriefPage onIncidents={() => setPage('incidents')} />}
          {diagnosticsEnabled && page === 'audit' && <AuditPage />}
          {diagnosticsEnabled && page === 'scorecard' && <ScorecardPage />}
        </main>
      </div>
      {/* Shared text and voice Copilot */}
      <SupervisorCopilotPage key={`${tenant}:${role}:${run?.runId ?? 'loading'}`} onOpenReport={() => setPage('brief')} />
    </div>
  )
}
