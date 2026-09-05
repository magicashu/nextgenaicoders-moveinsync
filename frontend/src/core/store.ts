import { create } from 'zustand'
import type { MorningBriefResponse } from './contracts'
import { defaultIdentity, TENANTS } from './identity'
import { httpApi } from './api'

export type Tenant = typeof TENANTS[number]
type State = {
  tenant: Tenant
  run: MorningBriefResponse | null
  epoch: number
  busy: boolean
  error: string | null
  lastRefresh: number
  setTenant: (tenant: Tenant) => void
  setRun: (run: MorningBriefResponse, epoch: number) => void
  refresh: () => Promise<void>
  activeNode: string | null
  setActiveNode: (node: string | null) => void
  timelineStep: number
  setTimelineStep: (step: number) => void
  isLive: boolean
  setLive: (live: boolean) => void
}
export const identityFor = (businessUnit: string) => ({ ...defaultIdentity, businessUnit })
export const useAppStore = create<State>((set, get) => ({
  tenant: TENANTS[0], run: null, epoch: 0, busy: false, error: null, lastRefresh: 0,
  setTenant: tenant => set(s => ({ tenant, run: null, epoch: s.epoch + 1, error: null, busy: false, lastRefresh: 0, timelineStep: 0, activeNode: null, isLive: false })),
  setRun: (run, epoch) => {
    if (get().epoch === epoch && get().tenant === run.businessUnit) set({ run, lastRefresh: Date.now(), error: null })
  },
  refresh: async () => {
    const { tenant, run, epoch, busy } = get()
    if (!run || busy) return
    set({ busy: true, error: null })
    try { get().setRun(await httpApi.getWorkflow(identityFor(tenant), run.runId), epoch) }
    catch (e) { if (get().epoch === epoch) set({ error: e instanceof Error ? e.message : 'Refresh failed' }) }
    finally { if (get().epoch === epoch) set({ busy: false }) }
  },
  activeNode: null, setActiveNode: activeNode => set({ activeNode }),
  timelineStep: 0, setTimelineStep: timelineStep => set({ timelineStep }),
  isLive: false, setLive: isLive => set({ isLive }),
}))
