import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine,
  LineChart, Line, PieChart, Pie, Cell, Legend, CartesianGrid
} from 'recharts'
import { vendorData, siteData, trendData, g1RunArtifact } from '../../core/mockData'
import { useAppStore } from '../../core/store'

const COLORS = ['#06b6d4','#6366f1','#10b981','#f59e0b','#ec4899','#8b5cf6','#14b8a6','#f97316','#84cc16','#fb7185']

function MetricChips() {
  const m = g1RunArtifact.metric
  return (
    <div className="metric-grid" style={{ marginBottom: 16 }}>
      <div className="metric-chip chip-warning">
        <div className="metric-chip-label">Current Rate</div>
        <div className="metric-chip-value">{m.value.toFixed(1)}%</div>
        <div className="metric-chip-detail">{m.numerator.toLocaleString()} of {m.denominator.toLocaleString()} trips</div>
      </div>
      <div className="metric-chip chip-neutral">
        <div className="metric-chip-label">4-Week Baseline</div>
        <div className="metric-chip-value">{m.baseline.toFixed(1)}%</div>
        <div className="metric-chip-detail">Prior four complete weeks</div>
      </div>
      <div className="metric-chip chip-warning">
        <div className="metric-chip-label">Delta</div>
        <div className="metric-chip-value">+{m.delta.toFixed(1)} pp</div>
        <div className="metric-chip-detail">Above materiality threshold</div>
      </div>
    </div>
  )
}

