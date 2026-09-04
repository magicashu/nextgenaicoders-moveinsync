export type MetricResult = {
  metricId: string
  metricName: string
  unit: 'PERCENT' | 'MINUTES' | 'CURRENCY' | 'CURRENCY_PER_KM' | 'PER_1000_TRIPS' | 'RATING' | 'COUNT'
  status: 'SUPPORTED' | 'UNSUPPORTED'
  value: number | null
  baselineValue: number | null
  delta: number | null
  numerator: number | null
  denominator: number | null
  supportingCount: number
  periodStart: string
  periodEnd: string
  filters: Record<string, string>
  contractVersion: string
  dataVersion: string
  source: string
  caveats: string[]
}

export type EvidenceBundle = {
  items: Array<{
    evidenceId: string
    metricId: string
    value: number
    unit: string
    baselineValue: number | null
    delta: number | null
    numerator: number | null
    denominator: number | null
    supportingCount: number
    periodStart: string
    periodEnd: string
    filters: Record<string, string>
    source: string
    contractVersion: string
    dataVersion: string
  }>
  confidence: number
  coverage: number
  caveats: string[]
}

export type DecisionBrief = {
  runId: string
  businessUnit: string
  asOfDate: string
  headline: string
  metric: MetricResult
  findings: string[]
  recommendedAction: {
    actionId: string
    type: string
    title: string
    rationale: string
    status: string
  }
  evidence: EvidenceBundle
  status: string
}
