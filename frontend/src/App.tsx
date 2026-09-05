import { lazy, Suspense, useState } from 'react'
import { TopNav, SideNav } from './shared/Nav'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { SupervisorCopilotPage } from './features/copilot/SupervisorCopilotPage'
import { WorkflowGraph3D } from './features/workflow/WorkflowGraph3D'
const WorkflowGraph3D = lazy(() => import('./features/workflow/WorkflowGraph3D').then(m => ({ default: m.WorkflowGraph3D })))
import { DecisionBriefPage } from './features/brief/DecisionBriefPage'
import { AuditPage } from './features/audit/AuditPage'
import { ScorecardPage } from './features/scorecard/ScorecardPage'
import { useAppStore } from './core/store'
import { AskCopilotPage } from './features/conversation/AskCopilotPage'
import { TrustPanelView } from './features/trust-panel/TrustPanelView'
import './styles.css'

export default function App() {
  const [page, setPage] = useState('dashboard')
  const { tenant, run, error } = useAppStore()

  return (
    <div className="app-shell">
      <TopNav page={page} setPage={setPage} />
      <div className="main-area">
        <SideNav page={page} setPage={setPage} />
        <main className="page-content" key={tenant}>
          {error && <div className="caveat-ribbon" role="alert">{error}</div>}
          {page === 'ask' && <AskCopilotPage key={run?.runId ?? tenant} onOpenRun={() => setPage('brief')} />}
          {page === 'trust' && (run ? <TrustPanelView trust={run.trust} traceUrl={import.meta.env.VITE_LANGFUSE_URL ?? null} /> : <div className="card card-body">Run an investigation to inspect LLM calls, evidence and node decisions.</div>)}
          {page === 'dashboard' && <DashboardPage />}
          {page === 'workflow' && <Suspense fallback={<div className="card card-body">Loading 3D workflow…</div>}><WorkflowGraph3D /></Suspense>}
          {page === 'brief' && <DecisionBriefPage />}
          {page === 'audit' && <AuditPage />}
          {page === 'scorecard' && <ScorecardPage />}
        </main>
      </div>
      {/* Global Floating Supervisor Voice & Chat Copilot Widget */}
      <SupervisorCopilotPage />
    </div>
  )
}
