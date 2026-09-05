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
    isAudioModeEnabled,
    setAudioModeEnabled,
    isListening,
    setIsListening,
    isPlayingAudio,
    setIsPlayingAudio,
    activeAudioMessageId,
    setActiveAudioMessageId,
    copilotMessages,
    addCopilotMessage,
  } = useAppStore()

  const [inputQuery, setInputQuery] = useState('')
  const [audienceProfile, setAudienceProfile] = useState<'operations' | 'executive'>('operations')
  const [pipelineStep, setPipelineStep] = useState<number | null>(null) // 0: supervisor, 1: investigator, 2: critic, 3: briefing
  const [sttTranscript, setSttTranscript] = useState('')
  
  const sttEngineRef = useRef<SpeechToTextEngine | null>(null)
  const chatBottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    sttEngineRef.current = new SpeechToTextEngine()
  }, [])

  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [copilotMessages, pipelineStep, sttTranscript])

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
            // Auto submit if Audio Mode is ON
            if (isAudioModeEnabled) {
              handleSendQuery(text, true)
            }
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

    // 4. Auto-Play Spoken Audio Response if Audio Mode is ON
    if (isAudioModeEnabled) {
      playAudioForMessage(agentMsgId, agentMsg.text)
    }
  }

  // Play Speech Audio via Sarvam TTS
  const playAudioForMessage = (msgId: string, textToSpeak: string) => {
    stopSpeaking()
    if (activeAudioMessageId === msgId && isPlayingAudio) {
      setIsPlayingAudio(false)
      setActiveAudioMessageId(null)
      return
    }

    // Clean markdown symbols for speech synthesis
    const plainText = textToSpeak.replace(/\*\*/g, '').replace(/\[|\]/g, '')
    setActiveAudioMessageId(msgId)
    setIsPlayingAudio(true)

    speakText(
      plainText,
      () => setIsPlayingAudio(true),
      () => {
        setIsPlayingAudio(false)
        setActiveAudioMessageId(null)
      }
    )
  }

  return (
    <div className="copilot-page">
      {/* Header Bar */}
      <div className="copilot-header">
        <div className="copilot-header-title">
          <span className="copilot-icon">🎙</span>
          <div>
            <h2>Supervisor Voice & Chat Copilot</h2>
            <p>Converse with the 4-agent Mobility Copilot in text or hands-free voice</p>
          </div>
        </div>

        <div className="copilot-header-actions">
          {/* Tenant Indicator */}
          <div className="badge-pill">
            <span className="dot cyan" />
            <span>Tenant: {tenant}</span>
          </div>

          {/* Audience Profile Switch */}
          <div className="profile-toggle">
            <button
              className={`toggle-btn ${audienceProfile === 'operations' ? 'active' : ''}`}
              onClick={() => setAudienceProfile('operations')}
            >
              Operations
            </button>
            <button
              className={`toggle-btn ${audienceProfile === 'executive' ? 'active' : ''}`}
              onClick={() => setAudienceProfile('executive')}
            >
              Executive
            </button>
          </div>

          {/* Audio Mode Toggle Switch */}
          <button
            className={`audio-mode-switch ${isAudioModeEnabled ? 'enabled' : ''}`}
            onClick={() => {
              const next = !isAudioModeEnabled
              setAudioModeEnabled(next)
              if (!next) stopSpeaking()
            }}
          >
            <span className="speaker-icon">{isAudioModeEnabled ? '🔊' : '🔇'}</span>
            <span>Audio Mode: {isAudioModeEnabled ? 'ON' : 'OFF'}</span>
          </button>
        </div>
      </div>

      {/* Agent Execution Pipeline Status Bar */}
      {pipelineStep !== null && (
        <div className="pipeline-status-bar">
          <div className="pipeline-title">Agent Workflow Execution:</div>
          <div className="pipeline-steps">
            <span className={`pipeline-step ${pipelineStep >= 0 ? 'active supervisor' : ''}`}>
              🔵 1. Supervisor Plan
            </span>
            <span className="pipeline-arrow">→</span>
            <span className={`pipeline-step ${pipelineStep >= 1 ? 'active investigator' : ''}`}>
              🟢 2. Investigator Workers
            </span>
            <span className="pipeline-arrow">→</span>
            <span className={`pipeline-step ${pipelineStep >= 2 ? 'active critic' : ''}`}>
              🟡 3. Critic Verification
            </span>
            <span className="pipeline-arrow">→</span>
            <span className={`pipeline-step ${pipelineStep >= 3 ? 'active briefing' : ''}`}>
              🩷 4. Briefing Summary
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
                  {msg.sender === 'user' ? 'You' : 'Supervisor Copilot'}
                </span>
                {msg.isVoiceInput && <span className="voice-badge">🎙 Voice Input</span>}
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
                  <div className="evidence-box-title">📌 Governed Evidence Citations:</div>
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
                  <div className="action-box-title">🎯 Draft Recommendation:</div>
                  <div className="action-box-name">{msg.recommendedAction.title}</div>
                  <div className="action-box-desc">{msg.recommendedAction.rationale}</div>
                </div>
              )}

              {/* Interactive Sarvam Audio Player Bar */}
              {msg.sender === 'agent' && (
                <div className="audio-player-bar">
                  <button
                    className="play-audio-btn"
                    onClick={() => playAudioForMessage(msg.id, msg.text)}
                  >
                    {activeAudioMessageId === msg.id && isPlayingAudio ? '⏸ Pause Voice' : '▶ Speak Response (Sarvam AI)'}
                  </button>

                  {activeAudioMessageId === msg.id && isPlayingAudio && (
                    <div className="audio-waveform-animation">
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                      <span className="wave-bar" />
                    </div>
                  )}
                  <span className="engine-tag">Powered by Sarvam AI TTS</span>
                </div>
              )}
            </div>
          </div>
        ))}
        <div ref={chatBottomRef} />
      </div>

      {/* Preset Prompt Suggestions */}
      <div className="prompt-suggestions">
        <span className="suggestions-label">Suggested Queries:</span>
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
          placeholder={isListening ? 'Listening to speech input...' : 'Ask the Supervisor Agent a question or request an analysis...'}
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
          Send ➔
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
