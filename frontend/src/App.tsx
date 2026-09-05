import { useState } from 'react'
import { TopNav, SideNav } from './shared/Nav'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { SupervisorCopilotPage } from './features/copilot/SupervisorCopilotPage'
import { WorkflowGraph3D } from './features/workflow/WorkflowGraph3D'
import { DecisionBriefPage } from './features/brief/DecisionBriefPage'
import { AuditPage } from './features/audit/AuditPage'
import { ScorecardPage } from './features/scorecard/ScorecardPage'
import './styles.css'

export default function App() {
  const [page, setPage] = useState('dashboard')

  return (
    <div className="app-shell">
      <TopNav page={page} setPage={setPage} />
      <div className="main-area">
        <SideNav page={page} setPage={setPage} />
        <main className="page-content">
          {page === 'dashboard' && <DashboardPage />}
          {page === 'workflow' && <WorkflowGraph3D />}
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
