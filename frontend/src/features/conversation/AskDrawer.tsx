import { useState } from 'react'
import type { QuestionResponse } from '../../core/contracts'
import { EvidenceChips } from '../../shared/EvidenceChips'

type Props = {
  open: boolean
  initialQuestion: string
  suggested: string[]
  busy: boolean
  response: QuestionResponse | null
  error: string | null
  onAsk: (question: string) => void
  onOpenEvidence: (evidenceId: string) => void
  onClose: () => void
}

/** "Ask about this": same governed workflow, tenant-scoped, refusals are typed. Answers always link back to evidence. */
export function AskDrawer({ open, initialQuestion, suggested, busy, response, error, onAsk, onOpenEvidence, onClose }: Props) {
  const [question, setQuestion] = useState(initialQuestion)
  if (!open) return null
  return (
    <aside className="drawer drawer--ask" role="dialog" aria-label="Ask about this brief">
      <header>
        <p className="eyebrow">Ask about this</p>
        <h3>Contextual question</h3>
        <button type="button" className="ghost" onClick={onClose} aria-label="Close ask drawer">Close</button>
      </header>
      <form onSubmit={(e) => { e.preventDefault(); if (question.trim()) onAsk(question.trim()) }}>
        <label>
          Question
          <textarea value={question} onChange={(e) => setQuestion(e.target.value)} rows={3} maxLength={500} placeholder="Where is this anomaly concentrated?" />
        </label>
        <button type="submit" className="primary" disabled={busy || !question.trim()}>{busy ? 'Investigating…' : 'Ask'}</button>
      </form>
      <ul className="questions">
        {suggested.map((q) => <li key={q}><button type="button" className="link" onClick={() => { setQuestion(q); onAsk(q) }}>{q}</button></li>)}
      </ul>
      {error && <p className="pill pill--failed" role="alert">{error}</p>}
      {response && response.refused && (
        <section className="answer answer--refused" role="status">
          <p className="eyebrow">Not answered</p>
          <p>{response.answer}</p>
          <small>Reason: {response.refusalReason}</small>
        </section>
      )}
      {response && !response.refused && (
        <section className="answer" role="status">
          <p className="eyebrow">Answer · intent {response.intent.toLowerCase()} · workers {response.workers.join(', ')}</p>
          <p className="answer-text">{response.answer}</p>
          <ul className="findings">
            {response.supportingFindings.map((f) => (
              <li key={f.claimId} className="finding"><span>{f.text}</span><EvidenceChips evidenceIds={f.evidenceIds} onOpen={onOpenEvidence} /></li>
            ))}
          </ul>
          {response.caveats.length > 0 && <ul className="caveats">{response.caveats.map((c) => <li key={c.claimId}>{c.text}</li>)}</ul>}
          {response.trust && <small>Trace {response.trust.traceId} · confidence {response.trust.confidence === null ? '—' : `${Math.round(response.trust.confidence * 100)}%`} · {response.trust.toolCalls} tool calls</small>}
          {response.draftedAction && <small>Drafted action: {response.draftedAction.title} (requires approval; nothing executed)</small>}
        </section>
      )}
    </aside>
  )
}
