import { useAppStore } from '../../core/store'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

export function ScorecardPage() {
  const { run } = useAppStore()
  const trust = run?.trust
  const latencyData = trust ? [{ label: 'This run', value: trust.latencyMs }] : []
  const callData = trust ? [
    { label: 'Model', value: trust.modelCalls }, { label: 'Fallback', value: trust.fallbackCalls },
    { label: 'Tool', value: trust.toolCalls },
  ] : []
  const gates = ['G1 · Delay spike', 'G2 · Degraded data', 'G3 · False alert', 'SEC · Security', 'AUDIT · Audit']
  const counters = ['Cross-tenant leaks', 'Unsupported numbers', 'Unauthorized actions', 'Duplicate effects', 'False escalations', 'Unbounded loops']
  return (
    <div>
      <h1 className="page-title">Acceptance Scorecard</h1>
      <p className="page-subtitle">{run ? `Current run ${run.runId}` : 'No investigation selected'} · Evaluation suite has not been run for this integration.</p>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header"><div className="card-title">Acceptance Gates</div></div>
        <div className="card-body">
          <div className="gate-row">
            {gates.map((g) => (
              <div key={g} className="gate-pill">
                <span>—</span>
                <strong>{g}</strong>
                <span style={{ fontWeight: 400, opacity: 0.85 }}>Not evaluated</span>
                <span style={{ opacity: 0.6, fontSize: '0.72rem' }}></span>
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
              {counters.map((z) => (
                <div key={z} className="zero-item">
                  <div className="zero-val">—</div>
                  <div className="zero-label">{z}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header"><div className="card-title">Recorded Run Performance</div></div>
          <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginBottom: 6 }}>Run latency (ms); no population percentiles</div>
              <ResponsiveContainer width="100%" height={110}>
                <BarChart data={latencyData} margin={{ top: 0, right: 4, left: -30, bottom: 0 }}>
                  <CartesianGrid stroke="rgba(0,0,0,0.06)" />
                  <XAxis dataKey="label" tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="value" fill="#3FA535" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginBottom: 6 }}>Call counts</div>
              <ResponsiveContainer width="100%" height={110}>
                <BarChart data={callData} margin={{ top: 0, right: 4, left: -30, bottom: 0 }}>
                  <CartesianGrid stroke="rgba(0,0,0,0.06)" />
                  <XAxis dataKey="label" tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="value" fill="#3C68D0" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="card-title">Evaluation Results</div>
          <div style={{ fontSize: '0.78rem', color: 'var(--green)', fontWeight: 700 }}>
            Not evaluated
          </div>
        </div>
        <div className="card-body" style={{ padding: 0 }}>
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Scenario</th><th>Agent</th><th>Result</th></tr>
            </thead>
            <tbody>
              <tr><td colSpan={4}>Run the dataset evaluation suite and review its versioned report before declaring acceptance gates passed.</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
