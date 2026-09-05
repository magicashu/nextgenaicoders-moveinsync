import { create } from 'zustand'
import { TENANTS } from './identity'

export type Tenant = typeof TENANTS[number]

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
  tenant: Tenant
  setTenant: (tenant: Tenant) => void
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
      ],
    },
  ],
  addCopilotMessage: (msg) => set((state) => ({ copilotMessages: [...state.copilotMessages, msg] })),
  clearCopilotMessages: () => set({ copilotMessages: [] }),
}))

export const identityFor = (businessUnit: string) => ({ businessUnit })
