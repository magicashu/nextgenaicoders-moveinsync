export const TENANTS = ['pinnacle-Slc', 'vanta-Sea', 'vanta-Aus', 'catalyst-Sac', 'orbit-Slc'] as const
export type Tenant = typeof TENANTS[number]

export const WORKFLOW_NODES = [
  'INITIALIZE_RUN', 'AUTHORIZE_SCOPE', 'PROFILE_DATASET', 'BUILD_CAPABILITY_MATRIX',
  'COMPUTE_METRIC_SNAPSHOT', 'DETECT_ANOMALIES', 'PRIORITIZE_ISSUE', 'SUPERVISOR_PLAN',
  'VALIDATE_PLAN', 'RUN_INVESTIGATIONS', 'MERGE_EVIDENCE', 'EVIDENCE_CRITIC',
  'VERIFY_EVIDENCE', 'COMPOSE_DECISION_BRIEF', 'ACTION_POLICY_GATE', 'APPROVAL_INTERRUPT',
] as const
export type WorkflowNode = typeof WORKFLOW_NODES[number]

export const NODE_AGENT: Record<WorkflowNode, string> = {
  INITIALIZE_RUN: 'system', AUTHORIZE_SCOPE: 'system', PROFILE_DATASET: 'system',
  BUILD_CAPABILITY_MATRIX: 'system', COMPUTE_METRIC_SNAPSHOT: 'system',
  DETECT_ANOMALIES: 'system', PRIORITIZE_ISSUE: 'system',
  SUPERVISOR_PLAN: 'supervisor', VALIDATE_PLAN: 'supervisor',
  RUN_INVESTIGATIONS: 'investigator', MERGE_EVIDENCE: 'investigator',
  EVIDENCE_CRITIC: 'critic', VERIFY_EVIDENCE: 'critic',
  COMPOSE_DECISION_BRIEF: 'briefing', ACTION_POLICY_GATE: 'briefing',
  APPROVAL_INTERRUPT: 'briefing',
}

export const AGENT_COLORS: Record<string, string> = {
  system: '#6366f1', supervisor: '#06b6d4', investigator: '#10b981', critic: '#f59e0b', briefing: '#ec4899',
}

export const vendorData = [
  { vendor: 'Rohan Mikhailov Travel', value: 22.17, baseline: 12.72, delta: 9.45, trips: 2891, share: 14.7 },
  { vendor: 'Aarav Mikhailov Travel', value: 20.78, baseline: 13.02, delta: 7.76, trips: 2161, share: 10.3 },
  { vendor: 'Karan Mikhailov Travel', value: 22.37, baseline: 14.11, delta: 8.26, trips: 1949, share: 10.0 },
  { vendor: 'Sanjay Mikhailov Travel', value: 22.68, baseline: 12.10, delta: 10.58, trips: 1667, share: 8.7 },
  { vendor: 'Pooja Mikhailov Travel', value: 28.37, baseline: 15.67, delta: 12.70, trips: 1248, share: 8.1 },
  { vendor: 'Amit Mikhailov Travel', value: 22.06, baseline: 11.84, delta: 10.22, trips: 1283, share: 6.5 },
  { vendor: 'Rahul Morozov Travel', value: 19.51, baseline: 9.09, delta: 10.42, trips: 1420, share: 6.4 },
  { vendor: 'Divya Mikhailov Travel', value: 21.83, baseline: 12.47, delta: 9.36, trips: 1237, share: 6.2 },
  { vendor: 'Arjun Mikhailov Travel', value: 23.22, baseline: 11.73, delta: 11.49, trips: 1150, share: 6.1 },
  { vendor: 'Rahul Orlov Travel', value: 24.95, baseline: 12.66, delta: 12.29, trips: 994, share: 5.7 },
]

export const siteData = [
  { site: 'Clearwater Campus', value: 24.07, baseline: 12.86, delta: 11.21, trips: 9247, share: 51.1 },
  { site: 'Willow Bend Campus', value: 19.33, baseline: 9.64, delta: 9.69, trips: 5560, share: 24.7 },
  { site: 'Oakmont Office', value: 20.58, baseline: 13.27, delta: 7.31, trips: 4907, share: 23.2 },
  { site: 'San Jose Commons', value: 72.09, baseline: 71.63, delta: 0.46, trips: 43, share: 0.7 },
  { site: 'Ashford Commons', value: 9.45, baseline: 18.37, delta: -8.92, trips: 127, share: 0.3 },
]

