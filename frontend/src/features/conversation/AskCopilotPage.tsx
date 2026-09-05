import { useState } from 'react'
import { httpApi } from '../../core/api'
import { identityFor, useAppStore } from '../../core/store'
import type { QuestionResponse } from '../../core/contracts'
import { TrustPanelView } from '../trust-panel/TrustPanelView'

export function AskCopilotPage({ onOpenRun }: { onOpenRun: () => void }) {
  const { tenant, run, epoch, busy } = useAppStore()
  const [question, setQuestion] = useState('')
  const [answer, setAnswer] = useState<QuestionResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const ask = async (text: string) => {
    if (!text.trim() || useAppStore.getState().busy) return
    setQuestion(text); setError(null); setAnswer(null)
    useAppStore.setState({ busy: true })
    try {
      const response = await httpApi.ask(identityFor(tenant), { question: text, relatedRunId: run?.runId, asOfDate: run?.asOfDate ?? '2026-06-08' })
      if (useAppStore.getState().epoch === epoch) setAnswer(response)
    } catch (e) { if (useAppStore.getState().epoch === epoch) setError(e instanceof Error ? e.message : 'Question failed') }
    finally { if (useAppStore.getState().epoch === epoch) useAppStore.setState({ busy: false }) }
  }
  const openAnswerRun = async () => {
    if (!answer?.runId || useAppStore.getState().busy) return
    useAppStore.setState({ busy: true })
    try {
      const next = await httpApi.getWorkflow(identityFor(tenant), answer.runId)
      if (useAppStore.getState().epoch === epoch) { useAppStore.getState().setRun(next, epoch); onOpenRun() }
    } catch (e) { if (useAppStore.getState().epoch === epoch) setError(e instanceof Error ? e.message : 'Unable to open investigation') }
    finally { if (useAppStore.getState().epoch === epoch) useAppStore.setState({ busy: false }) }
  }
  return <div>
    <h1 className="page-title">Ask Copilot</h1>
    <p className="page-subtitle">Ask about {tenant}. {run ? `Context: run ${run.runId}, as of ${run.asOfDate}.` : 'Default analysis date: 8 June 2026.'}</p>
    <form className="card card-body" onSubmit={e => { e.preventDefault(); void ask(question) }}>
      <label htmlFor="copilot-question">Your question</label>
      <textarea id="copilot-question" value={question} onChange={e => setQuestion(e.target.value)} rows={3} maxLength={4000} placeholder="Which vendors contributed to the increase in delays?" />
      <button className="btn btn-primary" disabled={busy || !question.trim()}>{busy ? 'Investigating…' : 'Ask Copilot'}</button>
    </form>
    <div className="suggestion-row">{(answer?.followUps ?? run?.suggestedQuestions ?? []).map(q => <button className="btn btn-secondary" disabled={busy} key={q} onClick={() => void ask(q)}>{q}</button>)}</div>
    {error && <div className="caveat-ribbon" role="alert">{error}</div>}
    {answer && <>
      <div className="hero-banner"><div className="hero-badge">{answer.refused ? 'Request declined' : answer.intent}</div><p style={{ whiteSpace: 'pre-wrap' }}>{answer.answer}</p>{answer.refusalReason && <p>{answer.refusalReason}</p>}</div>
      <div className="card card-body"><h3>Supporting evidence</h3>{answer.supportingFindings.map(f => <p key={f.claimId}>{f.text}<small> · {f.evidenceIds.join(', ')}</small></p>)}
        {answer.caveats.map(f => <p className="caveat-ribbon" key={f.claimId}>{f.text}</p>)}
        {answer.evidence && <details><summary>Evidence values and provenance</summary><pre>{JSON.stringify(answer.evidence, null, 2)}</pre></details>}
      </div>
      {answer.runId && answer.runId !== run?.runId && <button className="btn btn-primary" disabled={busy} onClick={() => void openAnswerRun()}>Open this investigation's brief, workflow and audit</button>}
      {answer.trust && <TrustPanelView trust={answer.trust} traceUrl={import.meta.env.VITE_LANGFUSE_URL ?? null} />}
    </>}
  </div>
}
