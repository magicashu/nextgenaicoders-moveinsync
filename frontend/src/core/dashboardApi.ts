import { httpApi } from './api'
import { identityHeaders } from './identity'
import { captureKey, chartKey, identityFor, useAppStore } from './store'
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
const investigations = new Map<string, Promise<MorningBriefResponse>>()
export async function startInvestigation(tenant: string, from: string, to: string, refresh = false) {
  const asOf = asOfFor(from, to)
  const key = captureKey(tenant, asOf)
  const state = useAppStore.getState()
  if (tenant !== state.tenant) throw new Error('Tenant selection changed')
  // Join an existing request before considering a second capture, including refresh clicks.
  const pending = investigations.get(key)
  if (pending) {
    useAppStore.setState({ busy: true })
    try {
      const run = await pending
      useAppStore.getState().capture(run, state.epoch)
      return run
    } finally {
      if (useAppStore.getState().epoch === state.epoch) useAppStore.setState({ busy: false })
    }
  }
  if (!refresh) {
    const captured = state.selectCapture(key)
    if (captured) return captured
  }
  if (state.busy) throw new Error('An operation is already running.')
  useAppStore.setState({ busy: true, error: null })
  const request = (async () => {
    try {
      const run = refresh
        ? await httpApi.startWorkflow(identityFor(tenant), asOf, undefined, true)
        : await httpApi.morningBrief(identityFor(tenant), asOf)
      await fetchCharts(tenant, asOf, run.trust.dataVersion)
      useAppStore.getState().capture(run, state.epoch)
      return run
    } finally {
      investigations.delete(key)
      if (useAppStore.getState().epoch === state.epoch) useAppStore.setState({ busy: false })
    }
  })()
  investigations.set(key, request)
  return request
}

import type { Charts, RankingRow } from './dashboardTypes'
const chartRequests = new Map<string, Promise<Charts>>()
export function cachedCharts(tenant: string, asOf: string, dataVersion: string) {
  return useAppStore.getState().charts[chartKey(tenant, asOf, dataVersion)]
}
export async function fetchCharts(tenant: string, asOf: string, dataVersion: string): Promise<Charts> {
  const key = chartKey(tenant, asOf, dataVersion)
  const cached = cachedCharts(tenant, asOf, dataVersion)
  if (cached) return cached
  const pending = chartRequests.get(key)
  if (pending) return pending
  const request = (async () => {
    try {
      const response = await fetch(`/api/v1/dashboard?asOf=${encodeURIComponent(asOf)}`, { headers: identityHeaders(identityFor(tenant)) })
      if (!response.ok) throw new Error(`Charts unavailable (${response.status}). The investigation is still available in Decision Brief.`)
      const charts: Charts = await response.json()
      if (charts.metric.dataVersion !== dataVersion) throw new Error('The dataset version changed. Refresh to capture a consistent investigation and charts.')
      useAppStore.getState().saveCharts(key, charts)
      return charts
    } finally { chartRequests.delete(key) }
  })()
  chartRequests.set(key, request)
  return request
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
    shifts: charts.shifts,
    noShow: charts.noShow,
    cost: charts.cost,
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
