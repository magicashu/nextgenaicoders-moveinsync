import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import App from '../../App'
import { ApiProvider } from '../../app/ApiContext'
import { mockApi, resetMockState } from '../../mocks/mockApi'
import { g1Brief } from '../../mocks/fixtures'

function renderApp() {
  return render(
    <ApiProvider api={mockApi}>
      <App />
    </ApiProvider>,
  )
}

describe('Mobility Decision Copilot judge flow (typed fixtures)', () => {
  beforeEach(() => resetMockState())

  it('renders the G1 morning brief with benchmark, impact, findings and a pending approval', async () => {
    renderApp()
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })).toBeInTheDocument()
    expect(screen.getByText('84%')).toBeInTheDocument()
    expect(screen.getByText(/Misses ≤ 10% · Configured target, editable per tenant/)).toBeInTheDocument()
    expect(screen.getByText('1,912')).toBeInTheDocument()
    expect(screen.getAllByText(/not attributable to a single vendor/).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /review and approve/i })).toBeEnabled()
    expect(screen.getByText(/facts cannot diverge/i)).toBeInTheDocument()
  })

  it('opens governed evidence metadata from a KPI', async () => {
    renderApp()
    await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })
    fireEvent.click(screen.getByRole('button', { name: /Delayed-trip rate evidence/i }))
    const drawer = await screen.findByRole('dialog', { name: /evidence details/i })
    expect(within(drawer).getByText('pinnacle-Slc:m01_delayed_trip_rate:2026-06-07')).toBeInTheDocument()
    expect(within(drawer).getByText(/4,357 of 19,913/)).toBeInTheDocument()
    expect(within(drawer).getByText('metrics-v1.1')).toBeInTheDocument()
    expect(within(drawer).getByText(/trips with delay_minutes > 0/)).toBeInTheDocument()
    expect(within(drawer).getByText('sql/metrics/m01_delayed_trip_rate.sql')).toBeInTheDocument()
  })

  it('shows approval scope, evidence timestamp and consequence, then an executed receipt after approval', async () => {
    renderApp()
    await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })
    fireEvent.click(screen.getByRole('button', { name: /review and approve/i }))
    expect(await screen.findByText(/Creates a mock watchlist entry/)).toBeInTheDocument()
    expect(screen.getByText('evidence-3f2a9c1b7d44')).toBeInTheDocument()
    expect(screen.getAllByText(/2026-06-08 08:00:00Z/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: /^Approve$/ }))
    expect(await screen.findByText(/Executed once, audited/)).toBeInTheDocument()
    expect(screen.getByText('WATCH-7f3a')).toBeInTheDocument()
    expect(screen.getByText(`${g1Brief.runId}:${g1Brief.operations.recommendedAction.actionId}`)).toBeInTheDocument()
  })

  it('reject never shows an execution and audit reflects the decision', async () => {
    renderApp()
    await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })
    fireEvent.click(screen.getByRole('button', { name: /review and approve/i }))
    fireEvent.click(await screen.findByRole('button', { name: /^Reject$/ }))
    expect(await screen.findByText(/Rejected — nothing executed/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Audit' }))
    expect(await screen.findByText(/approval reject/i)).toBeInTheDocument()
    expect(screen.queryByText(/action executed/i)).not.toBeInTheDocument()
    expect(screen.getByText(`trace-${g1Brief.runId}`)).toBeInTheDocument()
  })

  it('refuses out-of-scope questions and links in-scope answers to evidence', async () => {
    renderApp()
    await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })
    fireEvent.click(screen.getByRole('button', { name: /ask about this/i }))
    const drawer = await screen.findByRole('dialog', { name: /ask about this brief/i })
    fireEvent.change(within(drawer).getByRole('textbox'), { target: { value: 'Compare us with orbit-Slc' } })
    fireEvent.click(within(drawer).getByRole('button', { name: /^Ask$/ }))
    expect((await within(drawer).findAllByText(/Cross-tenant comparison is not available/)).length).toBeGreaterThan(0)
    fireEvent.click(within(drawer).getByRole('button', { name: /Did every high-volume vendor deteriorate/ }))
    await waitFor(() => expect(within(drawer).getByText(/intent vendor/i)).toBeInTheDocument())
    expect(within(drawer).getAllByRole('button', { name: /M01/ }).length).toBeGreaterThan(0)
  })

  it('renders healthy, degraded (G2) and unavailable states from the same contracts', async () => {
    renderApp()
    await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })
    fireEvent.change(screen.getByLabelText('Tenant'), { target: { value: 'catalyst-Sac' } })
    expect(await screen.findByRole('heading', { level: 2, name: /no material operational anomaly/i })).toBeInTheDocument()
    expect(screen.getByText(/No approval request is raised for a healthy tenant/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Tenant'), { target: { value: 'vanta-Aus' } })
    expect(await screen.findByRole('heading', { level: 2, name: /vanta-Aus: delayed-trip rate reached 7.19%/ })).toBeInTheDocument()
    expect(screen.getByText('Unsupported')).toBeInTheDocument()
    expect(screen.getAllByText(/Low feedback coverage: 3.9% of trips rated/).length).toBeGreaterThan(0)
    fireEvent.change(screen.getByLabelText('Tenant'), { target: { value: 'vanta-Sea' } })
    expect(await screen.findByText(/analytical plane is unavailable. No number was fabricated/i)).toBeInTheDocument()
  })

  it('exposes versions, latency, model use and the node trace in the trust panel', async () => {
    renderApp()
    await screen.findByRole('heading', { level: 2, name: /delayed-trip rate reached 21.88%/i })
    fireEvent.click(screen.getByRole('button', { name: 'Trust' }))
    expect(await screen.findByText('data-8ed5b4eae158')).toBeInTheDocument()
    expect(screen.getByText(/none \(deterministic roles\)/)).toBeInTheDocument()
    expect(screen.getByText(/7 governed analytical calls/)).toBeInTheDocument()
    expect(screen.getByText('approval_interrupt')).toBeInTheDocument()
  })
})
