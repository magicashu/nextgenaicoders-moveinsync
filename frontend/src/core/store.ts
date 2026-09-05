import { create } from 'zustand'
import type { MorningBriefResponse } from './contracts'
import { defaultIdentity, TENANTS } from './identity'
import { httpApi } from './api'

export interface CopilotMessage {
  id: string
  sender: 'user' | 'agent'
  text: string
  timestamp: string
  isVoiceInput?: boolean
  metrics?: { label: string; value: string; delta?: string; color?: string }[]
  evidence?: string[]
  recommendedAction?: { title: string; rationale: string }
  audioUrl?: string
}

interface AppStore {
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
  setLive: (v: boolean) => void
  approvalState: 'pending' | 'approved' | 'rejected'
  setApprovalState: (s: 'pending' | 'approved' | 'rejected') => void
  lastRefresh: number
  refresh: () => void
  
  // Copilot Voice & Chat State
  isCopilotWidgetOpen: boolean
  setCopilotWidgetOpen: (v: boolean) => void
  toggleCopilotWidgetOpen: () => void
  isAudioModeEnabled: boolean
  setAudioModeEnabled: (v: boolean) => void
  isListening: boolean
  setIsListening: (v: boolean) => void
  isPlayingAudio: boolean
  setIsPlayingAudio: (v: boolean) => void
  activeAudioMessageId: string | null
  setActiveAudioMessageId: (id: string | null) => void
  sarvamSpeaker: string
  setSarvamSpeaker: (speaker: string) => void
  copilotMessages: CopilotMessage[]
  addCopilotMessage: (msg: CopilotMessage) => void
  clearCopilotMessages: () => void
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
  lastRefresh: Date.now(),
  refresh: () => set({ lastRefresh: Date.now() }),

  // Copilot Voice & Chat defaults
  isCopilotWidgetOpen: false,
  setCopilotWidgetOpen: (isCopilotWidgetOpen) => set({ isCopilotWidgetOpen }),
  toggleCopilotWidgetOpen: () => set((state) => ({ isCopilotWidgetOpen: !state.isCopilotWidgetOpen })),
  isAudioModeEnabled: false,
  setAudioModeEnabled: (isAudioModeEnabled) => set({ isAudioModeEnabled }),
  isListening: false,
  setIsListening: (isListening) => set({ isListening }),
  isPlayingAudio: false,
  setIsPlayingAudio: (isPlayingAudio) => set({ isPlayingAudio }),
  activeAudioMessageId: null,
  setActiveAudioMessageId: (activeAudioMessageId) => set({ activeAudioMessageId }),
  sarvamSpeaker: 'assistant',
  setSarvamSpeaker: (sarvamSpeaker) => set({ sarvamSpeaker }),
  copilotMessages: [
    {
      id: 'welcome-1',
      sender: 'agent',
      text: 'Hello! I am your Mobility Supervisor Agent. You can query me using text or voice. Enable Audio Mode for hands-free speech responses powered by Sarvam AI.',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      metrics: [
        { label: 'Active Tenant', value: 'pinnacle-Slc', color: 'var(--accent)' },
        { label: 'Data Window', value: 'May–July 2026', color: 'var(--accent2)' },
      ]
    }
  ],
  addCopilotMessage: (msg) => set((state) => ({ copilotMessages: [...state.copilotMessages, msg] })),
  clearCopilotMessages: () => set({ copilotMessages: [] }),
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

