import { useState } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine,
  LineChart, Line, PieChart, Pie, Cell, Legend, CartesianGrid, LabelList
} from 'recharts'
import { vendorData, siteData, trendData, g1RunArtifact } from '../../core/mockData'
import { useAppStore } from '../../core/store'
import { DateRangePicker } from '../../shared/DateRangePicker'

const COLORS = ['#3FA535','#3C68D0','#10ADAE','#FF9D00','#C13D6D','#638FE7','#27D22E','#FF4444','#2E7D3E','#815FD5']

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
            <CartesianGrid stroke="rgba(0,0,0,0.06)" />
            <XAxis dataKey="week" tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} domain={[8, 24]} tickFormatter={(v: number) => `${v}%`} />
            <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} labelStyle={{ color: '#16211B' }} />
            <ReferenceLine y={12.28} stroke="#D4900C" strokeDasharray="4 4" label={{ value: 'Baseline', fill: '#D4900C', fontSize: 11 }} />
            <Line type="monotone" dataKey="delayed" stroke="#B00020" strokeWidth={2.5} dot={{ fill: '#B00020', r: 4 }} name="Delayed %" />
            <Line type="monotone" dataKey="baseline" stroke="#3FA535" strokeWidth={1.5} strokeDasharray="5 3" dot={false} name="Baseline" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function VendorChart() {
  const data = vendorData.map(v => ({
    name: v.vendor.replace(' Travel', ''),
    current: v.value,
    baseline: v.baseline,
  }))
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Vendor Delay Rates — Current vs Baseline</div>
        <div style={{ display: 'flex', gap: 14, fontSize: '0.72rem', color: 'var(--text-dim)' }}>
          <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: '#B00020', marginRight: 4 }} />Current</span>
          <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: '#3FA535', marginRight: 4 }} />Baseline</span>
        </div>
      </div>
      <div className="card-body" style={{ paddingRight: 8 }}>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart
            data={data}
            layout="vertical"
            margin={{ top: 0, right: 48, left: 0, bottom: 0 }}
            barCategoryGap="30%"
            barGap={3}
          >
            <CartesianGrid stroke="rgba(0,0,0,0.05)" horizontal={false} />
            <XAxis
              type="number"
              tick={{ fill: '#6B7A70', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v: number) => `${v}%`}
              domain={[0, 35]}
            />
            <YAxis
              type="category"
              dataKey="name"
              tick={{ fill: '#16211B', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              width={130}
            />
            <Tooltip
              contentStyle={{ background: '#fff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }}
              formatter={(v: number) => `${v.toFixed(1)}%`}
            />
            <Bar dataKey="current" fill="#B00020" name="Current" radius={[0, 3, 3, 0]} maxBarSize={10}>
              <LabelList dataKey="current" position="right" formatter={(v: number) => `${v.toFixed(1)}%`} style={{ fontSize: 10, fill: '#B00020', fontWeight: 600 }} />
            </Bar>
            <Bar dataKey="baseline" fill="#3FA535" name="Baseline" radius={[0, 3, 3, 0]} maxBarSize={10}>
              <LabelList dataKey="baseline" position="right" formatter={(v: number) => `${v.toFixed(1)}%`} style={{ fontSize: 10, fill: '#3FA535', fontWeight: 600 }} />
            </Bar>
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
        <ResponsiveContainer width="100%" height={280}>
          <PieChart>
            <Pie data={data} cx="45%" cy="50%" innerRadius={55} outerRadius={80} dataKey="value" nameKey="name" paddingAngle={3}>
              {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
            </Pie>
            <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} />
            <Legend iconSize={10} wrapperStyle={{ fontSize: 11, color: '#6B7A70' }} />
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
                background: v.delta > 10 ? '#B00020' : v.delta > 7 ? '#D4900C' : '#3FA535'
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
  const [dateRange, setDateRange] = useState({ from: run.metric.periodStart, to: run.metric.periodEnd })

  return (
    <div>
      {/* Filter bar */}
      <div className="filter-bar">
        <div className="filter-group">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ color: 'var(--mis-green)' }}>
            <path d="M2 3.5h10M4 7h6M6 10.5h2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
          </svg>
          <span className="filter-label">Tenant</span>
          <span className="filter-value">{tenant}</span>
        </div>
        <div className="filter-divider" />
        <DateRangePicker value={dateRange} onChange={setDateRange} />
        <button className="filter-apply-btn" type="button">Apply</button>
      </div>

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

      <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: 16, marginBottom: 16 }}>
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
