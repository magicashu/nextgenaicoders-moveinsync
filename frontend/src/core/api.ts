import type { DecisionBrief } from './contracts'

export const fixtureBrief: DecisionBrief = {
  runId: '00000000-0000-0000-0000-000000000001',
  businessUnit: 'pinnacle-Slc',
  asOfDate: '2026-06-08',
  headline: 'pinnacle-Slc: delayed-trip rate increased to 30.00%',
  metric: {
    metricId: 'M01_DELAYED_TRIP_RATE',
    metricName: 'Delayed-trip rate',
    unit: 'PERCENT',
    status: 'SUPPORTED',
    value: 30,
    baselineValue: 10,
    delta: 20,
    numerator: 3,
    denominator: 10,
    supportingCount: 10,
    periodStart: '2026-06-01',
    periodEnd: '2026-06-07',
    filters: {},
    contractVersion: 'metrics-v1.1',
    dataVersion: 'fixture-v1',
    source: 'sql/metrics/m01_delayed_trip_rate.sql',
    caveats: [],
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
    items: [
      {
        evidenceId: 'pinnacle-Slc:m01:2026-06-07',
        metricId: 'M01_DELAYED_TRIP_RATE',
        value: 30,
        unit: 'PERCENT',
        baselineValue: 10,
        delta: 20,
        numerator: 3,
        denominator: 10,
        supportingCount: 10,
        periodStart: '2026-06-01',
        periodEnd: '2026-06-07',
        filters: {},
        source: 'sql/metrics/m01_delayed_trip_rate.sql',
        contractVersion: 'metrics-v1.1',
        dataVersion: 'fixture-v1',
      },
    ],
    confidence: 1,
    coverage: 10,
    caveats: ['Tiny fixture for scaffold verification; not a production claim.'],
  },
  status: 'AWAITING_APPROVAL',
}

export async function fetchDemoBrief(): Promise<DecisionBrief> {
  if (import.meta.env.VITE_USE_MOCKS === 'true') {
    return fixtureBrief
  }
  const response = await fetch('/api/v1/demo/brief?asOf=2026-06-08', {
    headers: { 'X-Business-Unit': 'pinnacle-Slc' },
  })
  if (!response.ok) {
    throw new Error(`Brief request failed with status ${response.status}`)
  }
  return response.json() as Promise<DecisionBrief>
}
