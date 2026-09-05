/**
 * API layer — all calls hit the real backend at /api (proxied to port 8081).
 * Set VITE_USE_MOCKS=true in .env.local to bypass the backend entirely.
 */

import { scorecardData } from './mockData'
import type { DecisionBrief } from './contracts'

const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true'

// ── Types mirroring InvestigationController ───────────────────────────────

export interface EvidencePayload {
  evidenceId: string
  status: 'AVAILABLE' | 'UNAVAILABLE' | 'DEGRADED' | string
  value: number | null
  numerator: number | null
  denominator: number | null
  population: number
  unit: string
  metricVersion: string
  sourceReference: string
  warnings: string[]
}

export interface ActionPayload {
  actionId: string
  type: string
  title: string
  rationale: string
  status: string
}

export interface InvestigateResponse {
  metricId: string
  dateFrom: string
  dateTo: string
  dataVersion: string
  primaryEvidence: EvidencePayload | null
  allEvidence: EvidencePayload[]
  operationalSummary: string
  leadershipSummary: string
  verificationStatus: 'VERIFIED' | 'QUALIFIED' | 'REJECTED' | string
  findings: string[]
  proposedActions: ActionPayload[]
  caveats: string[]
  warnings: string[]
}

export interface MetricMeta {
  id: string
  contractId: string
  unit: string
}

export interface GroupRow {
  groupKey: string
  value: number
  overallValue: number
  numerator: number | null
  denominator: number | null
  population: number
}

export interface BreakdownResponse {
  metricId: string
  dimension: string
  dateFrom: string
  dateTo: string
  dataVersion: string
  overallValue: number
  rows: GroupRow[]
}

// Dashboard model consumed by DashboardPage
export interface DashboardData {
  runId: string
  businessUnit: string
  headline: string
  metric: {
    metricId: string
    value: number
    baseline: number
    delta: number
    numerator: number
    denominator: number
    periodStart: string
    periodEnd: string
    contractVersion: string
    dataVersion: string
  }
  findings: string[]
  recommendedAction: ActionPayload | null
  evidence: { confidence: number; coverage: number; caveats: string[] }
  operationalSummary: string
  leadershipSummary: string
  verificationStatus: string
  status: string
  toolCalls: number
  maxToolCalls: number
}

// Vendor / site row shapes used by charts
export interface VendorRow {
  vendor: string
  value: number
  baseline: number
  delta: number
  trips: number
  share: number
}

export interface SiteRow {
  site: string
  value: number
  baseline: number
  delta: number
  trips: number
  share: number
}

export interface TrendPoint {
  week: string
  delayed: number
  baseline: number
}

// ── Core fetch helpers ────────────────────────────────────────────────────

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error((err as { error?: string }).error ?? `Request failed: ${res.status}`)
  }
  return res.json() as Promise<T>
}

// ── Public API functions ──────────────────────────────────────────────────

export async function fetchInvestigation(
  businessUnit: string,
  metricId: string,
  dateFrom: string,
  dateTo: string,
): Promise<InvestigateResponse> {
  return post('/api/v1/investigate', { businessUnit, metricId, dateFrom, dateTo })
}

export async function fetchBreakdown(
  businessUnit: string,
  metricId: string,
  dimension: 'vendor_id' | 'site_id',
  dateFrom: string,
  dateTo: string,
): Promise<BreakdownResponse> {
  return post('/api/v1/investigate/breakdown', { businessUnit, metricId, dimension, dateFrom, dateTo })
}

export async function fetchAvailableMetrics(): Promise<MetricMeta[]> {
  const res = await fetch('/api/v1/investigate/metrics')
  if (!res.ok) throw new Error(`Metrics list failed: ${res.status}`)
  return res.json() as Promise<MetricMeta[]>
}

// ── Dashboard ─────────────────────────────────────────────────────────────

