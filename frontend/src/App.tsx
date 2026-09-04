import { useCallback, useEffect, useState } from 'react'
import { AppShell, type View } from './app/AppShell'
import { useApi } from './app/ApiContext'
import { ApiRequestError } from './core/api'
import type { ApprovalDecisionRequest, ApprovalDecisionResponse, AuditResponse, Identity, MorningBriefResponse, QuestionResponse } from './core/contracts'
import { defaultIdentity } from './core/identity'
import { InvestigationPage } from './features/anomaly-investigation/InvestigationPage'
import { ApprovalPage } from './features/approval-inbox/ApprovalPage'
import { AuditPage } from './features/audit-trail/AuditPage'
import { AskDrawer } from './features/conversation/AskDrawer'
import { MorningBriefPage } from './features/morning-brief/MorningBriefPage'
import { TrustPanelView } from './features/trust-panel/TrustPanelView'
import { EvidenceDrawer } from './shared/EvidenceDrawer'
import { EmptyPanel, ErrorPanel, LoadingPanel } from './shared/StatePanels'

type Failure = { status?: number; message: string; error: ApiRequestError['error'] }

function toFailure(reason: unknown): Failure {
  if (reason instanceof ApiRequestError) return { status: reason.status, message: reason.message, error: reason.error }
  const anyReason = reason as { status?: number; message?: string }
  return { status: anyReason?.status, message: anyReason?.message ?? 'Unknown failure', error: null }
}

const TRACE_URL = (import.meta.env.VITE_LANGFUSE_URL as string | undefined) ?? null

export default function App() {
  const api = useApi()
  const [identity, setIdentity] = useState<Identity>(defaultIdentity)
  const [asOf, setAsOf] = useState('2026-06-08')
  const [view, setView] = useState<View>('brief')
  const [brief, setBrief] = useState<MorningBriefResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [failure, setFailure] = useState<Failure | null>(null)
  const [evidenceId, setEvidenceId] = useState<string | null>(null)
  const [askOpen, setAskOpen] = useState(false)
  const [askQuestion, setAskQuestion] = useState('')
  const [askBusy, setAskBusy] = useState(false)
  const [askResponse, setAskResponse] = useState<QuestionResponse | null>(null)
  const [askError, setAskError] = useState<string | null>(null)
  const [decisionBusy, setDecisionBusy] = useState(false)
  const [decisionResult, setDecisionResult] = useState<ApprovalDecisionResponse | null>(null)
  const [decisionError, setDecisionError] = useState<Failure | null>(null)
  const [audit, setAudit] = useState<AuditResponse | null>(null)
  const [auditFailure, setAuditFailure] = useState<Failure | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setFailure(null)
    setDecisionResult(null)
    setDecisionError(null)
    setAudit(null)
    setAskResponse(null)
    try {
      const result = await api.morningBrief(identity, asOf, identity.roles[0])
      setBrief(result)
    } catch (reason) {
      setBrief(null)
      setFailure(toFailure(reason))
    } finally {
      setLoading(false)
    }
  }, [api, identity, asOf])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (view !== 'audit' || !brief) return
    setAuditFailure(null)
    api.audit(identity, brief.workflowId).then(setAudit).catch((reason) => setAuditFailure(toFailure(reason)))
  }, [view, brief, identity, api, decisionResult])

  const decide = async (request: ApprovalDecisionRequest) => {
    if (!brief?.operations.approval) return
    setDecisionBusy(true)
    setDecisionError(null)
    try {
      const result = await api.decide(identity, brief.operations.approval.approvalId, request)
      setDecisionResult(result)
      const refreshed = await api.getWorkflow(identity, brief.workflowId).catch(() => null)
      if (refreshed) setBrief(refreshed)
    } catch (reason) {
      setDecisionError(toFailure(reason))
    } finally {
      setDecisionBusy(false)
    }
  }

  const ask = async (question: string) => {
    setAskBusy(true)
    setAskError(null)
    try {
      setAskResponse(await api.ask(identity, { question, asOfDate: asOf, relatedRunId: brief?.runId, persona: identity.roles[0] }))
    } catch (reason) {
      setAskError(toFailure(reason).message)
    } finally {
      setAskBusy(false)
    }
  }

  const openAsk = (question?: string) => {
    setAskQuestion(question ?? '')
    setAskResponse(null)
    setAskOpen(true)
    if (question) void ask(question)
  }

  let content
  if (loading) {
    content = <LoadingPanel label="Sensing governed metrics, detecting anomalies and investigating…" />
  } else if (failure || !brief) {
    content = <ErrorPanel status={failure?.status} error={failure?.error ?? null} message={failure?.message ?? 'No brief'} onRetry={() => void load()} />
  } else if (view === 'brief') {
    content = <MorningBriefPage brief={brief} onOpenEvidence={setEvidenceId} onReviewApproval={() => setView('approval')} onAsk={openAsk} onInvestigate={() => setView('investigation')} />
  } else if (view === 'investigation') {
    content = <InvestigationPage brief={brief} onOpenEvidence={setEvidenceId} />
  } else if (view === 'approval') {
    content = <ApprovalPage approval={brief.operations.approval} receipt={brief.operations.receipt} result={decisionResult} busy={decisionBusy} error={decisionError} onDecide={(r) => void decide(r)} />
  } else if (view === 'audit') {
    content = auditFailure ? <ErrorPanel status={auditFailure.status} error={auditFailure.error} message={auditFailure.message} /> : audit ? <AuditPage audit={audit} traceUrl={TRACE_URL} /> : <LoadingPanel label="Loading append-only audit events…" />
  } else {
    content = brief.trust ? <TrustPanelView trust={brief.trust} traceUrl={TRACE_URL} /> : <EmptyPanel title="No trust record" body="The run did not return telemetry." />
  }

  return (
    <AppShell identity={identity} onIdentity={setIdentity} asOf={asOf} onAsOf={setAsOf} view={view} onView={setView} onAsk={() => openAsk()} onRun={() => void load()}>
      {content}
      {brief && <EvidenceDrawer evidenceId={evidenceId} evidence={askResponse?.evidence ?? brief.evidence} trust={brief.trust} onClose={() => setEvidenceId(null)} />}
      <AskDrawer open={askOpen} initialQuestion={askQuestion} suggested={brief?.suggestedQuestions ?? []} busy={askBusy} response={askResponse} error={askError} onAsk={(q) => void ask(q)} onOpenEvidence={setEvidenceId} onClose={() => setAskOpen(false)} />
    </AppShell>
  )
}
