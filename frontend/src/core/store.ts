import { create } from 'zustand'
import type { Tenant } from './mockData'
import { TENANTS } from './mockData'

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
  setTenant: (t: Tenant) => void
  activeNode: string | null
  setActiveNode: (n: string | null) => void
  timelineStep: number
  setTimelineStep: (s: number) => void
  isLive: boolean
  setLive: (v: boolean) => void
  approvalState: 'pending' | 'approved' | 'rejected'
  setApprovalState: (s: 'pending' | 'approved' | 'rejected') => void
  
  // Copilot Voice & Chat State
  isAudioModeEnabled: boolean
  setAudioModeEnabled: (v: boolean) => void
  isListening: boolean
  setIsListening: (v: boolean) => void
  isPlayingAudio: boolean
  setIsPlayingAudio: (v: boolean) => void
  activeAudioMessageId: string | null
  setActiveAudioMessageId: (id: string | null) => void
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

  // Copilot Voice & Chat defaults
  isAudioModeEnabled: false,
  setAudioModeEnabled: (isAudioModeEnabled) => set({ isAudioModeEnabled }),
  isListening: false,
  setIsListening: (isListening) => set({ isListening }),
  isPlayingAudio: false,
  setIsPlayingAudio: (isPlayingAudio) => set({ isPlayingAudio }),
  activeAudioMessageId: null,
  setActiveAudioMessageId: (activeAudioMessageId) => set({ activeAudioMessageId }),
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
}))

