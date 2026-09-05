import type { Kpi } from '../core/contracts'
import { formatDelta, formatPopulation, formatValue } from './format'

type Props = { kpi: Kpi; tone?: 'neutral' | 'warning' | 'good'; onOpen: (evidenceId: string) => void }

export function MetricCard({ kpi, tone = 'neutral', onOpen }: Props) {
  const unsupported = kpi.metric.status === 'UNSUPPORTED'
  return (
    <button type="button" className={`metric-card metric-card--${unsupported ? 'unsupported' : tone}`} onClick={() => onOpen(kpi.evidenceId)} aria-label={`${kpi.label} evidence`}>
      <span>{kpi.label}</span>
      {unsupported ? (
        <>
          <strong className="unsupported">Unsupported</strong>
          <small>{kpi.metric.caveats[0]?.replace(/^Unsupported: /, '') ?? 'not computable for this tenant'}</small>
        </>
      ) : (
        <>
          <strong>{formatValue(kpi.metric.value, kpi.metric.unit)}</strong>
          <small>{formatDelta(kpi.metric)} vs {kpi.comparison}</small>
          <small>{formatPopulation(kpi.metric)}</small>
          {kpi.configuredTarget && (
            <small className={kpi.meetsTarget ? 'target target--met' : 'target target--missed'}>
              {kpi.meetsTarget ? 'Meets' : 'Misses'} {kpi.configuredTarget} · {kpi.targetLabel}
            </small>
          )}
        </>
      )}
    </button>
  )
}