function TrendChart() {
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-header">
        <div className="card-title">Delayed-Trip Rate Trend</div>
      </div>
      <div className="card-body">
        <ResponsiveContainer width="100%" height={180}>
          <LineChart data={trendData} margin={{ top: 4, right: 12, left: -20, bottom: 0 }}>
            <CartesianGrid stroke="rgba(99,147,220,0.08)" />
            <XAxis dataKey="week" tick={{ fill: '#7a97c0', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: '#7a97c0', fontSize: 11 }} axisLine={false} tickLine={false} domain={[8, 24]} tickFormatter={(v: number) => `${v}%`} />
            <Tooltip contentStyle={{ background: '#0d1526', border: '1px solid #1e3a5f', borderRadius: 8, fontSize: 12 }} labelStyle={{ color: '#e2eaf8' }} />
            <ReferenceLine y={12.28} stroke="#f59e0b" strokeDasharray="4 4" label={{ value: 'Baseline', fill: '#f59e0b', fontSize: 11 }} />
            <Line type="monotone" dataKey="delayed" stroke="#ef4444" strokeWidth={2.5} dot={{ fill: '#ef4444', r: 4 }} name="Delayed %" />
            <Line type="monotone" dataKey="baseline" stroke="#06b6d4" strokeWidth={1.5} strokeDasharray="5 3" dot={false} name="Baseline" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function VendorChart() {
  const top6 = vendorData.slice(0, 6).map(v => ({
    name: v.vendor.split(' ').slice(0, 2).join(' '),
    current: v.value,
    baseline: v.baseline,
  }))
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Vendor Delay Rates — Top 6 by volume</div>
      </div>
      <div className="card-body">
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={top6} margin={{ top: 4, right: 12, left: -20, bottom: 0 }}>
            <CartesianGrid stroke="rgba(99,147,220,0.08)" />
            <XAxis dataKey="name" tick={{ fill: '#7a97c0', fontSize: 10 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: '#7a97c0', fontSize: 11 }} axisLine={false} tickLine={false} tickFormatter={(v: number) => `${v}%`} />
            <Tooltip contentStyle={{ background: '#0d1526', border: '1px solid #1e3a5f', borderRadius: 8, fontSize: 12 }} />
            <Bar dataKey="current" fill="#ef4444" name="Current" radius={[3, 3, 0, 0]} />
            <Bar dataKey="baseline" fill="#06b6d4" name="Baseline" radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function SiteDonut() {
  const data = siteData.map(s => ({ name: s.site.split(' ').slice(0, 2).join(' '), value: s.share }))
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Site Delay Share</div>
      </div>
      <div className="card-body">
        <ResponsiveContainer width="100%" height={200}>
          <PieChart>
            <Pie data={data} cx="45%" cy="50%" innerRadius={55} outerRadius={80} dataKey="value" nameKey="name" paddingAngle={3}>
              {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
            </Pie>
            <Tooltip contentStyle={{ background: '#0d1526', border: '1px solid #1e3a5f', borderRadius: 8, fontSize: 12 }} />
            <Legend iconSize={10} wrapperStyle={{ fontSize: 11, color: '#7a97c0' }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function VendorDeltaBars() {
  const max = Math.max(...vendorData.map(v => v.delta))
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Vendor Delta vs Baseline (pp)</div>
      </div>
      <div className="card-body">
        {vendorData.map((v) => (
          <div key={v.vendor} className="delta-bar-row">
            <div className="delta-bar-label" title={v.vendor}>{v.vendor.split(' ').slice(0, 3).join(' ')}</div>
            <div className="delta-bar-track">
              <div className="delta-bar-fill" style={{
                width: `${(v.delta / max) * 100}%`,
                background: v.delta > 10 ? '#ef4444' : v.delta > 7 ? '#f59e0b' : '#06b6d4'
              }} />
            </div>
            <div className="delta-bar-val">+{v.delta.toFixed(1)}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function DashboardPage() {
  const { tenant } = useAppStore()
  const run = g1RunArtifact

  return (
    <div>
      <div className="hero-banner">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <div className="hero-badge">⚠ Material Anomaly · Evidence Verified</div>
            <div className="hero-headline">{run.headline}</div>
            <div className="hero-sub">
              {tenant} · {run.metric.periodStart} → {run.metric.periodEnd} ·
              Contract <code style={{ background: 'rgba(255,255,255,0.08)', padding: '1px 6px', borderRadius: 4, fontSize: '0.8em' }}>{run.metric.contractVersion}</code>
            </div>
          </div>
          <div className="hero-confidence">
            <div className="conf-val">{Math.round(run.evidence.confidence * 100)}%</div>
            <div className="conf-label">evidence confidence</div>
          </div>
        </div>
      </div>

      <MetricChips />
      <TrendChart />

      <div className="two-col" style={{ marginBottom: 16 }}>
        <VendorChart />
        <SiteDonut />
      </div>

      <VendorDeltaBars />

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginTop: 16 }}>
        <div className="card">
          <div className="card-header"><div className="card-title">Investigation Findings</div></div>
          <div className="card-body">
            {run.findings.map((f, i) => (
              <div key={i} style={{ display: 'flex', gap: 10, marginBottom: 12 }}>
                <div style={{ minWidth: 20, height: 20, borderRadius: '50%', background: 'var(--bg3)', border: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: 'var(--accent)', fontWeight: 800 }}>{i + 1}</div>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)', lineHeight: 1.6 }}>{f}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="card">
          <div className="card-header"><div className="card-title">Run Metadata</div></div>
          <div className="card-body">
            {([
              ['Run ID', run.runId.slice(0, 18) + '…'],
              ['Final Step', run.finalStep.replace(/_/g, ' ')],
              ['Tool Calls', `${run.toolCalls} / ${run.maxToolCalls}`],
              ['Correction Cycles', `${run.correctionCycles} / ${run.maxCorrectionCycles}`],
              ['Eligible Trips', run.metric.denominator.toLocaleString()],
              ['Status', run.status.replace(/_/g, ' ')],
            ] as [string, string][]).map(([k, v]) => (
              <div key={k} className="node-info-row" style={{ padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
                <span>{k}</span><span>{v}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {run.evidence.caveats.map((c, i) => (
        <div key={i} className="caveat-ribbon" style={{ marginTop: 12 }}>
          ⚠ {c}
        </div>
      ))}
    </div>
  )
}
