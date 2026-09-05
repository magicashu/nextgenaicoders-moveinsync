import { scorecardData, AGENT_COLORS } from '../../core/mockData'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'

const latencyData = [
  { label: 'P50', value: scorecardData.performance.p50Ms, fill: '#10b981' },
  { label: 'P95', value: scorecardData.performance.p95Ms, fill: '#3FA535' },
  { label: 'Max', value: scorecardData.performance.maxMs, fill: '#3C68D0' },
]

const callData = [
  { label: 'Tool Calls', value: scorecardData.performance.toolCalls, fill: '#3C68D0' },
  { label: 'Fallback', value: scorecardData.performance.fallbackCalls, fill: '#f59e0b' },
  { label: 'Model', value: scorecardData.performance.modelCalls, fill: '#6366f1' },
  { label: 'Max Tools', value: scorecardData.performance.maxToolCalls, fill: '#e2e8f0' },
]

const passingCount = scorecardData.scenarios.filter(s => s.pass).length
const totalCount = scorecardData.scenarios.length
const passRate = Math.round((passingCount / totalCount) * 100)

const gatesAll = scorecardData.gates.every(g => g.pass)
const zerosAll = scorecardData.zeroTolerance.every(z => z.value === 0)

export function ScorecardPage() {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
        <h1 className="page-title">Acceptance Scorecard</h1>
        <div style={{
          padding: '4px 14px', borderRadius: 999, fontSize: '0.72rem', fontWeight: 800,
          background: 'rgba(16,185,129,0.12)', color: 'var(--green)',
          border: '1px solid rgba(16,185,129,0.3)',
        }}>
          ALL GATES PASS ✓
        </div>
      </div>
      <p className="page-subtitle">Generated {scorecardData.generatedAt} · {scorecardData.target}</p>

      {/* Hero strip */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 12, marginBottom: 20 }}>
        {[
          {
            label: 'Scenario Pass Rate',
            value: `${passRate}%`,
            sub: `${passingCount}/${totalCount} scenarios`,
            color: passRate >= 90 ? 'var(--green)' : passRate >= 70 ? 'var(--yellow)' : 'var(--red)',
            bg: 'rgba(16,185,129,0.08)',
          },
          {
            label: 'Acceptance Gates',
            value: gatesAll ? 'ALL PASS' : 'FAILURE',
            sub: `${scorecardData.gates.length} gates checked`,
            color: gatesAll ? 'var(--green)' : 'var(--red)',
            bg: gatesAll ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
          },
          {
            label: 'Zero-Tolerance',
            value: zerosAll ? '0 Violations' : 'FAIL',
            sub: `${scorecardData.zeroTolerance.length} counters verified`,
            color: zerosAll ? 'var(--green)' : 'var(--red)',
            bg: zerosAll ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
          },
          {
            label: 'Median Latency',
            value: `${scorecardData.performance.p50Ms}ms`,
            sub: `P95: ${scorecardData.performance.p95Ms}ms`,
            color: 'var(--accent)',
            bg: 'rgba(6,182,212,0.08)',
          },
        ].map(h => (
          <div key={h.label} className="card" style={{ padding: '16px 18px', background: h.bg, borderColor: h.color + '30' }}>
            <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 6 }}>{h.label}</div>
            <div style={{ fontSize: '1.6rem', fontWeight: 900, color: h.color, lineHeight: 1 }}>{h.value}</div>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginTop: 4 }}>{h.sub}</div>
          </div>
        ))}
      </div>

      {/* Acceptance Gates */}
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header">
          <div className="card-title">Acceptance Gates</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--green)', fontWeight: 700 }}>
            {scorecardData.gates.filter(g => g.pass).length}/{scorecardData.gates.length} passing
          </div>
        </div>
        <div className="card-body">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {scorecardData.gates.map((g) => (
              <div key={g.id} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '10px 14px', borderRadius: 10,
                background: g.pass ? 'rgba(16,185,129,0.06)' : 'rgba(239,68,68,0.06)',
                border: `1px solid ${g.pass ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
              }}>
                <div style={{
                  width: 28, height: 28, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: g.pass ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                  color: g.pass ? 'var(--green)' : 'var(--red)', fontSize: 13, fontWeight: 900, flexShrink: 0,
                }}>
                  {g.pass ? '✓' : '✗'}
                </div>
                <div style={{ minWidth: 50 }}>
                  <span style={{ fontSize: '0.78rem', fontWeight: 900, color: g.pass ? 'var(--green)' : 'var(--red)', fontFamily: 'monospace' }}>{g.id}</span>
                </div>
                <div style={{ flex: 1, fontSize: '0.85rem', color: 'var(--text)', fontWeight: 600 }}>{g.label}</div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', fontFamily: 'monospace', background: 'var(--bg3)', padding: '2px 8px', borderRadius: 4 }}>
                  {g.latencyMs}ms
                </div>
                <div style={{ padding: '3px 10px', borderRadius: 999, fontSize: '0.7rem', fontWeight: 800,
                  background: g.pass ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                  color: g.pass ? 'var(--green)' : 'var(--red)' }}>
                  {g.pass ? 'PASS' : 'FAIL'}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="two-col" style={{ marginBottom: 16 }}>
        {/* Zero-Tolerance Counters */}
        <div className="card">
          <div className="card-header">
            <div className="card-title">Zero-Tolerance Counters</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--green)', fontWeight: 700 }}>All zero ✓</div>
          </div>
          <div className="card-body">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {scorecardData.zeroTolerance.map((z) => (
                <div key={z.label} style={{
                  padding: '12px 14px', borderRadius: 10,
                  background: z.value === 0 ? 'rgba(16,185,129,0.06)' : 'rgba(239,68,68,0.1)',
                  border: `1px solid ${z.value === 0 ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.3)'}`,
                }}>
                  <div style={{ fontSize: '1.5rem', fontWeight: 900, color: z.value === 0 ? 'var(--green)' : 'var(--red)', lineHeight: 1 }}>
                    {z.value}
                  </div>
                  <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginTop: 4, lineHeight: 1.3 }}>{z.label}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Performance */}
        <div className="card">
          <div className="card-header"><div className="card-title">Performance Metrics</div></div>
          <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginBottom: 6, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>Latency (ms)</div>
              <ResponsiveContainer width="100%" height={100}>
                <BarChart data={latencyData} margin={{ top: 0, right: 4, left: -28, bottom: 0 }}>
                  <XAxis dataKey="label" tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                    {latencyData.map((d, i) => <Cell key={i} fill={d.fill} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginBottom: 6, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>Agent Call Counts</div>
              <ResponsiveContainer width="100%" height={100}>
                <BarChart data={callData} margin={{ top: 0, right: 4, left: -28, bottom: 0 }}>
                  <XAxis dataKey="label" tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                    {callData.map((d, i) => <Cell key={i} fill={d.fill} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>

      {/* Scenarios Table */}
      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="card-title">Dataset Scenarios DS-01 → DS-20</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              padding: '4px 14px', borderRadius: 999, fontSize: '0.75rem', fontWeight: 800,
              background: 'rgba(16,185,129,0.12)', color: 'var(--green)',
              border: '1px solid rgba(16,185,129,0.3)',
            }}>
              {passingCount}/{totalCount} PASSING · {passRate}%
            </div>
          </div>
        </div>
        <div className="card-body" style={{ padding: 0 }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: 70 }}>ID</th>
                <th>Scenario</th>
                <th style={{ width: 110 }}>Agent</th>
                <th style={{ width: 90 }}>Result</th>
              </tr>
            </thead>
            <tbody>
              {scorecardData.scenarios.map((s) => (
                <tr key={s.id} style={{ background: s.pass ? undefined : 'rgba(239,68,68,0.04)' }}>
                  <td className="td-mono" style={{ fontWeight: 800, color: 'var(--accent)' }}>{s.id}</td>
                  <td style={{ fontSize: '0.82rem' }}>{s.label}</td>
                  <td>
                    <span className="agent-badge" style={{
                      color: AGENT_COLORS[s.agent] ?? 'var(--text-dim)',
                      borderColor: `${AGENT_COLORS[s.agent] ?? '#1e3a5f'}44`,
                      background: `${AGENT_COLORS[s.agent] ?? '#1e3a5f'}12`,
                    }}>{s.agent}</span>
                  </td>
                  <td>
                    <span style={{
                      padding: '3px 8px', borderRadius: 999, fontSize: '0.72rem', fontWeight: 800,
                      background: s.pass ? 'rgba(16,185,129,0.12)' : 'rgba(239,68,68,0.12)',
                      color: s.pass ? 'var(--green)' : 'var(--red)',
                    }}>
                      {s.pass ? '✓ PASS' : '✗ FAIL'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
