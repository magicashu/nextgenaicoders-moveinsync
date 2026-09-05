import { useEffect, useRef, useState } from 'react'
import { httpApi } from '../../core/api'
import type { QuestionResponse } from '../../core/contracts'
import { identityFor, useAppStore } from '../../core/store'
import { SpeechToTextEngine, prepareSpeech, speakText, stopSpeaking, clearSpeechCache } from '../../core/sarvamAudio'
import { EvidenceSummary } from '../../shared/EvidenceSummary'
import { plain } from '../../core/presentation'

type Message = { id: string; sender: 'user' | 'assistant'; text: string; voice?: boolean; response?: QuestionResponse }
const suggestions = ['Which sites and shifts were most affected?', 'What reasons were recorded for the delays?', 'Did the cost per trip increase?']
export function FloatingCopilot({ onOpenReport }: { onOpenReport: () => void }) {
  const { tenant, role, asOf, run, epoch, busy: dashboardBusy } = useAppStore()
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<Message[]>([])
  const [busy, setBusy] = useState(false)
  const [listening, setListening] = useState(false)
  const [voiceInput, setVoiceInput] = useState(false)
  const [voiceSupported, setVoiceSupported] = useState(false)
  const [voice, setVoice] = useState('shubh')
  const [audioId, setAudioId] = useState<string | null>(null)
  const [audioLoading, setAudioLoading] = useState(false)
  const [notice, setNotice] = useState('')
  const engine = useRef<SpeechToTextEngine | null>(null)
  const active = useRef(true)
  const sending = useRef(false)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const launcher = useRef<HTMLButtonElement>(null)
  const bottom = useRef<HTMLDivElement>(null)
  useEffect(() => {
    active.current = true
    engine.current = new SpeechToTextEngine()
    setVoiceSupported(engine.current.isSupported())
    return () => { active.current = false; engine.current?.dispose(); clearSpeechCache() }
  }, [])
  useEffect(() => { if (open) inputRef.current?.focus() }, [open])
  useEffect(() => { if (open) bottom.current?.scrollIntoView({ block: 'nearest' }) }, [open, messages, busy])
  useEffect(() => {
    const latest = messages.at(-1)
    if (open && latest?.sender === 'assistant') {
      void prepareSpeech(latest.text, identityFor(tenant, role), voice)
    }
  }, [open, messages, tenant, role, voice])
  const current = () => active.current && useAppStore.getState().epoch === epoch
  const close = () => {
    engine.current?.stopListening(); stopSpeaking(); setListening(false); setAudioId(null); setAudioLoading(false); setOpen(false)
    launcher.current?.focus()
  }
  const append = (message: Message) => setMessages(previous => [...previous, message].slice(-40))
  const send = async (question = input) => {
    const text = question.trim()
    if (!text || text.length > 500 || sending.current || dashboardBusy) return
    sending.current = true; setBusy(true); setNotice(''); setInput('')
    engine.current?.stopListening(); setListening(false); stopSpeaking(); setAudioId(null); setAudioLoading(false)
    append({ id: crypto.randomUUID(), sender: 'user', text, voice: voiceInput })
    setVoiceInput(false)
    try {
      const response = await httpApi.ask(identityFor(tenant, role), { question: text, relatedRunId: run?.runId, asOfDate: asOf })
      if (current()) append({ id: crypto.randomUUID(), sender: 'assistant', text: plain(response.answer), response })
    } catch {
      if (current()) append({ id: crypto.randomUUID(), sender: 'assistant', text: 'I could not get an answer right now. Please try again in a moment. Your dashboard data has not changed.' })
    } finally { sending.current = false; if (current()) setBusy(false) }
  }
  const microphone = () => {
    if (listening) { engine.current?.stopListening(); setListening(false); return }
    stopSpeaking(); setAudioId(null); setAudioLoading(false); setNotice(''); setListening(true)
    engine.current?.startListening({
      onTranscript: text => { if (current()) { setInput(text); setVoiceInput(true) } },
      onError: message => { if (current()) { setNotice(message); setListening(false) } },
      onEnd: () => { if (current()) setListening(false) },
    })
  }
  const play = (message: Message) => {
    if (audioId === message.id) { stopSpeaking(); setAudioId(null); setAudioLoading(false); return }
    engine.current?.stopListening(); setListening(false)
    setNotice(''); setAudioId(message.id); setAudioLoading(true)
    void speakText(message.text, identityFor(tenant, role), voice, {
      onStart: () => { if (current()) setAudioLoading(false) },
      onEnd: () => { if (current()) { setAudioId(null); setAudioLoading(false) } },
      onNotice: text => { if (current()) setNotice(text) },
    })
  }
  const openReport = async (response: QuestionResponse) => {
    if (!response.runId || sending.current) return
    sending.current = true; setBusy(true)
    try {
      const next = await httpApi.getWorkflow(identityFor(tenant, role), response.runId)
      if (current()) { useAppStore.getState().setRun(next, epoch); onOpenReport(); close() }
    } catch { if (current()) setNotice('That report is no longer available. Refresh the dashboard and ask again.') }
    finally { sending.current = false; if (current()) setBusy(false) }
  }
  return <>
    <button ref={launcher} className="copilot-floating-fab" type="button" aria-label={open ? 'Close Copilot chat' : 'Open Copilot chat and voice'}
      aria-expanded={open} aria-controls="copilot-chat" onClick={() => open ? close() : setOpen(true)}>
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 6V3M3 11v5M21 11v5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="12" cy="2.5" r="1.25" fill="currentColor"/><rect x="5" y="6" width="14" height="14" rx="4" stroke="currentColor" strokeWidth="1.8"/><circle cx="9" cy="11.5" r="1.25" fill="currentColor"/><circle cx="15" cy="11.5" r="1.25" fill="currentColor"/><path d="M9 16h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    </button>
    {open && <section id="copilot-chat" className="copilot-widget-container" role="dialog" aria-label="Mobility Copilot chat" onKeyDown={e => { if (e.key === 'Escape') close() }}>
      <header className="copilot-widget-header">
        <div className="copilot-header-title"><span className="copilot-icon" aria-hidden="true">◈</span><div><h3>Ask Copilot</h3><p>{tenant} · report as of {asOf}</p></div></div>
        <button className="widget-close-btn" type="button" aria-label="Minimize chat" onClick={close}>×</button>
      </header>
      <div className="chat-thread" role="log" aria-live="polite" aria-relevant="additions">
        {!messages.length && <div className="chat-welcome"><h3>How can I help?</h3><p>Ask about delays, arrivals, costs or safety in your selected report. Type a question or use the microphone.</p></div>}
        {messages.map(message => <div key={message.id} className={`chat-bubble-row ${message.sender === 'user' ? 'user' : 'agent'}`}>
          <div className="chat-bubble">
            <div className="chat-bubble-header"><span className="chat-sender-name">{message.sender === 'user' ? 'You' : 'Copilot'}</span>{message.voice && <span className="voice-badge">Voice input</span>}</div>
            <div className="chat-text" style={{ whiteSpace: 'pre-wrap' }}>{message.text}</div>
            {message.response && !message.response.refused && <EvidenceSummary findings={message.response.supportingFindings} caveats={message.response.caveats} evidence={message.response.evidence} />}
            {message.response?.runId && message.response.runId !== run?.runId && <button className="btn btn-secondary" disabled={busy} onClick={() => void openReport(message.response!)}>Open supporting report</button>}
            {message.sender === 'assistant' && <div className="audio-player-bar"><button className="play-audio-btn" type="button" onClick={() => play(message)} aria-label={audioId === message.id ? audioLoading ? 'Cancel audio preparation' : 'Stop reading answer' : 'Read answer aloud'}>{audioId === message.id ? audioLoading ? '■ Cancel' : '■ Stop reading' : '▷ Read aloud'}</button>{audioId === message.id && audioLoading && <span className="audio-preparing" role="status"><span className="audio-spinner" aria-hidden="true" />Preparing audio…</span>}</div>}
          </div>
        </div>)}
        {busy && <p className="chat-working" role="status">Preparing your answer…</p>}
        <div ref={bottom} />
      </div>
      {!messages.length && <div className="prompt-suggestions">{suggestions.map(prompt => <button className="suggestion-chip" key={prompt} disabled={busy || dashboardBusy} onClick={() => void send(prompt)}>{prompt}</button>)}</div>}
      <div className="chat-voice-settings"><label>Read-aloud voice <select value={voice} onChange={e => { stopSpeaking(); setAudioId(null); setAudioLoading(false); setVoice(e.target.value) }}><option value="shubh">Voice 1</option><option value="ritu">Voice 2</option><option value="rahul">Voice 3</option></select></label><small>{listening ? 'Listening… Click the microphone to stop.' : 'Voice input fills the box. Review it, then send.'}</small></div>
      {notice && <p className="chat-notice" role="status">{notice}</p>}
      {!voiceSupported && <p className="chat-notice">Voice input is unavailable in this browser. Text chat and read-aloud are still available.</p>}
      {dashboardBusy && <p className="chat-notice" role="status">Your report is being prepared. You can ask when it is ready.</p>}
      <form className="copilot-input-bar" onSubmit={e => { e.preventDefault(); void send() }}>
        <button className={`mic-button ${listening ? 'listening' : ''}`} type="button" onClick={microphone} disabled={!voiceSupported || busy || dashboardBusy} aria-label={listening ? 'Stop voice input' : 'Start voice input'} aria-pressed={listening}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3" stroke="currentColor" strokeWidth="1.8"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8" stroke="currentColor" strokeWidth="1.8"/></svg>
        </button>
        <textarea ref={inputRef} className="copilot-input" rows={2} maxLength={500} aria-label="Your question" placeholder="Ask about your transport report…" value={input}
          onChange={e => { setInput(e.target.value); setVoiceInput(false) }} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); void send() } }} />
        <button className="send-button" type="submit" aria-label="Send question" disabled={!input.trim() || busy || dashboardBusy || listening}>➔</button>
      </form>
    </section>}
  </>
}
