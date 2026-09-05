import type { Identity } from './contracts'
import { identityHeaders } from './identity'

type Recognition = {
  continuous: boolean; interimResults: boolean; lang: string
  onresult: ((event: { resultIndex: number; results: { isFinal: boolean; 0: { transcript: string } }[] }) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void; abort(): void
}
type VoiceWindow = Window & { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition }
export class SpeechToTextEngine {
  private recognition: Recognition | null = null
  private timer: ReturnType<typeof setTimeout> | undefined
  constructor() {
    const browser = window as VoiceWindow
    const Constructor = browser.SpeechRecognition ?? browser.webkitSpeechRecognition
    if (Constructor) {
      this.recognition = new Constructor()
      this.recognition.continuous = false; this.recognition.interimResults = true; this.recognition.lang = 'en-IN'
    }
  }
  isSupported() { return this.recognition !== null }
  startListening(handler: { onTranscript: (text: string) => void; onError: (message: string) => void; onEnd: () => void }) {
    const recognition = this.recognition
    if (!recognition) { handler.onError('Voice input is not available in this browser. You can type your question.'); return }
    recognition.onresult = event => {
      const text = Array.from(event.results).map(result => result[0].transcript).join(' ')
      handler.onTranscript(text.slice(0, 500))
    }
    recognition.onerror = event => {
      handler.onError(event.error === 'not-allowed' ? 'Microphone access was not allowed. Enable it in your browser or type your question.'
        : event.error === 'no-speech' ? 'No speech was detected. Try again or type your question.' : 'Voice input is unavailable right now. Please type your question.')
    }
    recognition.onend = () => { clearTimeout(this.timer); handler.onEnd() }
    try { recognition.start(); this.timer = setTimeout(() => this.stopListening(), 30_000) }
    catch { handler.onError('Could not start the microphone. Please try again.'); handler.onEnd() }
  }
  stopListening() { clearTimeout(this.timer); this.recognition?.abort() }
  dispose() {
    this.stopListening()
    if (this.recognition) { this.recognition.onresult = null; this.recognition.onerror = null; this.recognition.onend = null }
  }
}

let audio: HTMLAudioElement | null = null
let pending: AbortController | null = null
let generation = 0
export function stopSpeaking() {
  generation++; pending?.abort(); pending = null
  audio?.pause(); audio = null
  if ('speechSynthesis' in window) window.speechSynthesis.cancel()
}
export async function speakText(text: string, identity: Identity, voice: string, callbacks: {
  onStart: () => void; onEnd: () => void; onNotice: (text: string) => void
}) {
  stopSpeaking()
  const attempt = generation
  pending = new AbortController()
  try {
    const response = await fetch('/api/v1/speech', { method: 'POST', signal: pending.signal,
      headers: { 'Content-Type': 'application/json', ...identityHeaders(identity) }, body: JSON.stringify({ text, voice }) })
    if (!response.ok) throw new Error('Audio unavailable')
    const value: { audio: string; contentType: string } = await response.json()
    if (attempt !== generation) return
    const player = new Audio('data:' + value.contentType + ';base64,' + value.audio)
    audio = player
    player.onended = () => { if (attempt === generation) { audio = null; callbacks.onEnd() } }
    player.onerror = () => { if (attempt === generation) { callbacks.onNotice('Audio playback failed. Your answer is still available as text.'); callbacks.onEnd() } }
    await player.play()
    if (attempt === generation) callbacks.onStart()
  } catch {
    if (attempt !== generation) return
    if (!('speechSynthesis' in window)) { callbacks.onNotice('Audio is unavailable in this browser. Your answer is available as text.'); callbacks.onEnd(); return }
    callbacks.onNotice('Using your browser voice because online audio is unavailable.')
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'en-IN'; utterance.rate = 1
    utterance.onstart = () => { if (attempt === generation) callbacks.onStart() }
    utterance.onend = () => { if (attempt === generation) callbacks.onEnd() }
    utterance.onerror = () => { if (attempt === generation) { callbacks.onNotice('Audio playback is unavailable. Your answer is available as text.'); callbacks.onEnd() } }
    window.speechSynthesis.speak(utterance)
  } finally { if (attempt === generation) pending = null }
}

