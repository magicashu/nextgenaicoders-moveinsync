import type { MorningBriefResponse } from '../../core/contracts'
import { EvidenceChips } from '../../shared/EvidenceChips'
import { MetricCard } from '../../shared/MetricCard'
import { EmptyPanel } from '../../shared/StatePanels'

type Props = {
  brief: MorningBriefResponse
  onOpenEvidence: (evidenceId: string) => void
  onReviewApproval: () => void
  onAsk: (question?: string) => void
  onInvestigate: () => void
}

export function MorningBriefPage({ brief, onOpenEvidence, onReviewApproval, onAsk, onInvestigate }: Props) {
  const ops = brief.operations
  const healthy = brief.status === 'HEALTHY'
  const impact = brief.evidence.items.find((i) => i.evidenceId.endsWith(':impact'))
  const confidence = brief.trust.confidence
  return (
    <>
      <section className={`hero panel ${healthy ? 'hero--healthy' : 'hero--anomaly'}`}>
        <div>
          <p className="status">{healthy ? 'Healthy · no material anomaly' : brief.status === 'REPORT_ONLY' ? 'Anomaly · report only' : 'Material anomaly · evidence verified'}</p>
          <h2>{ops.headline}</h2>
          <p>
            {brief.businessUnit} · as of {brief.asOfDate} · persona {brief.persona.replace('_', ' ').toLowerCase()} · contract {brief.trust.contractVersion} · data {brief.trust.dataVersion}
          </p>
        </div>
        <div className="confidence">
          {confidence === null ? '—' : `${Math.round(confidence * 100)}%`}
          <span>evidence confidence</span>
        </div>
      </section>

      <section className="metrics">
        <MetricCard kpi={ops.headlineKpi} tone={healthy ? 'good' : 'warning'} onOpen={onOpenEvidence} />
        {ops.supportingKpis.map((kpi) => (
          <MetricCard key={kpi.evidenceId} kpi={kpi} onOpen={onOpenEvidence} />
        ))}
      </section>

      {!healthy && impact && (
        <section className="panel impact">
          <p className="eyebrow">Business impact and benchmark</p>
          <div className="impact-grid">
            <button type="button" className="impact-tile" onClick={() => onOpenEvidence(impact.evidenceId)}>
              <strong>{impact.value.toLocaleString('en-IN')}</strong>
              <span>excess events versus the prior four complete weeks</span>
            </button>
            <button type="button" className="impact-tile" onClick={() => onOpenEvidence(impact.evidenceId)}>
              <strong>{impact.denominator?.toLocaleString('en-IN') ?? '—'}</strong>
              <span>rider legs on affected trips ({impact.numerator?.toLocaleString('en-IN') ?? '—'} excess)</span>
            </button>
            <div className="impact-tile">
              <strong>{ops.headlineKpi.configuredTarget ?? 'n/a'}</strong>
              <span>{ops.headlineKpi.targetLabel ?? 'no configured target'}</span>
            </div>
          </div>
        </section>
      )}

      <section className="content-grid">
        <article className="panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Investigation</p>
              <h3>What the workflow found</h3>
            </div>
            <button type="button" className="ghost" onClick={onInvestigate}>Drill down</button>
          </div>
          {ops.findings.length === 0 ? (
            <EmptyPanel title="No findings" body="All sensed metrics are within the materiality rule; the workflow did not investigate." />
          ) : (
            <ul className="findings">
              {ops.findings.map((f) => (
                <li key={f.claimId} className={`finding finding--${f.kind.toLowerCase()}`}>
                  <span className="kind">{f.kind === 'INFERRED' ? 'inferred' : 'measured'}</span>
                  <span>{f.text}</span>
                  <EvidenceChips evidenceIds={f.evidenceIds} onOpen={onOpenEvidence} />
                </li>
              ))}
            </ul>
          )}
          {ops.caveats.length > 0 && (
            <>
              <p className="eyebrow">Caveats and data quality</p>
              <ul className="caveats">
                {ops.caveats.map((c) => <li key={c.claimId}>{c.text}</li>)}
              </ul>
            </>
          )}
        </article>

        <article className={`panel action-panel ${healthy ? 'action-panel--none' : ''}`}>
          <p className="eyebrow">Recommended action</p>
          <h3>{ops.recommendedAction.title}</h3>
          <p>{ops.recommendedAction.rationale}</p>
          {ops.approval ? (
            <>
              <dl className="kv compact">
                <div><dt>Status</dt><dd>{ops.approval.status}</dd></div>
                <div><dt>Scope</dt><dd>{Object.entries(ops.approval.scope).filter(([k]) => !['businessUnit', 'metricId', 'windowEnd'].includes(k)).map(([k, v]) => `${k}: ${v}`).join(' · ')}</dd></div>
                <div><dt>Evidence</dt><dd>{ops.approval.evidenceVersion}</dd></div>
                <div><dt>Expires</dt><dd>{ops.approval.expiresAt.replace('T', ' ').replace('Z', ' UTC')}</dd></div>
              </dl>
              <button type="button" className="primary" onClick={onReviewApproval} disabled={ops.approval.status !== 'PENDING' && !ops.receipt}>
                {ops.approval.status === 'PENDING' ? 'Review and approve' : `View ${ops.approval.status.toLowerCase()} result`}
              </button>
              <small>No side effect executes from this screen. Approval triggers revalidation, one idempotent mock effect and an audit event.</small>
            </>
          ) : (
            <small>{healthy ? 'No approval request is raised for a healthy tenant.' : 'The policy gate routed this run to report-only; no approval was requested.'}</small>
          )}
        </article>
      </section>

      <section className="content-grid">
        <article className="panel leadership">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Leadership-ready summary</p>
              <h3>{brief.leadership.title}</h3>
            </div>
            <button type="button" className="ghost" onClick={() => navigator.clipboard?.writeText(brief.leadership.forwardableText)}>Copy to forward</button>
          </div>
          <ol>
            {brief.leadership.narrative.map((line) => <li key={line}>{line}</li>)}
          </ol>
          <small>Same evidence bundle as the operations view; facts cannot diverge.</small>
        </article>
        <article className="panel">
          <p className="eyebrow">Ask about this</p>
          <h3>Contextual questions</h3>
          <ul className="questions">
            {brief.suggestedQuestions.map((q) => (
              <li key={q}><button type="button" className="link" onClick={() => onAsk(q)}>{q}</button></li>
            ))}
          </ul>
          <small>Scoped to {brief.businessUnit}; governed tools only; no SQL, no cross-tenant data.</small>
        </article>
      </section>
    </>
  )
}
