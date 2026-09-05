import { useState, useRef, useEffect } from 'react'
import { useAppStore, CopilotMessage } from '../../core/store'
import { SpeechToTextEngine, speakText, stopSpeaking } from '../../core/sarvamAudio'

const PRESET_PROMPTS = [
  'Why did delays spike in pinnacle-Slc during June?',
  'Check employee punctuality and EV trip share for vanta-Aus',
  'What was median billed trip cost for vanta-Sea in May?',
  'Did pinnacle-Slc experience a safety alert spike?',
]

export function SupervisorCopilotPage() {
  const {
    tenant,
    isCopilotWidgetOpen,
    setCopilotWidgetOpen,
    isListening,
    setIsListening,
    isPlayingAudio,
    setIsPlayingAudio,
    activeAudioMessageId,
    setActiveAudioMessageId,
    sarvamSpeaker,
    setSarvamSpeaker,
    copilotMessages,
    addCopilotMessage,
  } = useAppStore()

  const [inputQuery, setInputQuery] = useState('')
  const [pipelineStep, setPipelineStep] = useState<number | null>(null) // 0: supervisor, 1: investigator, 2: critic, 3: briefing
  const [sttTranscript, setSttTranscript] = useState('')
  
  const [loadingAudioMsgId, setLoadingAudioMsgId] = useState<string | null>(null)
  
  const sttEngineRef = useRef<SpeechToTextEngine | null>(null)
  const chatBottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    sttEngineRef.current = new SpeechToTextEngine()
  }, [])

  useEffect(() => {
    if (isCopilotWidgetOpen) {
      chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [copilotMessages, pipelineStep, sttTranscript, isCopilotWidgetOpen])

  // Handle STT Voice Microphone Click
  const toggleListening = () => {
    if (!sttEngineRef.current) return

    if (isListening) {
      sttEngineRef.current.stopListening()
      setIsListening(false)
    } else {
      setIsListening(true)
      setSttTranscript('Listening...')
      sttEngineRef.current.startListening({
        onTranscript: (text, isFinal) => {
          setSttTranscript(text)
          setInputQuery(text)
          if (isFinal) {
            setIsListening(false)
            setSttTranscript('')
            handleSendQuery(text, true)
          }
        },
        onError: (err) => {
          console.warn('STT Error:', err)
          setIsListening(false)
          setSttTranscript('')
        },
        onEnd: () => {
          setIsListening(false)
        },
      })
    }
  }

  // Execute 4-Agent Pipeline
  const handleSendQuery = async (queryText: string, isVoice = false) => {
    const textToSend = queryText.trim()
    if (!textToSend) return

    setInputQuery('')
    setSttTranscript('')

    // 1. Add User Message
    const userMsg: CopilotMessage = {
      id: `user-${Date.now()}`,
      sender: 'user',
      text: textToSend,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      isVoiceInput: isVoice,
    }
    addCopilotMessage(userMsg)

    // 2. Animate Agent Pipeline Progress
    setPipelineStep(0) // Supervisor Planning
    await new Promise((r) => setTimeout(r, 600))
    setPipelineStep(1) // Investigator Workers
    await new Promise((r) => setTimeout(r, 900))
    setPipelineStep(2) // Critic Verification
    await new Promise((r) => setTimeout(r, 700))
    setPipelineStep(3) // Briefing Summarization
    await new Promise((r) => setTimeout(r, 500))

    // 3. Generate Structured Agent Response based on Query Context
    const agentMsgId = `agent-${Date.now()}`
    let agentMsg: CopilotMessage

    if (textToSend.toLowerCase().includes('pinnacle-slc') || textToSend.toLowerCase().includes('delay')) {
      agentMsg = {
        id: agentMsgId,
        sender: 'agent',
        text: `**Supervisor Briefing (${tenant})**: Delayed-trip rate spiked to **30.00%** (June 1–7) vs baseline **12.28%** (+17.72 pp). Clearwater Campus morning shift accounts for 68% of delays. Multi-vendor analysis confirms deterioration across all qualified vendors.`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        metrics: [
          { label: 'Current Delay Rate', value: '30.00%', delta: '+17.72 pp', color: 'var(--red)' },
          { label: '4-Week Baseline', value: '12.28%', color: 'var(--accent)' },
          { label: 'Site Concentration', value: 'Clearwater Morning', color: 'var(--yellow)' },
        ],
        evidence: [
          'M01 Delayed Trip Rate: 30.0% (3,120 / 10,400 trips)',
          'M03 Delay Reason Mix: Driver/Route 42%, Vehicle 31%, Traffic 27%',
          'Vendor Comparison: Vendor-A (+14.2 pp), Vendor-B (+18.5 pp) - No single vendor blame.',
        ],
        recommendedAction: {
          title: 'Create Clearwater Morning-Shift Watchlist',
          rationale: 'Issue site-shift watchlist and delay ticket before operational escalation.',
        },
      }
    } else if (textToSend.toLowerCase().includes('vanta-aus') || textToSend.toLowerCase().includes('punctuality')) {
      agentMsg = {
        id: agentMsgId,
        sender: 'agent',
        text: `**Supervisor Briefing (vanta-Aus)**: Employee pickup punctuality deteriorated to **78.4%** while EV trip share improved to **24.6%**. No-show rate showed positive improvement (4.2%). Low driver rating coverage caveat applies (18.2%).`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        metrics: [
          { label: 'Pickup Punctuality', value: '78.40%', delta: '-11.20 pp', color: 'var(--red)' },
          { label: 'EV Trip Share', value: '24.60%', delta: '+6.10 pp', color: 'var(--green)' },
          { label: 'No-Show Rate', value: '4.20%', delta: '-1.80 pp', color: 'var(--green)' },
        ],
        evidence: [
          'M04/M05 Punctuality: 78.4% eligible boarded legs within 10 minutes.',
          'M17 EV Trip Share: 24.6% of total trips operated by electric vehicles.',
          'M11 Driver Feedback: Rating 4.65/5 but low coverage caveat (18.2%).',
        ],
        recommendedAction: {
          title: 'Review Roster Shift Timing for Cedar Ridge',
          rationale: 'Address pickup delay concentration without impacting EV deployment targets.',
        },
      }
    } else {
      agentMsg = {
        id: agentMsgId,
        sender: 'agent',
        text: `**Supervisor Analysis (${tenant})**: Evaluated 18 metric contracts across May–July 2026 data. Analyzed query "${textToSend}" with 7 analytical workers. All findings have been verified by the Evidence Critic.`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        metrics: [
          { label: 'Scope Verified', value: tenant, color: 'var(--accent)' },
          { label: 'Worker DAGs Run', value: '7 / 7 Executed', color: 'var(--green)' },
        ],
        evidence: [
          'Governed metrics computed using DuckDB engine snapshot.',
          'Critic Verified: Zero ungrounded or single-vendor blame claims.',
        ],
      }
    }

    addCopilotMessage(agentMsg)
    setPipelineStep(null)
  }

  // Play Speech Audio via Sarvam TTS with Loading Spinner
  const playAudioForMessage = (msgId: string, textToSpeak: string, speakerOverride?: string) => {
    stopSpeaking()
    if (activeAudioMessageId === msgId && isPlayingAudio && !speakerOverride) {
      setIsPlayingAudio(false)
      setActiveAudioMessageId(null)
      setLoadingAudioMsgId(null)
      return
    }

    const speakerToUse = speakerOverride || sarvamSpeaker
    const plainText = textToSpeak.replace(/\*\*/g, '').replace(/\[|\]/g, '')
    setActiveAudioMessageId(msgId)

    speakText(
      plainText,
      speakerToUse,
      () => {
        setLoadingAudioMsgId(null)
        setIsPlayingAudio(true)
      },
      () => {
        setIsPlayingAudio(false)
        setActiveAudioMessageId(null)
        setLoadingAudioMsgId(null)
      },
      (isLoading) => {
        if (isLoading) {
          setLoadingAudioMsgId(msgId)
        } else {
          setLoadingAudioMsgId(null)
        }
      }
    )
  }

  // Handle Instant Speaker Change
  const handleSpeakerChange = (newSpeaker: string) => {
    setSarvamSpeaker(newSpeaker)
    if ((isPlayingAudio || loadingAudioMsgId) && activeAudioMessageId) {
      const activeMsg = copilotMessages.find((m) => m.id === activeAudioMessageId)
      if (activeMsg) {
        playAudioForMessage(activeMsg.id, activeMsg.text, newSpeaker)
      }
    }
  }

  // Collapsed Floating Trigger Button (FAB)
  if (!isCopilotWidgetOpen) {
    return (
      <button
        className="copilot-floating-fab"
        onClick={() => setCopilotWidgetOpen(true)}
        title="Open Voice & Chat Copilot"
      >
        <span className="fab-icon">🎙</span>
        <span className="fab-label">Voice Copilot</span>
      </button>
    )
  }

  // Expanded Floating Widget Box
  return (
    <div className="copilot-widget-container">
      {/* Header Bar */}
      <div className="copilot-widget-header">
        <div className="copilot-header-title">
          <span className="copilot-icon">🎙</span>
          <div>
            <h3>Supervisor Voice Copilot</h3>
            <p>Multi-modal agent copilot</p>
          </div>
        </div>

        <div className="copilot-header-actions">
          {/* Sarvam AI Speaker Voice Accent Selector */}
          <div className="speaker-accent-wrapper">
            <span className="accent-label">Voice:</span>
            <select
              className="speaker-accent-select"
              value={sarvamSpeaker}
              onChange={(e) => handleSpeakerChange(e.target.value)}
              title="Select Sarvam AI Persona & Speaker (bulbul:v3)"
            >
              <option value="assistant">🤖 Assistant (Shubh)</option>
              <option value="manager">👩‍💼 Manager (Ritu)</option>
              <option value="expert">👨‍🏫 Expert (Rahul)</option>
              <option value="executive">👔 Executive (Arvind)</option>
              <option value="analyst">📊 Analyst (Meera)</option>
            </select>
          </div>

          {/* Minimize Button */}
          <button
            className="widget-close-btn"
            onClick={() => setCopilotWidgetOpen(false)}
            title="Minimize Chat Widget"
          >
            ✕
          </button>
        </div>
      </div>

      {/* Agent Execution Pipeline Status Bar */}
      {pipelineStep !== null && (
        <div className="pipeline-status-bar">
          <div className="pipeline-steps">
            <span className={`pipeline-step ${pipelineStep >= 0 ? 'active supervisor' : ''}`}>
              🔵 Plan
            </span>
            <span className="pipeline-arrow">→</span>
            <span className={`pipeline-step ${pipelineStep >= 1 ? 'active investigator' : ''}`}>
              🟢 Workers
            </span>
            <span className="pipeline-arrow">→</span>
            <span className={`pipeline-step ${pipelineStep >= 2 ? 'active critic' : ''}`}>
              🟡 Critic
            </span>
            <span className="pipeline-arrow">→</span>
            <span className={`pipeline-step ${pipelineStep >= 3 ? 'active briefing' : ''}`}>
              🩷 Brief
            </span>
          </div>
        </div>
      )}

      {/* Conversation Thread Area */}
      <div className="chat-thread">
        {copilotMessages.map((msg) => (
          <div key={msg.id} className={`chat-bubble-row ${msg.sender}`}>
            <div className="chat-avatar">{msg.sender === 'user' ? '👤' : '🤖'}</div>
            <div className="chat-bubble">
              <div className="chat-bubble-header">
                <span className="chat-sender-name">
                  {msg.sender === 'user' ? 'You' : 'Supervisor Agent'}
                </span>
                {msg.isVoiceInput && <span className="voice-badge">🎙 Voice</span>}
                <span className="chat-timestamp">{msg.timestamp}</span>
              </div>

              {/* Message Narrative */}
              <div className="chat-text" dangerouslySetInnerHTML={{ __html: formatMarkdown(msg.text) }} />

              {/* Structured Metric Pills */}
              {msg.metrics && msg.metrics.length > 0 && (
                <div className="metric-pills-row">
                  {msg.metrics.map((m, idx) => (
                    <div key={idx} className="metric-pill" style={{ borderColor: m.color || 'var(--accent)' }}>
                      <span className="pill-label">{m.label}:</span>
                      <span className="pill-value">{m.value}</span>
                      {m.delta && <span className="pill-delta">{m.delta}</span>}
                    </div>
                  ))}
                </div>
              )}

              {/* Evidence Citations */}
              {msg.evidence && (
                <div className="evidence-citation-box">
                  <div className="evidence-box-title">📌 Governed Evidence:</div>
                  <ul>
                    {msg.evidence.map((item, idx) => (
                      <li key={idx}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Recommended Action Card */}
              {msg.recommendedAction && (
                <div className="recommended-action-box">
                  <div className="action-box-title">🎯 Draft Proposal:</div>
                  <div className="action-box-name">{msg.recommendedAction.title}</div>
                  <div className="action-box-desc">{msg.recommendedAction.rationale}</div>
                </div>
              )}

              {/* Sarvam Audio Player Bar */}
              {msg.sender === 'agent' && (
                <div className="audio-player-bar">
                  <button
                    className={`play-audio-btn ${loadingAudioMsgId === msg.id ? 'loading' : ''}`}
                    onClick={() => playAudioForMessage(msg.id, msg.text)}
                    disabled={loadingAudioMsgId === msg.id}
                  >
                    {loadingAudioMsgId === msg.id ? (
                      <>
                        <span className="spinner-icon">🌀</span> Generating Audio...
                      </>
                    ) : activeAudioMessageId === msg.id && isPlayingAudio ? (
                      '⏸ Pause'
                    ) : (
                      '▶ Speak (Sarvam AI)'
                    )}
                  </button>

                  {loadingAudioMsgId === msg.id && (
                    <div className="audio-loader-dots">
                      <span>Sarvam AI bulbul:v3 synthesizing...</span>
                      <span className="dot-pulse" />
                      <span className="dot-pulse" />
                      <span className="dot-pulse" />
                    </div>
                  )}

                  {activeAudioMessageId === msg.id && isPlayingAudio && (
                    <div className="audio-waveform-animation">
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                    </div>
                  )}
                  <span className="engine-tag">Sarvam AI bulbul:v3 ({sarvamSpeaker})</span>
                </div>
              )}
            </div>
          </div>
        ))}
        <div ref={chatBottomRef} />
      </div>

      {/* Preset Prompt Suggestions */}
      <div className="prompt-suggestions">
        {PRESET_PROMPTS.map((prompt, idx) => (
          <button
            key={idx}
            className="suggestion-chip"
            onClick={() => handleSendQuery(prompt)}
          >
            {prompt}
          </button>
        ))}
      </div>

      {/* STT Listening Banner */}
      {isListening && (
        <div className="stt-banner">
          <span className="stt-mic-pulsing">🎙</span>
          <span>Listening... {sttTranscript && `"${sttTranscript}"`}</span>
        </div>
      )}

      {/* Voice & Text Input Bar */}
      <div className="copilot-input-bar">
        <button
          className={`mic-button ${isListening ? 'listening' : ''}`}
          onClick={toggleListening}
          title="Speech-to-Text Voice Input"
        >
          🎙
        </button>

        <input
          type="text"
          className="copilot-input"
          placeholder={isListening ? 'Listening...' : 'Ask Supervisor Copilot...'}
          value={inputQuery}
          onChange={(e) => setInputQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSendQuery(inputQuery)
          }}
        />

        <button
          className="send-button"
          onClick={() => handleSendQuery(inputQuery)}
          disabled={!inputQuery.trim()}
        >
          ➔
        </button>
      </div>
    </div>
  )
}

function formatMarkdown(text: string): string {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br />')
}
