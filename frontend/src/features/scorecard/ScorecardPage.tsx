import { scorecardData, AGENT_COLORS } from '../../core/mockData'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

const latencyData = [
  { label: 'P50', value: scorecardData.performance.p50Ms },
  { label: 'P95', value: scorecardData.performance.p95Ms },
  { label: 'Max', value: scorecardData.performance.maxMs },
]

const callData = [
  { label: 'Model', value: scorecardData.performance.modelCalls },
  { label: 'Fallback', value: scorecardData.performance.fallbackCalls },
  { label: 'Tool', value: scorecardData.performance.toolCalls },
  { label: 'Max Tools', value: scorecardData.performance.maxToolCalls },
]

export function ScorecardPage() {
  const p = scorecardData.performance

  return (
    <div>
      <h1 className="page-title">Acceptance Scorecard</h1>
      <p className="page-subtitle">Generated {scorecardData.generatedAt} · Target: {scorecardData.target}</p>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header"><div className="card-title">Acceptance Gates</div></div>
        <div className="card-body">
          <div className="gate-row">
            {scorecardData.gates.map((g) => (
              <div key={g.id} className={`gate-pill ${g.pass ? 'pass' : 'fail'}`}>
                <span>{g.pass ? '✓' : '✗'}</span>
                <strong>{g.id}</strong>
                <span style={{ fontWeight: 400, opacity: 0.85 }}>{g.label}</span>
                <span style={{ opacity: 0.6, fontSize: '0.72rem' }}>{g.latencyMs}ms</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="two-col" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-header"><div className="card-title">Zero-Tolerance Counters</div></div>
          <div className="card-body">
            <div className="zero-grid">
              {scorecardData.zeroTolerance.map((z) => (
                <div key={z.label} className="zero-item">
                  <div className={`zero-val ${z.value !== 0 ? 'nonzero' : ''}`}>{z.value}</div>
                  <div className="zero-label">{z.label}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header"><div className="card-title">Performance</div></div>
          <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginBottom: 6 }}>Latency (ms)</div>
              <ResponsiveContainer width="100%" height={110}>
                <BarChart data={latencyData} margin={{ top: 0, right: 4, left: -30, bottom: 0 }}>
                  <CartesianGrid stroke="rgba(99,147,220,0.06)" />
                  <XAxis dataKey="label" tick={{ fill: '#7a97c0', fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#7a97c0', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#0d1526', border: '1px solid #1e3a5f', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="value" fill="#06b6d4" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginBottom: 6 }}>Call counts</div>
              <ResponsiveContainer width="100%" height={110}>
                <BarChart data={callData} margin={{ top: 0, right: 4, left: -30, bottom: 0 }}>
                  <CartesianGrid stroke="rgba(99,147,220,0.06)" />
                  <XAxis dataKey="label" tick={{ fill: '#7a97c0', fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#7a97c0', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#0d1526', border: '1px solid #1e3a5f', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="value" fill="#6366f1" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="card-title">Dataset Scenarios DS-01 → DS-20</div>
          <div style={{ fontSize: '0.78rem', color: 'var(--green)', fontWeight: 700 }}>
            {scorecardData.scenarios.filter(s => s.pass).length}/{scorecardData.scenarios.length} passing
          </div>
        </div>
        <div className="card-body" style={{ padding: 0 }}>
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Scenario</th><th>Agent</th><th>Result</th></tr>
            </thead>
            <tbody>
              {scorecardData.scenarios.map((s) => (
                <tr key={s.id}>
                  <td className="td-mono" style={{ fontWeight: 700 }}>{s.id}</td>
                  <td style={{ maxWidth: 360, fontSize: '0.8rem' }}>{s.label}</td>
                  <td>
                    <span className="agent-badge" style={{
                      color: AGENT_COLORS[s.agent] ?? 'var(--text-dim)',
                      borderColor: `${AGENT_COLORS[s.agent] ?? '#1e3a5f'}44`,
                      background: `${AGENT_COLORS[s.agent] ?? '#1e3a5f'}12`,
                    }}>{s.agent}</span>
                  </td>
                  <td className={s.pass ? 'scenario-pass' : 'scenario-fail'}>{s.pass ? '✓ PASS' : '✗ FAIL'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
