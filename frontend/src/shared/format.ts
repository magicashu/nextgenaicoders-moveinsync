import type { MetricResult, MetricUnit } from '../core/contracts'

/** Display formatting only. No arithmetic beyond presentation of values the API already computed. */
export function formatValue(value: number | null, unit: MetricUnit | string): string {
  if (value === null || value === undefined) return 'n/a'
  switch (unit) {
    case 'PERCENT':
      return `${value.toFixed(value >= 100 ? 0 : 2)}%`
    case 'MINUTES':
      return `${value.toFixed(1)} min`
    case 'CURRENCY':
      return value.toLocaleString('en-IN', { maximumFractionDigits: 2 })
    case 'CURRENCY_PER_KM':
      return `${value.toFixed(2)} / km`
    case 'PER_1000_TRIPS':
      return `${value.toFixed(2)} per 1,000 trips`
    case 'RATING':
      return value.toFixed(2)
    default:
      return value.toLocaleString('en-IN')
  }
}

export function formatDelta(metric: MetricResult): string {
  if (metric.delta === null) return 'no baseline'
  const sign = metric.delta > 0 ? '+' : ''
  if (metric.unit === 'PERCENT') return `${sign}${metric.delta.toFixed(2)} pts`
  return `${sign}${formatValue(metric.delta, metric.unit)}`
}

export function formatPopulation(metric: MetricResult): string {
  if (metric.numerator !== null && metric.denominator !== null) {
    return `${metric.numerator.toLocaleString('en-IN')} of ${metric.denominator.toLocaleString('en-IN')}`
  }
  return `${metric.supportingCount.toLocaleString('en-IN')} in population`
}

export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toISOString().replace('T', ' ').replace(/\.\d+Z$/, 'Z')
}

export function metricLabel(metricId: string): string {
  const words = metricId.slice(4).toLowerCase().split('_')
  return words.map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w)).join(' ')
}
