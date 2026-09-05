import { useState } from 'react'
import { httpApi } from '../../core/api'
import { identityFor, useAppStore } from '../../core/store'
import type { QuestionResponse } from '../../core/contracts'
import { EvidenceSummary } from '../../shared/EvidenceSummary'
import { plain } from '../../core/presentation'

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
    <p className="page-subtitle">Ask about {tenant}. {run ? `Report as of ${run.asOfDate}.` : 'Default analysis date: 8 June 2026.'}</p>
    <form className="card card-body" onSubmit={e => { e.preventDefault(); void ask(question) }}>
      <label htmlFor="copilot-question">Your question</label>
      <textarea id="copilot-question" value={question} onChange={e => setQuestion(e.target.value)} rows={3} maxLength={500} placeholder="Which vendors contributed to the increase in delays?" />
      <button className="btn btn-primary" disabled={busy || !question.trim()}>{busy ? 'Preparing your answer…' : 'Ask Copilot'}</button>
    </form>
    <div className="suggestion-row">{(answer?.followUps ?? run?.suggestedQuestions ?? []).map(q => <button className="btn btn-secondary" disabled={busy} key={q} onClick={() => void ask(q)}>{q}</button>)}</div>
    {error && <div className="caveat-ribbon" role="alert">{error}</div>}
    {answer && <>
      <div className="hero-banner"><div className="hero-badge">{answer.refused ? 'Request declined' : 'Your explanation'}</div><p style={{ whiteSpace: 'pre-wrap' }}>{plain(answer.answer)}</p>{answer.refusalReason && <p>{answer.refusalReason}</p>}</div>
      <EvidenceSummary findings={answer.supportingFindings} caveats={answer.caveats} evidence={answer.evidence} />
      {answer.runId && answer.runId !== run?.runId && <button className="btn btn-primary" disabled={busy} onClick={() => void openAnswerRun()}>Open the supporting report</button>}
    </>}
  </div>
}