function apiToDashboard(r: InvestigateResponse, businessUnit: string): DashboardData {
  const ev = r.primaryEvidence
  const value = ev?.value ?? 0
  const denom = ev?.denominator ?? 0
  const numer = ev?.numerator ?? 0

  const headlineVal = value.toFixed(1)
  const headline = r.leadershipSummary ||
    `${businessUnit}: delayed-trip rate is ${headlineVal}% for selected period`

  return {
    runId: crypto.randomUUID(),
    businessUnit,
    headline,
    metric: {
      metricId: r.metricId,
      value,
      baseline: 0,
      delta: 0,
      numerator: numer,
      denominator: denom,
      periodStart: r.dateFrom,
      periodEnd: r.dateTo,
      contractVersion: ev?.metricVersion ?? 'M01-v1.1',
      dataVersion: r.dataVersion,
    },
    findings: r.findings,
    recommendedAction: r.proposedActions[0] ?? null,
    evidence: {
      confidence: r.verificationStatus === 'VERIFIED' ? 0.94
                : r.verificationStatus === 'QUALIFIED' ? 0.72 : 0.5,
      coverage: denom,
      caveats: [],
    },
    operationalSummary: r.operationalSummary,
    leadershipSummary: r.leadershipSummary,
    verificationStatus: r.verificationStatus,
    status: r.proposedActions.length > 0 ? 'AWAITING_APPROVAL' : 'COMPLETED',
    toolCalls: r.allEvidence.length,
    maxToolCalls: 40,
  }
}

export async function fetchDashboard(
  businessUnit: string,
  dateFrom: string,
  dateTo: string,
): Promise<DashboardData> {
  const raw = await fetchInvestigation(businessUnit, 'M01_DELAYED_TRIP_RATE', dateFrom, dateTo)
  return apiToDashboard(raw, businessUnit)
}

// ── Vendor breakdown → VendorRow[] ────────────────────────────────────────

export async function fetchVendorBreakdown(
  businessUnit: string,
  dateFrom: string,
  dateTo: string,
): Promise<VendorRow[]> {
  const data = await fetchBreakdown(businessUnit, 'M01_DELAYED_TRIP_RATE', 'vendor_id', dateFrom, dateTo)
  const overall = data.overallValue
  const totalTrips = data.rows.reduce((s, r) => s + (r.denominator ?? 0), 0)

  return data.rows.map(r => ({
    vendor: r.groupKey,
    value: r.value,
    baseline: overall,
    delta: parseFloat((r.value - overall).toFixed(2)),
    trips: r.denominator ?? r.population,
    share: totalTrips > 0 ? parseFloat(((r.denominator ?? 0) / totalTrips * 100).toFixed(1)) : 0,
  }))
}

// ── Site breakdown → SiteRow[] ────────────────────────────────────────────

export async function fetchSiteBreakdown(
  businessUnit: string,
  dateFrom: string,
  dateTo: string,
): Promise<SiteRow[]> {
  const data = await fetchBreakdown(businessUnit, 'M01_DELAYED_TRIP_RATE', 'site_id', dateFrom, dateTo)
  const overall = data.overallValue
  const totalTrips = data.rows.reduce((s, r) => s + (r.denominator ?? 0), 0)

  return data.rows.map(r => ({
    site: r.groupKey,
    value: r.value,
    baseline: overall,
    delta: parseFloat((r.value - overall).toFixed(2)),
    trips: r.denominator ?? r.population,
    share: totalTrips > 0 ? parseFloat(((r.denominator ?? 0) / totalTrips * 100).toFixed(1)) : 0,
  }))
}

// ── Trend — 7 weekly windows ending at dateTo ─────────────────────────────

function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function weekLabel(dateStr: string): string {
  const d = new Date(dateStr)
  const month = d.toLocaleString('en', { month: 'short' })
  // approximate week number within month
  const week = Math.ceil(d.getDate() / 7)
  return `W${week} ${month}`
}

