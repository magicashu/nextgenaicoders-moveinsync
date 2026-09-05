import { useState, useEffect } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine,
  PieChart, Pie, Cell, Legend, CartesianGrid, LabelList, Area, AreaChart
} from 'recharts'
import {
  startInvestigation, fetchCharts, dashboardView, rangeFor,
  type DashboardData, type VendorRow, type SiteRow, type TrendPoint,
} from '../../core/dashboardApi'
import { useAppStore } from '../../core/store'
import { DateRangePicker } from '../../shared/DateRangePicker'

const SITE_COLORS = ['#3C68D0','#3FA535','#10ADAE','#FF9D00','#C13D6D','#638FE7','#815FD5','#2E7D3E','#FF6B35','#1E5F8E']

// ── Evidence Confidence Score ─────────────────────────────────────────────────────

function evidenceConfidence(confidence: number) {
  return { score: Math.round(confidence * 100), label: 'Evidence confidence', color: '#3FA535' }
}

// ── Components ──────────────────────────────────────────────────────────────

function AnomalyAlerts({ data }: { data: DashboardData }) {
  if (data.status === 'HEALTHY') return <div className="caveat-ribbon">No escalation required by the workflow for this window.</div>
  return <div style={{ marginBottom: 16, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
    {data.findings.slice(0, 3).map((finding, index) => <div key={index} style={{
      flex: 1, minWidth: 220, background: 'linear-gradient(135deg, #FFF0F0 0%, #FFF8F0 100%)',
      border: '1px solid rgba(176,0,32,0.25)', borderLeft: '3px solid #B00020', borderRadius: 10,
      padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 14,
    }}><span style={{ color: '#B00020', fontSize: '1.1rem' }}>⚠</span>
      <div style={{ fontSize: '0.78rem', lineHeight: 1.6 }}>{finding}</div></div>)}
  </div>
}

function FleetHealthGauge({ score, label, color, delayRate, totalTrips }: { score: number; label: string; color: string; delayRate: number; totalTrips: number }) {
  const circumference = 2 * Math.PI * 42
  const dashOffset = circumference * (1 - score / 100)
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
      <div style={{ position: 'relative', width: 100, height: 100, flexShrink: 0 }}>
        <svg width="100" height="100" style={{ transform: 'rotate(-90deg)' }}>
          <circle cx="50" cy="50" r="42" fill="none" stroke="#E0E0E0" strokeWidth="8" />
          <circle cx="50" cy="50" r="42" fill="none" stroke={color} strokeWidth="8"
            strokeDasharray={circumference} strokeDashoffset={dashOffset}
            strokeLinecap="round" style={{ transition: 'stroke-dashoffset 1s ease' }} />
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, fontFamily: 'IBM Plex Mono, monospace', color, lineHeight: 1 }}>{score}</div>
          <div style={{ fontSize: '0.58rem', color: '#6B7A70', textTransform: 'uppercase', letterSpacing: 1 }}>confidence %</div>
        </div>
      </div>
      <div>
        <div style={{ fontSize: '1.1rem', fontWeight: 800, color, marginBottom: 4 }}>{label}</div>
        <div style={{ fontSize: '0.8rem', color: '#6B7A70', lineHeight: 1.7 }}>
          <div><span style={{ fontFamily: 'IBM Plex Mono, monospace', fontWeight: 700, color: '#16211B' }}>{delayRate.toFixed(1)}%</span> delayed trip rate</div>
          <div><span style={{ fontFamily: 'IBM Plex Mono, monospace', fontWeight: 700, color: '#16211B' }}>{totalTrips.toLocaleString()}</span> trips analysed</div>
        </div>
      </div>
    </div>
  )
}

