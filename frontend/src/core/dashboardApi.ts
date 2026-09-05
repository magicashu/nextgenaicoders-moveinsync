import { httpApi } from './api'
import { identityHeaders } from './identity'
import { identityFor, useAppStore } from './store'
import type { MetricResult, MorningBriefResponse } from './contracts'

export function asOfFor(from: string, to: string): string {
  const start = Date.parse(from + 'T00:00:00Z'), end = Date.parse(to + 'T00:00:00Z')
  if (!Number.isFinite(start) || !Number.isFinite(end) || end - start !== 6 * 86400000)
    throw new Error('Select seven consecutive days. This workflow compares that week with the preceding four weeks.')
  return new Date(end + 86400000).toISOString().slice(0, 10)
}
export function rangeFor(asOf: string) {
  const end = Date.parse(asOf + 'T00:00:00Z')
  return { from: new Date(end - 7 * 86400000).toISOString().slice(0, 10), to: new Date(end - 86400000).toISOString().slice(0, 10) }
}
export async function startInvestigation(tenant: string, from: string, to: string) {
  const asOf = asOfFor(from, to)
  const state = useAppStore.getState()
  if (state.busy) throw new Error('An operation is already running.')
  useAppStore.setState({ busy: true, error: null })
  try {
    const run = await httpApi.startWorkflow(identityFor(tenant), asOf)
    useAppStore.getState().setRun(run, state.epoch)
    return run
  } finally {
    if (useAppStore.getState().epoch === state.epoch) useAppStore.setState({ busy: false })
  }
}

type RankingRow = { member: string; currentValue: number | null; baselineValue: number | null; delta: number | null; currentNumerator: number; currentDenominator: number; shareOfCurrentNumerator: number; qualified: boolean }
type Ranking = { rows: RankingRow[]; caveats: string[]; evidenceId: string; source: string }
export type Charts = { metric: MetricResult; onTime: MetricResult; vendors: Ranking; sites: Ranking; trend: { points: { date: string; value: number | null }[]; source: string } }
export async function fetchCharts(tenant: string, asOf: string): Promise<Charts> {
  const response = await fetch(`/api/v1/dashboard?asOf=${encodeURIComponent(asOf)}`, { headers: identityHeaders(identityFor(tenant)) })
  if (!response.ok) throw new Error(`Charts unavailable (${response.status}). The investigation is still available in Decision Brief.`)
  return response.json()
}
export type VendorRow = { vendor: string; value: number; baseline: number | null; delta: number | null; trips: number; share: number }
export type SiteRow = Omit<VendorRow, 'vendor'> & { site: string }
export type TrendPoint = { week: string; delayed: number | null; baseline: number | null }
export type DashboardData = {
  runId: string; businessUnit: string; headline: string; metric: MetricResult & { value: number; numerator: number; denominator: number };
  onTime: MetricResult; findings: string[]; recommendedAction: MorningBriefResponse['operations']['recommendedAction'];
  evidence: MorningBriefResponse['evidence']; operationalSummary: string; leadershipSummary: string;
  verificationStatus: string; status: string; toolCalls: number; evidenceCount: number;
}
export function dashboardView(run: MorningBriefResponse, charts: Charts) {
  const metric = charts.metric
  if (metric.value == null || metric.numerator == null || metric.denominator == null)
    throw new Error(metric.caveats.join(' ') || 'No supported delayed-trip metric for this window.')
  const row = (r: RankingRow) => ({ value: r.currentValue!, baseline: r.baselineValue, delta: r.delta, trips: r.currentDenominator, share: r.shareOfCurrentNumerator })
  return {
    dashboard: {
      runId: run.runId, businessUnit: run.businessUnit, headline: run.operations.headline,
      metric: { ...metric, value: metric.value, numerator: metric.numerator, denominator: metric.denominator },
      onTime: charts.onTime, findings: run.operations.findings.map(f => f.text),
      recommendedAction: run.operations.recommendedAction, evidence: { ...run.evidence, caveats: [...new Set([...run.evidence.caveats, ...metric.caveats, ...charts.vendors.caveats, ...charts.sites.caveats])] },
      operationalSummary: run.operations.headline, leadershipSummary: run.leadership.narrative.join(' '),
      verificationStatus: run.trust.finalStep, status: run.operations.receipt?.status ?? run.status, toolCalls: run.trust.toolCalls, evidenceCount: run.evidence.items.length,
    } satisfies DashboardData,
    vendors: charts.vendors.rows.filter(r => r.currentValue != null).map(r => ({ ...row(r), vendor: r.member })),
    sites: charts.sites.rows.filter(r => r.currentValue != null).map(r => ({ ...row(r), site: r.member })),
    trend: charts.trend.points.map(p => ({ week: p.date, delayed: p.value, baseline: metric.baselineValue })),
  }
}

export function briefView(run: MorningBriefResponse) {
  const m = run.operations.headlineKpi.metric
  return {
    dateFrom: m.periodStart, dateTo: m.periodEnd, metricId: m.metricId, metricName: m.metricName,
    verificationStatus: run.trust.finalStep, leadershipSummary: run.leadership.narrative.join(' '),
    operationalSummary: run.operations.headline, primaryEvidence: m,
    findings: run.operations.findings.map(f => f.text), proposedActions: run.operations.recommendedAction ? [run.operations.recommendedAction] : [],
    allEvidence: run.evidence.items.map(e => ({ ...e, metricVersion: e.metricId, population: e.supportingCount, status: 'AVAILABLE' })),
    caveats: [...new Set([...run.evidence.caveats, ...run.errors, ...run.operations.caveats.map(f => f.text)])],
  }
}
export function formatValue(value: number | null, unit: string): string {
  if (value == null) return '—'
  return `${value.toLocaleString(undefined, { maximumFractionDigits: 2 })}${unit === 'PERCENT' ? '%' : ` ${unit.toLowerCase().replaceAll('_', ' ')}`}`
}
