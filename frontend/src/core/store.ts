import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { MorningBriefResponse } from './contracts'
import type { Charts } from './dashboardTypes'
import { TENANTS } from './identity'
import type { ManagerRole } from './presentation'

export type Tenant = typeof TENANTS[number]
type Capture = { run: MorningBriefResponse; capturedAt: number }
type State = {
  tenant: Tenant
  role: ManagerRole
  personaChosen: boolean
  choosePersona: (role: ManagerRole, tenant: Tenant) => void
  changePersona: () => void
  setRole: (role: ManagerRole) => void
  asOf: string
  run: MorningBriefResponse | null
  captures: Record<string, Capture>
  charts: Record<string, Charts>
  epoch: number
  busy: boolean
  error: string | null
  lastRefresh: number
  setTenant: (tenant: Tenant) => void
  setRun: (run: MorningBriefResponse, epoch: number) => void
  updateCapture: (run: MorningBriefResponse, epoch: number) => void
  capture: (run: MorningBriefResponse, epoch: number) => void
  selectCapture: (key: string) => MorningBriefResponse | null
  saveCharts: (key: string, charts: Charts) => void
  refresh: () => Promise<void>
  activeNode: string | null
  setActiveNode: (node: string | null) => void
  timelineStep: number
  setTimelineStep: (step: number) => void
  isLive: boolean
  setLive: (live: boolean) => void
}
export const identityFor = (businessUnit: string, role: ManagerRole = useAppStore.getState().role) => ({ actorId: role.toLowerCase().replaceAll('_', '-') + '-demo', businessUnit, roles: [role] })
export const captureKey = (tenant: string, asOf: string, role: string = useAppStore.getState().role) => JSON.stringify([tenant, asOf, role])
export const chartKey = (tenant: string, asOf: string, dataVersion: string) => JSON.stringify([tenant, asOf, dataVersion])
function bounded<T>(values: Record<string, T>, key: string, value: T) {
  const entries = Object.entries(values).filter(([existing]) => existing !== key).slice(-7)
  return Object.fromEntries([...entries, [key, value]])
}
function captureTime(run: MorningBriefResponse) {
  const transition = run.trust.transitions.find(t => t.node === 'COMPOSE_DECISION_BRIEF' && !t.subNode)
    ?? run.trust.transitions[0]
  return transition ? Date.parse(transition.startedAt) + transition.durationMs : Date.now()
}
// Storage can be unavailable or full; the dashboard still works in memory.
const storage = createJSONStorage(() => ({
  getItem: (key: string) => { try { return sessionStorage.getItem(key) } catch { return null } },
  setItem: (key: string, value: string) => { try { sessionStorage.setItem(key, value) } catch { /* memory-only */ } },
  removeItem: (key: string) => { try { sessionStorage.removeItem(key) } catch { /* memory-only */ } },
}))
export const useAppStore = create<State>()(persist((set, get) => ({
  tenant: TENANTS[0], role: 'TRANSPORT_MANAGER', asOf: '2026-06-08', run: null, captures: {}, charts: {}, epoch: 0, busy: false, error: null, lastRefresh: 0,
  personaChosen: false,
  choosePersona: (role, tenant) => {
    const saved = get().captures[captureKey(tenant, get().asOf, role)]
    set(s => ({ role, tenant, personaChosen: true, run: saved?.run ?? null, lastRefresh: saved?.capturedAt ?? 0,
      epoch: s.epoch + 1, busy: false, error: null, activeNode: null, isLive: false }))
  },
  changePersona: () => set(s => ({ personaChosen: false, epoch: s.epoch + 1, busy: false, error: null })),
  setRole: role => {
    if (get().role === role) return
    const saved = get().captures[captureKey(get().tenant, get().asOf, role)]
    set(s => ({ role, run: saved?.run ?? null, lastRefresh: saved?.capturedAt ?? 0, epoch: s.epoch + 1, busy: false, error: null, activeNode: null, isLive: false }))
  },
  updateCapture: (run, epoch) => {
    if (get().epoch !== epoch || get().tenant !== run.businessUnit) return
    const captures = Object.fromEntries(Object.entries(get().captures).map(([key, capture]) => [key, capture.run.runId === run.runId ? { ...capture, run } : capture]))
    set({ captures, ...(get().run?.runId === run.runId ? { run } : {}) })
  },
  setTenant: tenant => {
    if (tenant === get().tenant) return
    const selected = get().captures[captureKey(tenant, get().asOf)]
    set(s => ({ tenant, run: selected?.run ?? null, epoch: s.epoch + 1, error: null, busy: false,
      lastRefresh: selected?.capturedAt ?? 0, timelineStep: 0, activeNode: null, isLive: false }))
  },
  setRun: (run, epoch) => {
    if (get().epoch !== epoch || get().tenant !== run.businessUnit) return
    // Approval updates must not replace the morning capture with a question-specific run.
    const captures = Object.fromEntries(Object.entries(get().captures).map(([key, capture]) =>
      [key, capture.run.runId === run.runId ? { ...capture, run } : capture]))
    set({ run, asOf: run.asOfDate, captures, error: null })
  },
  capture: (run, epoch) => {
    if (get().epoch !== epoch || get().tenant !== run.businessUnit) return
    const capturedAt = captureTime(run)
    set({ run, asOf: run.asOfDate, lastRefresh: capturedAt, error: null,
      captures: bounded(get().captures, captureKey(run.businessUnit, run.asOfDate), { run, capturedAt }) })
  },
  selectCapture: key => {
    const capture = get().captures[key]
    if (!capture || capture.run.businessUnit !== get().tenant) return null
    set({ run: capture.run, asOf: capture.run.asOfDate, lastRefresh: capture.capturedAt, error: null })
    return capture.run
  },
  saveCharts: (key, charts) => set(s => ({ charts: bounded(s.charts, key, charts) })),
  refresh: async () => {
    const { tenant, asOf, epoch } = get()
    try {
      const api = await import('./dashboardApi')
      const range = api.rangeFor(asOf)
      await api.startInvestigation(tenant, range.from, range.to, true)
    } catch (e) { if (get().epoch === epoch) set({ error: e instanceof Error ? e.message : 'Refresh failed; keeping the previous capture' }) }
  },
  activeNode: null, setActiveNode: activeNode => set({ activeNode }),
  timelineStep: 0, setTimelineStep: timelineStep => set({ timelineStep }),
  isLive: false, setLive: isLive => set({ isLive }),
}), {
  name: 'mobility-dashboard-captures-v1', version: 3, storage,
  partialize: state => ({ tenant: state.tenant, role: state.role, asOf: state.asOf, run: state.run,
    captures: state.captures, charts: state.charts, lastRefresh: state.lastRefresh }),
}))