export async function fetchTrend(
  businessUnit: string,
  _dateFrom: string,
  dateTo: string,
): Promise<TrendPoint[]> {
  // Build 7 weekly windows (each 7 days wide) ending at dateTo
  const weeks: Array<{ from: string; to: string }> = []
  for (let i = 6; i >= 0; i--) {
    const wEnd = addDays(dateTo, -i * 7)
    const wStart = addDays(wEnd, -6)
    weeks.push({ from: wStart, to: wEnd })
  }

  const results = await Promise.allSettled(
    weeks.map(w =>
      fetchInvestigation(businessUnit, 'M01_DELAYED_TRIP_RATE', w.from, w.to)
        .then(r => ({
          week: weekLabel(w.to),
          delayed: r.primaryEvidence?.value ?? 0,
        }))
    )
  )

  const points: TrendPoint[] = results.map((r, i) => ({
    week: weekLabel(weeks[i].to),
    delayed: r.status === 'fulfilled' ? r.value.delayed : 0,
    baseline: 0,
  }))

  // Use the mean of all weeks as the baseline reference line
  const validValues = points.map(p => p.delayed).filter(v => v > 0)
  const baseline = validValues.length > 0
    ? parseFloat((validValues.reduce((a, b) => a + b, 0) / validValues.length).toFixed(2))
    : 0

  return points.map(p => ({ ...p, baseline }))
}

// ── Scorecard (static implementation metadata) ────────────────────────────

export async function fetchScorecard(): Promise<typeof scorecardData> {
  return scorecardData
}

// ── Legacy compat (MorningBriefPage + its test) ───────────────────────────

export const fixtureBrief: DecisionBrief = {
  runId: '00000000-0000-0000-0000-000000000001',
  businessUnit: 'pinnacle-Slc',
  asOfDate: '2026-06-08',
  headline: 'pinnacle-Slc: delayed-trip rate increased to 30.00%',
  metric: {
    metricId: 'M01_DELAYED_TRIP_RATE', metricName: 'Delayed-trip rate',
    valuePercent: 30, baselinePercent: 10, deltaPercentagePoints: 20,
    numerator: 3, denominator: 10,
    periodStart: '2026-06-01', periodEnd: '2026-06-07',
    contractVersion: 'M01-v1', dataVersion: 'fixture-v1',
  },
  findings: [
    'Delayed-trip rate rose materially against the prior four complete weeks.',
    'Current: 30.00%; prior four weeks: 10.00%; change: 20.00 percentage points.',
    'Worker-specific attribution is the next implementation slice.',
  ],
  recommendedAction: {
    actionId: '00000000-0000-0000-0000-000000000002',
    type: 'CREATE_WATCHLIST',
    title: 'Create a site-shift watchlist',
    rationale: 'Investigate the deterioration before assigning vendor blame.',
    status: 'DRAFT_REQUIRES_APPROVAL',
  },
  evidence: {
    items: [{
      evidenceId: 'pinnacle-Slc:m01:2026-06-07', metricId: 'M01_DELAYED_TRIP_RATE',
      valuePercent: 30, baselinePercent: 10, numerator: 3, denominator: 10,
      source: 'sql/metrics/m01_delayed_trip_rate.sql',
      contractVersion: 'M01-v1', dataVersion: 'fixture-v1',
    }],
    confidence: 1, coverage: 10,
    caveats: ['Tiny fixture for scaffold verification; not a production claim.'],
  },
  status: 'AWAITING_APPROVAL',
}

export async function fetchDemoBrief(): Promise<DecisionBrief> {
  if (USE_MOCKS) return fixtureBrief
  const response = await fetch('/api/v1/demo/brief?asOf=2026-06-08', {
    headers: { 'X-Business-Unit': 'pinnacle-Slc' },
  })
  if (!response.ok) throw new Error(`Brief request failed: ${response.status}`)
  return response.json() as Promise<DecisionBrief>
}
