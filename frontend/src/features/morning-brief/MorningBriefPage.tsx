import { useEffect, useState } from 'react'
import { fetchDemoBrief } from '../../core/api'
import type { DecisionBrief } from '../../core/contracts'
import { MetricCard } from '../../shared/MetricCard'

export function MorningBriefPage() {
  const [brief, setBrief] = useState<DecisionBrief | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchDemoBrief().then(setBrief).catch((reason: Error) => setError(reason.message))
  }, [])

  if (error) {
    return (
      <main className="shell">
        <section className="panel error-panel">
          <p className="eyebrow">Backend unavailable</p>
          <h1>Start Spring Boot or set VITE_USE_MOCKS=true.</h1>
          <code>{error}</code>
        </section>
      </main>
    )
  }

  if (!brief) {
    return <main className="shell loading">Preparing the governed morning brief…</main>
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Mobility Decision Copilot</p>
          <h1>Morning operations brief</h1>
        </div>
        <div className="tenant-pill">{brief.businessUnit} · as of {brief.asOfDate}</div>
      </header>

      <section className="hero panel">
        <div>
          <p className="status">Material anomaly · evidence verified</p>
          <h2>{brief.headline}</h2>
          <p>Compared with the prior four complete weeks using governed contract {brief.metric.contractVersion}.</p>
        </div>
        <div className="confidence">{Math.round(brief.evidence.confidence * 100)}%<span>evidence confidence</span></div>
      </section>

      <section className="metrics">
        <MetricCard label="Current" value={`${brief.metric.valuePercent.toFixed(1)}%`} detail={`${brief.metric.numerator} of ${brief.metric.denominator} trips`} tone="warning" />
        <MetricCard label="Baseline" value={`${brief.metric.baselinePercent.toFixed(1)}%`} detail="Prior four complete weeks" />
        <MetricCard label="Change" value={`+${brief.metric.deltaPercentagePoints.toFixed(1)} pp`} detail="Deterministic materiality rule" tone="warning" />
      </section>

      <section className="content-grid">
        <article className="panel">
          <p className="eyebrow">Investigation</p>
          <h3>What the workflow found</h3>
          <ul>{brief.findings.map((finding) => <li key={finding}>{finding}</li>)}</ul>
        </article>
        <article className="panel action-panel">
          <p className="eyebrow">Recommended action</p>
          <h3>{brief.recommendedAction.title}</h3>
          <p>{brief.recommendedAction.rationale}</p>
          <button type="button" disabled>Review before approval</button>
          <small>No side effect executes from this sample.</small>
        </article>
      </section>

      <section className="panel trust-panel">
        <div>
          <p className="eyebrow">Trust record</p>
          <h3>{brief.evidence.items[0].evidenceId}</h3>
        </div>
        <dl>
          <div><dt>Metric</dt><dd>{brief.evidence.items[0].metricId}</dd></div>
          <div><dt>SQL</dt><dd>{brief.evidence.items[0].source}</dd></div>
          <div><dt>Coverage</dt><dd>{brief.evidence.coverage} trips</dd></div>
          <div><dt>Status</dt><dd>{brief.status}</dd></div>
        </dl>
      </section>
    </main>
  )
}
