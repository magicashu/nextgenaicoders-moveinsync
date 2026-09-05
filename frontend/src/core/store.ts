import { create } from 'zustand'
import type { Tenant } from './mockData'
import { TENANTS } from './mockData'

interface AppStore {
  tenant: Tenant
  setTenant: (t: Tenant) => void
  activeNode: string | null
  setActiveNode: (n: string | null) => void
  timelineStep: number
  setTimelineStep: (s: number) => void
  isLive: boolean
  setLive: (v: boolean) => void
  approvalState: 'pending' | 'approved' | 'rejected'
  setApprovalState: (s: 'pending' | 'approved' | 'rejected') => void
}

export const useAppStore = create<AppStore>((set) => ({
  tenant: TENANTS[0],
  setTenant: (tenant) => set({ tenant }),
  activeNode: null,
  setActiveNode: (activeNode) => set({ activeNode }),
  timelineStep: 15,
  setTimelineStep: (timelineStep) => set({ timelineStep }),
  isLive: false,
  setLive: (isLive) => set({ isLive }),
  approvalState: 'pending',
  setApprovalState: (approvalState) => set({ approvalState }),
}))