export const scorecardData = {
  generatedAt: '2026-09-05T01:32:06Z',
  target: 'http://localhost:8080',
  gates: [
    { id: 'G1', label: 'Delay spike brief produced', pass: true, latencyMs: 82 },
    { id: 'G2', label: 'Degraded-data caveats visible', pass: true, latencyMs: 79 },
    { id: 'G3', label: 'False alert not escalated', pass: true, latencyMs: 80 },
    { id: 'SEC', label: 'Adversarial cases refused', pass: true, latencyMs: 12 },
    { id: 'AUDIT', label: 'Audit trail tenant-scoped', pass: true, latencyMs: 15 },
  ],
  zeroTolerance: [
    { label: 'Cross-tenant leaks', value: 0 },
    { label: 'Unsupported numbers shown', value: 0 },
    { label: 'Unauthorized actions', value: 0 },
    { label: 'Duplicate effects', value: 0 },
    { label: 'G3 escalations', value: 0 },
    { label: 'Unbounded loops', value: 0 },
  ],
  performance: {
    modelCalls: 0, fallbackCalls: 3, toolCalls: 6, maxToolCalls: 12,
    p50Ms: 79, p95Ms: 81, maxMs: 82,
  },
  scenarios: Array.from({ length: 20 }, (_, i) => ({
    id: `DS-${String(i + 1).padStart(2, '0')}`,
    label: [
      'Delayed-trip rate for pinnacle-Slc week of 2026-06-07',
      'Baseline delayed-trip rate prior 4 weeks',
      'Early/late trip share for pinnacle-Slc',
      'Median cost per leg for pinnacle-Slc',
      'Cost-per-km unsupported (zero-km issue)',
      'Excluded sign-off violation events count',
      'Cross-tenant trip ID collisions detection',
      'Duplicate bill lines and legs removed',
      'Delay minutes capped above 600 min',
      'Marshal rating-0 rows excluded',
      'Vendor attribution for top delay contributors',
      'Site concentration for delay spike',
      'Shift direction analysis for delays',
      'No-show roster pattern detection',
      'Feedback correlation with delays',
      'Tracking safety events correlation',
      'G1 golden: brief produced with evidence',
      'G2 golden: degraded data caveats shown',
      'G3 golden: regime change not escalated',
      'SEC: cross-tenant request rejected',
    ][i],
    pass: i < 17 || i === 18 || i === 19,
    agent: ['supervisor', 'investigator', 'critic', 'briefing'][i % 4],
  })),
}

export const g1RunArtifact = {
  runId: 'd881b8ed-f206-479f-8cd8-d4b157a0e677',
  businessUnit: 'pinnacle-Slc',
  asOfDate: '2026-06-08',
  finalStep: 'AWAITING_APPROVAL' as WorkflowNode,
  transitions: WORKFLOW_NODES as unknown as WorkflowNode[],
  toolCalls: 6,
  maxToolCalls: 12,
  correctionCycles: 0,
  maxCorrectionCycles: 1,
  headline: 'pinnacle-Slc: delayed-trip rate rose to 21.88% (+9.6 pp above baseline)',
  metric: {
    metricId: 'M01_DELAYED_TRIP_RATE', metricName: 'Delayed Trip Rate',
    value: 21.88, baseline: 12.28, delta: 9.6,
    numerator: 4357, denominator: 19913,
    periodStart: '2026-06-01', periodEnd: '2026-06-07',
    contractVersion: 'metrics-v1.1', dataVersion: 'data-8ed5b4eae158',
  },
  findings: [
    'Delayed-trip rate rose 9.6 pp above the prior-four-week baseline (12.28%).',
    'Clearwater Campus accounts for 51.1% of delayed trips (2,226 of 4,357).',
    '14 of 17 vendors show elevated delay rates vs baseline.',
    'Pooja Mikhailov Travel has the highest absolute rate: 28.37% (+12.7 pp).',
    'No evidence of data-quality degradation; coverage 19,913 eligible trips.',
  ],
  recommendedAction: {
    actionId: 'action-001', type: 'CREATE_WATCHLIST',
    title: 'Create vendor and site watchlist for pinnacle-Slc',
    rationale: 'Concentrate review on Clearwater Campus vendors before assigning vendor blame.',
    status: 'DRAFT_REQUIRES_APPROVAL',
  },
  evidence: {
    confidence: 0.94, coverage: 19913,
    caveats: ['77 delay values capped at 600 min; 4 quarantined above 1440 min.'],
  },
  status: 'AWAITING_APPROVAL',
  nodeDurations: {
    INITIALIZE_RUN: 12, AUTHORIZE_SCOPE: 8, PROFILE_DATASET: 34, BUILD_CAPABILITY_MATRIX: 21,
    COMPUTE_METRIC_SNAPSHOT: 45, DETECT_ANOMALIES: 28, PRIORITIZE_ISSUE: 15,
    SUPERVISOR_PLAN: 62, VALIDATE_PLAN: 18, RUN_INVESTIGATIONS: 88, MERGE_EVIDENCE: 31,
    EVIDENCE_CRITIC: 55, VERIFY_EVIDENCE: 22, COMPOSE_DECISION_BRIEF: 71,
    ACTION_POLICY_GATE: 9, APPROVAL_INTERRUPT: 5,
  },
}

export const trendData = [
  { week: 'W1 May', delayed: 11.2, baseline: 12.1 },
  { week: 'W2 May', delayed: 12.8, baseline: 12.2 },
  { week: 'W3 May', delayed: 11.9, baseline: 12.1 },
  { week: 'W4 May', delayed: 12.5, baseline: 12.3 },
  { week: 'W1 Jun', delayed: 14.1, baseline: 12.2 },
  { week: 'W2 Jun', delayed: 18.3, baseline: 12.3 },
  { week: 'W3 Jun', delayed: 21.88, baseline: 12.28 },
]

export const auditEvents = [
  { id: 'AUD-001', event: 'RUN_STARTED', agent: 'system', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:01:12Z' },
  { id: 'AUD-002', event: 'ANOMALY_DETECTED', agent: 'supervisor', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:01:19Z' },
  { id: 'AUD-003', event: 'INVESTIGATION_COMPLETE', agent: 'investigator', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:01:48Z' },
  { id: 'AUD-004', event: 'CRITIQUE_PASSED', agent: 'critic', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:02:03Z' },
  { id: 'AUD-005', event: 'BRIEF_COMPOSED', agent: 'briefing', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:02:21Z' },
  { id: 'AUD-006', event: 'AWAITING_APPROVAL', agent: 'system', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:02:22Z' },
]
