export type MetricResult = {
  metricId: string
  metricName: string
  valuePercent: number
  baselinePercent: number
  deltaPercentagePoints: number
  numerator: number
  denominator: number
  periodStart: string
  periodEnd: string
  contractVersion: string
  dataVersion: string
}

export type EvidenceBundle = {
  items: Array<{
    evidenceId: string
    metricId: string
    valuePercent: number
    baselinePercent: number
    numerator: number
    denominator: number
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
