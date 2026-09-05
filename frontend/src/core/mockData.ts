// Re-export workflow design constants from workflowDesign (includes 2 extra nodes from main)
export { WORKFLOW_NODES, NODE_AGENT, AGENT_COLORS, TENANTS } from './workflowDesign'
export type { WorkflowNode } from './workflowDesign'

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

export const auditEvents = [
  { id: 'AUD-001', event: 'RUN_STARTED', agent: 'system', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:01:12Z' },
  { id: 'AUD-002', event: 'ANOMALY_DETECTED', agent: 'supervisor', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:01:19Z' },
  { id: 'AUD-003', event: 'INVESTIGATION_COMPLETE', agent: 'investigator', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:01:48Z' },
  { id: 'AUD-004', event: 'CRITIQUE_PASSED', agent: 'critic', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:02:03Z' },
  { id: 'AUD-005', event: 'BRIEF_COMPOSED', agent: 'briefing', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:02:21Z' },
  { id: 'AUD-006', event: 'AWAITING_APPROVAL', agent: 'system', tenant: 'pinnacle-Slc', runId: 'd881b8ed', ts: '2026-06-08T06:02:22Z' },
]