function KpiStrip({ data, trend }: { data: DashboardData; trend: TrendPoint[] }) {
  const m = data.metric
  const trendDir = m.delta
  const trendUp = trendDir != null && trendDir > 0
  const trendDown = trendDir != null && trendDir < 0

  const miniTrendData = trend.slice(-5).map((p, i) => ({ i, v: p.delayed }))

  const health = evidenceConfidence(data.evidence.confidence)

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
      {/* Delay rate */}
      <div style={{ background: '#fff', border: '1px solid #E0E0E0', borderTop: '3px solid #B00020', borderRadius: 10, padding: '16px 18px', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
        <div style={{ fontSize: '0.68rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6B7A70', marginBottom: 8 }}>Delayed Trip Rate</div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, marginBottom: 4 }}>
          <div style={{ fontSize: '2rem', fontWeight: 800, fontFamily: 'IBM Plex Mono, monospace', color: '#B00020', lineHeight: 1 }}>{m.value.toFixed(1)}%</div>
          {(trendUp || trendDown) && (
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: trendUp ? '#B00020' : '#3FA535', marginBottom: 4 }}>
              {trendUp ? '▲' : '▼'} {Math.abs(trendDir!).toFixed(1)}pp vs prior 4 weeks
            </div>
          )}
        </div>
        <div style={{ fontSize: '0.71rem', color: '#A8B2AB' }}>{m.numerator.toLocaleString()} of {m.denominator.toLocaleString()} trips</div>
        {miniTrendData.length > 1 && (
          <div style={{ marginTop: 10 }}>
            <ResponsiveContainer width="100%" height={32}>
              <AreaChart data={miniTrendData} margin={{ top: 2, right: 0, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="redGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#B00020" stopOpacity={0.15} />
                    <stop offset="95%" stopColor="#B00020" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <Area type="monotone" dataKey="v" stroke="#B00020" strokeWidth={1.5} fill="url(#redGrad)" dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* On-time rate */}
      <div style={{ background: '#fff', border: '1px solid #E0E0E0', borderTop: '3px solid #3FA535', borderRadius: 10, padding: '16px 18px', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
        <div style={{ fontSize: '0.68rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6B7A70', marginBottom: 8 }}>On-Time Pickup Rate · M04</div>
        <div style={{ fontSize: '2rem', fontWeight: 800, fontFamily: 'IBM Plex Mono, monospace', color: '#2E7D3E', lineHeight: 1, marginBottom: 4 }}>{data.onTime.value == null ? '—' : `${data.onTime.value.toFixed(1)}%`}</div>
        <div style={{ fontSize: '0.71rem', color: '#A8B2AB' }}>{data.onTime.numerator?.toLocaleString() ?? '—'} / {data.onTime.denominator?.toLocaleString() ?? '—'} eligible pickups</div>
        <div style={{ marginTop: 10, fontSize: '0.71rem', color: '#6B7A70' }}>{data.onTime.caveats.join(' ')}</div>
      </div>

      {/* Fleet health score */}
      <div style={{ background: '#fff', border: '1px solid #E0E0E0', borderTop: `3px solid ${health.color}`, borderRadius: 10, padding: '16px 18px', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
        <div style={{ fontSize: '0.68rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6B7A70', marginBottom: 8 }}>Evidence Confidence</div>
        <FleetHealthGauge score={health.score} label={health.label} color={health.color} delayRate={m.value} totalTrips={m.denominator} />
      </div>

      {/* Period + evidence */}
      <div style={{ background: '#fff', border: '1px solid #E0E0E0', borderTop: '3px solid #3C68D0', borderRadius: 10, padding: '16px 18px', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
        <div style={{ fontSize: '0.68rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6B7A70', marginBottom: 8 }}>Analysis</div>
        <div style={{ fontSize: '0.82rem', fontWeight: 700, color: '#16211B', marginBottom: 4 }}>{m.periodStart} → {m.periodEnd}</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
            <span style={{ color: '#6B7A70' }}>Evidence items</span>
            <span style={{ fontWeight: 700, fontFamily: 'IBM Plex Mono, monospace' }}>{data.evidenceCount}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
            <span style={{ color: '#6B7A70' }}>Confidence</span>
            <span style={{ fontWeight: 700, color: '#3C68D0' }}>{Math.round(data.evidence.confidence * 100)}%</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
            <span style={{ color: '#6B7A70' }}>Contract</span>
            <span style={{ fontWeight: 700, fontFamily: 'IBM Plex Mono, monospace', fontSize: '0.7rem' }}>{m.contractVersion}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

function TrendChart({ data }: { data: TrendPoint[] }) {
  const baseline = data.find(p => p.baseline != null)?.baseline ?? null
  const max = Math.max(...data.map(p => p.delayed ?? 0), baseline ?? 0)
  const first = data.find(p => p.delayed != null)?.delayed
  const last = [...data].reverse().find(p => p.delayed != null)?.delayed
  const isWorsening = first != null && last != null && last > first
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div className="card-title">Delayed-Trip Rate — Daily Trend</div>
          {first != null && last != null && first !== last ? isWorsening
            ? <span style={{ fontSize: '0.7rem', padding: '2px 8px', borderRadius: 20, background: 'rgba(176,0,32,0.1)', color: '#B00020', fontWeight: 700 }}>▲ Higher than first day</span>
            : <span style={{ fontSize: '0.7rem', padding: '2px 8px', borderRadius: 20, background: 'rgba(63,165,53,0.1)', color: '#2E7D3E', fontWeight: 700 }}>▼ Lower than first day</span>
            : <span style={{ fontSize: '0.7rem', color: '#6B7A70' }}>Recorded daily values</span>
          }
        </div>
        {baseline != null && <div style={{ fontSize: '0.75rem', color: '#6B7A70' }}>Prior four weeks: <strong>{baseline.toFixed(1)}%</strong></div>}
      </div>
      <div className="card-body" style={{ paddingTop: 12 }}>
        <ResponsiveContainer width="100%" height={200}>
          <AreaChart data={data} margin={{ top: 8, right: 16, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="trendGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#B00020" stopOpacity={0.12} />
                <stop offset="95%" stopColor="#B00020" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="rgba(0,0,0,0.05)" vertical={false} />
            <XAxis dataKey="week" tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: '#6B7A70', fontSize: 11 }} axisLine={false} tickLine={false}
              tickFormatter={(v: number) => `${v.toFixed(0)}%`}
              domain={[0, Math.ceil(max * 1.2)]} />
            <Tooltip
              contentStyle={{ background: '#fff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12, boxShadow: '0 4px 16px rgba(0,0,0,0.12)' }}
              labelStyle={{ color: '#16211B', fontWeight: 700 }}
              formatter={(v) => [`${Number(v).toFixed(1)}%`, 'Delayed rate']} />
            {baseline != null && (
              <ReferenceLine y={baseline} stroke="#D4900C" strokeDasharray="5 3"
                label={{ value: `Baseline ${baseline.toFixed(1)}%`, fill: '#D4900C', fontSize: 11, position: 'insideTopRight' }} />
            )}
            <Area type="monotone" dataKey="delayed" stroke="#B00020" strokeWidth={2.5}
              fill="url(#trendGrad)" dot={{ fill: '#B00020', r: 4, strokeWidth: 2, stroke: '#fff' }}
              activeDot={{ r: 6, stroke: '#B00020', strokeWidth: 2 }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function VendorChart({ data }: { data: VendorRow[] }) {
  const chartData = data.slice(0, 12).map(v => ({
    name: v.vendor.replace(' Travel', '').slice(0, 20),
    current: parseFloat(v.value.toFixed(1)),
    baseline: v.baseline == null ? null : parseFloat(v.baseline.toFixed(1)),
    isHigh: (v.delta ?? 0) > 0,
  }))
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Vendor Delay Rates vs Prior Four Weeks</div>
        <div style={{ display: 'flex', gap: 14, fontSize: '0.72rem', color: '#6B7A70' }}>
          <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: '#B00020', marginRight: 4 }} />Current</span>
          <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: '#3FA535', marginRight: 4 }} />Prior 4 weeks</span>
        </div>
      </div>
      <div className="card-body" style={{ paddingRight: 4 }}>
        <ResponsiveContainer width="100%" height={Math.max(260, chartData.length * 26)}>
          <BarChart data={chartData} layout="vertical" margin={{ top: 0, right: 56, left: 0, bottom: 0 }} barCategoryGap="28%" barGap={3}>
            <CartesianGrid stroke="rgba(0,0,0,0.04)" horizontal={false} />
            <XAxis type="number" tick={{ fill: '#6B7A70', fontSize: 10 }} axisLine={false} tickLine={false} tickFormatter={(v: number) => `${v}%`} />
            <YAxis type="category" dataKey="name" tick={{ fill: '#16211B', fontSize: 10 }} axisLine={false} tickLine={false} width={130} />
            <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12 }} formatter={(v) => [`${Number(v).toFixed(1)}%`]} />
            <Bar dataKey="current" name="Current" radius={[0, 3, 3, 0]} maxBarSize={9}>
              {chartData.map((entry, i) => (
                <Cell key={i} fill={entry.isHigh ? '#B00020' : '#E57373'} />
              ))}
              <LabelList dataKey="current" position="right" formatter={(v: unknown) => `${Number(v).toFixed(1)}%`} style={{ fontSize: 9, fontWeight: 700 }} />
            </Bar>
            <Bar dataKey="baseline" fill="#3FA535" name="Prior 4 weeks" radius={[0, 3, 3, 0]} maxBarSize={9}>
              <LabelList dataKey="baseline" position="right" formatter={(v: unknown) => `${Number(v).toFixed(1)}%`} style={{ fontSize: 9, fill: '#3FA535', fontWeight: 600 }} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function SiteDonut({ data }: { data: SiteRow[] }) {
  const sorted = [...data].sort((a, b) => b.trips - a.trips).slice(0, 8)
  const total = sorted.reduce((s, r) => s + r.trips, 0)
  const chartData = sorted.map(s => ({
    name: s.site.replace(' Campus', '').replace(' Office', '').replace(' Commons', ''),
    value: total > 0 ? parseFloat(((s.trips / total) * 100).toFixed(1)) : 0,
    delayRate: s.value,
    trips: s.trips,
  }))
  return (
    <div className="card">
      <div className="card-header"><div className="card-title">Trip Volume — Top 8 Sites</div></div>
      <div className="card-body">
        <ResponsiveContainer width="100%" height={260}>
          <PieChart>
            <Pie data={chartData} cx="45%" cy="50%" innerRadius={50} outerRadius={82} dataKey="value" nameKey="name" paddingAngle={2}>
              {chartData.map((_, i) => <Cell key={i} fill={SITE_COLORS[i % SITE_COLORS.length]} />)}
            </Pie>
            <Tooltip
              contentStyle={{ background: '#fff', border: '1px solid #E0E0E0', borderRadius: 8, fontSize: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}
              formatter={(v, _n, props) => [`${Number(v).toFixed(1)}% of displayed trips · ${props.payload?.delayRate?.toFixed(1)}% delayed`]}
            />
            <Legend iconSize={9} wrapperStyle={{ fontSize: 10, color: '#6B7A70', lineHeight: 1.8 }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function VendorDeltaBars({ data }: { data: VendorRow[] }) {
  const top = data.filter((v): v is VendorRow & { delta: number } => v.delta != null).slice(0, 10)
  const max = Math.max(...top.map(v => Math.abs(v.delta)), 1)
  const getColor = (delta: number) => delta > 0 ? '#B00020' : '#3FA535'
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Vendor Performance Delta vs Baseline (pp)</div>
        <div style={{ fontSize: '0.72rem', color: '#6B7A70' }}>+ = higher than baseline</div>
      </div>
      <div className="card-body">
        {top.map((v) => {
          const color = getColor(v.delta)
          return (
            <div key={v.vendor} style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 9 }}>
              <div style={{ fontSize: '0.72rem', color: '#6B7A70', minWidth: 150, maxWidth: 150, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={v.vendor}>
                {v.vendor.replace(' Travel', '')}
              </div>
              <div style={{ flex: 1, height: 7, background: '#F0F3FA', borderRadius: 4, overflow: 'hidden' }}>
                <div style={{ width: `${(Math.abs(v.delta) / max) * 100}%`, height: '100%', borderRadius: 4, background: color, transition: 'width 0.8s cubic-bezier(0.4,0,0.2,1)' }} />
              </div>
              <div style={{ fontSize: '0.73rem', fontFamily: 'IBM Plex Mono, monospace', fontWeight: 700, color, minWidth: 52, textAlign: 'right' }}>
                {v.delta >= 0 ? '+' : ''}{v.delta.toFixed(1)}pp
              </div>
              <div style={{ fontSize: '0.68rem', color: '#A8B2AB', minWidth: 60, textAlign: 'right' }}>
                {v.trips.toLocaleString()} trips
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function SiteDelayTable({ data }: { data: SiteRow[] }) {
  const sorted = [...data].sort((a, b) => b.value - a.value)
  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Site Delay Rates</div>
        <div style={{ fontSize: '0.72rem', color: '#6B7A70' }}>sorted by delay rate ↓</div>
      </div>
      <div className="card-body" style={{ padding: 0 }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Site</th>
              <th style={{ textAlign: 'right' }}>Rate</th>
              <th style={{ textAlign: 'right' }}>Trips</th>
              <th style={{ textAlign: 'right' }}>vs Baseline</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((s) => {
              const sevColor = (s.delta ?? 0) > 0 ? '#B00020' : '#2E7D3E'
              return (
                <tr key={s.site}>
                  <td style={{ fontWeight: 500 }}>{s.site}</td>
                  <td style={{ textAlign: 'right', fontWeight: 800, fontFamily: 'IBM Plex Mono, monospace', color: sevColor }}>
                    {s.value.toFixed(1)}%
                  </td>
                  <td style={{ textAlign: 'right', color: '#6B7A70', fontFamily: 'IBM Plex Mono, monospace', fontSize: '0.78rem' }}>
                    {s.trips.toLocaleString()}
                  </td>
                  <td style={{ textAlign: 'right', fontWeight: 700, fontFamily: 'IBM Plex Mono, monospace', fontSize: '0.8rem', color: (s.delta ?? 0) > 0 ? '#B00020' : '#2E7D3E' }}>
                    {s.delta != null && s.delta >= 0 ? '+' : ''}{s.delta?.toFixed(1) ?? '—'}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function AgentInsightPanel({ data }: { data: DashboardData }) {
  const health = evidenceConfidence(data.evidence.confidence)
  return (
    <div className="card" style={{ gridColumn: 'span 2' }}>
      <div className="card-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div className="card-title">AI Agent Analysis</div>
          <span style={{ fontSize: '0.68rem', padding: '2px 8px', borderRadius: 20, background: 'rgba(63,165,53,0.1)', color: '#2E7D3E', fontWeight: 700, marginLeft: 8 }}>
            4-agent pipeline
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8, fontSize: '0.72rem' }}>
          <span style={{ padding: '2px 8px', borderRadius: 20, background: 'rgba(60,104,208,0.1)', color: '#3C68D0', fontWeight: 700 }}>supervisor</span>
          <span style={{ padding: '2px 8px', borderRadius: 20, background: 'rgba(16,173,174,0.1)', color: '#10ADAE', fontWeight: 700 }}>investigator</span>
          <span style={{ padding: '2px 8px', borderRadius: 20, background: 'rgba(212,144,12,0.1)', color: '#D4900C', fontWeight: 700 }}>critic</span>
          <span style={{ padding: '2px 8px', borderRadius: 20, background: 'rgba(63,165,53,0.1)', color: '#2E7D3E', fontWeight: 700 }}>briefing</span>
        </div>
      </div>
      <div className="card-body">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div>
            {data.findings.length > 0 ? (
              <>
                <div style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, color: '#6B7A70', marginBottom: 10 }}>Verified Findings</div>
                {data.findings.map((f, i) => (
                  <div key={i} style={{ display: 'flex', gap: 10, marginBottom: 10 }}>
                    <div style={{ minWidth: 20, height: 20, borderRadius: '50%', background: 'rgba(63,165,53,0.12)', border: '1px solid rgba(63,165,53,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: '#3FA535', fontWeight: 800, flexShrink: 0 }}>{i + 1}</div>
                    <div style={{ fontSize: '0.84rem', color: '#16211B', lineHeight: 1.6 }}>{f}</div>
                  </div>
                ))}
              </>
            ) : (
              <>
                <div style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, color: '#6B7A70', marginBottom: 10 }}>Agent Summary</div>
                <p style={{ fontSize: '0.85rem', color: '#6B7A70', lineHeight: 1.7, marginBottom: 10 }}>
                  {data.operationalSummary || 'Investigation pipeline completed.'}
                </p>
                <p style={{ fontSize: '0.78rem', color: '#A8B2AB', lineHeight: 1.6 }}>
                  Open the workflow and trust record to inspect the evidence checks, model calls and recorded node decisions.
                </p>
              </>
            )}
          </div>
          <div>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, color: '#6B7A70', marginBottom: 10 }}>Pipeline Summary</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {[
                { label: 'Evidence Confidence', value: `${health.score}/100`, color: health.color },
                { label: 'Evidence items', value: String(data.evidenceCount), color: '#16211B' },
                { label: 'Coverage', value: data.metric.denominator.toLocaleString(), color: '#16211B' },
                { label: 'Contract', value: data.metric.contractVersion, color: '#3C68D0' },
                { label: 'Status', value: data.status.replace(/_/g, ' '), color: '#16211B' },
                { label: 'Confidence', value: `${Math.round(data.evidence.confidence * 100)}%`, color: '#3FA535' },
              ].map(({ label, value, color }) => (
                <div key={label} style={{ padding: '8px 10px', background: '#FAFAFA', borderRadius: 8, border: '1px solid #E0E0E0' }}>
                  <div style={{ fontSize: '0.65rem', color: '#6B7A70', textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 2 }}>{label}</div>
                  <div style={{ fontSize: '0.83rem', fontWeight: 700, color, fontFamily: 'IBM Plex Mono, monospace' }}>{value}</div>
                </div>
              ))}
            </div>
            {data.recommendedAction && (
              <div style={{ marginTop: 14, padding: '12px 14px', borderRadius: 10, background: 'rgba(63,165,53,0.07)', border: '1px solid rgba(63,165,53,0.25)' }}>
                <div style={{ fontSize: '0.68rem', fontWeight: 800, color: '#3FA535', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 }}>Recommended Action</div>
                <div style={{ fontSize: '0.84rem', fontWeight: 700, color: '#16211B', marginBottom: 3 }}>{data.recommendedAction.title}</div>
                <div style={{ fontSize: '0.78rem', color: '#6B7A70' }}>{data.recommendedAction.rationale}</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

function LoadingOverlay() {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(245,245,245,0.92)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', zIndex: 100, gap: 16 }}>
      <div style={{ width: 44, height: 44, border: '3px solid #E0E0E0', borderTopColor: '#3FA535', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
      <div style={{ fontSize: '0.92rem', fontWeight: 700, color: '#16211B' }}>Running 4-agent pipeline…</div>
      <div style={{ fontSize: '0.78rem', color: '#6B7A70', textAlign: 'center', maxWidth: 320 }}>
        Supervisor → Investigator → Critic → Briefing<br />
        Waiting for the backend workflow and governed charts
      </div>
    </div>
  )
}

interface AllData { dashboard: DashboardData; vendors: VendorRow[]; sites: SiteRow[]; trend: TrendPoint[] }

export function DashboardPage() {
  const { tenant, run, busy } = useAppStore()
  const [dateRange, setDateRange] = useState(run ? rangeFor(run.asOfDate) : { from: '2026-06-01', to: '2026-06-07' })
  const [allData, setAllData] = useState<AllData | null>(null)
  const [chartLoading, setChartLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const loading = busy || chartLoading

  const loadAll = async () => {
    setError(null)
    try { await startInvestigation(tenant, dateRange.from, dateRange.to) }
    catch (e) { setError(e instanceof Error ? e.message : 'Investigation failed') }
  }
  useEffect(() => {
    let active = true
    setAllData(null)
    if (!run) return
    setDateRange(rangeFor(run.asOfDate))
    setChartLoading(true)
    fetchCharts(tenant, run.asOfDate).then(charts => {
      if (active) setAllData(dashboardView(run, charts))
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Charts unavailable') })
      .finally(() => { if (active) setChartLoading(false) })
    return () => { active = false }
  }, [tenant, run])

  const d = allData?.dashboard

  return (
    <div>
      {loading && <LoadingOverlay />}

      <div className="filter-bar">
        <div className="filter-group">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ color: '#3FA535' }}>
            <path d="M2 3.5h10M4 7h6M6 10.5h2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
          </svg>
          <span className="filter-label">Tenant</span>
          <span className="filter-value">{tenant}</span>
        </div>
        <div className="filter-divider" />
        <DateRangePicker value={dateRange} onChange={setDateRange} />
        <button className="filter-apply-btn" type="button" onClick={loadAll} disabled={loading}>
          {loading ? 'Running…' : 'Analyse'}
        </button>
        {d && <span style={{ marginLeft: 'auto', fontSize: '0.72rem', color: '#3FA535', fontWeight: 700 }}>● DATASET RESULT</span>}
      </div>

      <p className="page-subtitle">Select seven days; comparisons use the preceding four weeks. Results below retain their recorded dates.</p>
      {error && (
        <div className="caveat-ribbon" style={{ background: '#FFF0F0', borderColor: '#FFCCCC', color: '#B00020', marginBottom: 12 }}>⚠ {error}</div>
      )}

      {!d && !loading && (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 420, gap: 16 }}>
          <div style={{ width: 72, height: 72, borderRadius: '50%', background: '#F0F3FA', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
              <path d="M16 4 L28 28 H4 Z" stroke="#C8CDD3" strokeWidth="2" fill="none" strokeLinejoin="round"/>
              <line x1="16" y1="13" x2="16" y2="20" stroke="#C8CDD3" strokeWidth="2" strokeLinecap="round"/>
              <circle cx="16" cy="23" r="1.2" fill="#C8CDD3"/>
            </svg>
          </div>
          <div style={{ fontSize: '1rem', fontWeight: 700, color: '#16211B' }}>Ready to analyse</div>
          <div style={{ fontSize: '0.85rem', textAlign: 'center', maxWidth: 380, color: '#6B7A70', lineHeight: 1.7 }}>
            Select a tenant and seven-day window, then click <strong style={{ color: '#16211B' }}>Analyse</strong> to run the 4-agent pipeline.<br />
            Supervisor · Investigator · Critic · Briefing
          </div>
          <div style={{ fontSize: '0.78rem', color: '#A8B2AB', textAlign: 'center', maxWidth: 400 }}>
            Dataset: May–July 2026 · Tenants: pinnacle-Slc · vanta-Sea · vanta-Aus · catalyst-Sac · orbit-Slc
          </div>
        </div>
      )}

      {d && (
        <>
          {/* Hero */}
          <div style={{
            background: 'linear-gradient(135deg, #fff 0%, #F8FFF8 100%)',
            border: '1px solid #E0E0E0', borderLeft: '4px solid #3FA535',
            borderRadius: 12, padding: '20px 24px', marginBottom: 16,
            boxShadow: '0 2px 12px rgba(0,0,0,0.07)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
                  <span style={{ padding: '3px 10px', borderRadius: 20, background: '#FEF3C7', border: '1px solid #F59E0B', color: '#92400E', fontSize: '0.71rem', fontWeight: 700 }}>
                    M01 · Delayed Trip Rate
                  </span>
                  <span style={{ padding: '3px 10px', borderRadius: 20, background: 'rgba(60,104,208,0.1)', border: '1px solid rgba(60,104,208,0.3)', color: '#3C68D0', fontSize: '0.71rem', fontWeight: 700 }}>
                    4-Agent Pipeline
                  </span>
                </div>
                <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#16211B', marginBottom: 6, lineHeight: 1.3 }}>
                  {d.headline}
                </div>
                <div style={{ fontSize: '0.8rem', color: '#6B7A70' }}>
                  {tenant} · {d.metric.periodStart} → {d.metric.periodEnd} · {d.metric.denominator.toLocaleString()} eligible trips
                </div>
              </div>
              <div style={{ textAlign: 'center', background: 'rgba(176,0,32,0.08)', border: `1px solid ${'rgba(176,0,32,0.25)'}`, borderRadius: 12, padding: '16px 24px', minWidth: 120, flexShrink: 0, marginLeft: 20 }}>
                <div style={{ fontSize: '2.2rem', fontWeight: 800, fontFamily: 'IBM Plex Mono, monospace', color: '#B00020', lineHeight: 1 }}>
                  {d.metric.value.toFixed(1)}%
                </div>
                <div style={{ fontSize: '0.65rem', color: '#6B7A70', marginTop: 4, textTransform: 'uppercase', letterSpacing: 1 }}>delayed</div>
              </div>
            </div>
          </div>

          {/* Findings from the actual workflow */}
          <AnomalyAlerts data={d} />

          {/* KPI strip */}
          <KpiStrip data={d} trend={allData!.trend} />

          {/* Trend chart */}
          {allData!.trend.length > 0 && <TrendChart data={allData!.trend} />}

          {/* Vendor + Site side by side */}
          {(allData!.vendors.length > 0 || allData!.sites.length > 0) && (
            <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: 16, marginBottom: 16 }}>
              {allData!.vendors.length > 0 && <VendorChart data={allData!.vendors} />}
              {allData!.sites.length > 0 && <SiteDonut data={allData!.sites} />}
            </div>
          )}

          {/* Delta bars + Site table */}
          {(allData!.vendors.length > 0 || allData!.sites.length > 0) && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
              {allData!.vendors.length > 0 && <VendorDeltaBars data={allData!.vendors} />}
              {allData!.sites.length > 0 && <SiteDelayTable data={allData!.sites} />}
            </div>
          )}

          {/* Agent analysis full-width */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 16, marginBottom: 8 }}>
            <AgentInsightPanel data={d} />
          </div>

          {d.evidence.caveats.map((c, i) => (
            <div key={i} className="caveat-ribbon" style={{ marginTop: 8 }}>⚠ {c}</div>
          ))}
        </>
      )}
    </div>
  )
}
