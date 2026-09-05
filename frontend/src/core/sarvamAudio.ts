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

type SpeechAudio = { audio: string; contentType: string }
type PreparedSpeech = { controller: AbortController; ready: boolean; result: Promise<SpeechAudio | null> }
const prepared = new Map<string, PreparedSpeech>()
const PREPARATION_TIMEOUT_MS = 30_000
const PLAYBACK_WAIT_MS = 4_000
const speechKey = (text: string, identity: Identity, voice: string) =>
  JSON.stringify([identity.actorId, identity.businessUnit, [...identity.roles].sort(), voice, text])

let audio: HTMLAudioElement | null = null
let generation = 0
function stopPlayback() {
  generation++
  audio?.pause(); audio = null
  if ('speechSynthesis' in window) window.speechSynthesis.cancel()
}
function cancelPreparationsExcept(key?: string) {
  for (const [id, entry] of prepared) {
    if (id !== key && !entry.ready) { prepared.delete(id); entry.controller.abort() }
  }
}
export function stopSpeaking() {
  stopPlayback()
  cancelPreparationsExcept()
}
export function clearSpeechCache() {
  stopSpeaking()
  prepared.clear()
}

// One shared request per exact answer/voice/identity. Preparation never starts playback.
function preparedSpeech(text: string, identity: Identity, voice: string): Promise<SpeechAudio | null> {
  if (!text.trim() || text.length > 2500) return Promise.resolve(null)
  const key = speechKey(text, identity, voice)
  cancelPreparationsExcept(key)
  const existing = prepared.get(key)
  if (existing) {
    prepared.delete(key); prepared.set(key, existing)
    return existing.result
  }
  while (prepared.size >= 8) {
    const oldest = prepared.keys().next().value!
    prepared.get(oldest)?.controller.abort()
    prepared.delete(oldest)
  }
  const entry: PreparedSpeech = { controller: new AbortController(), ready: false, result: Promise.resolve(null) }
  prepared.set(key, entry)
  entry.result = (async () => {
    const timeout = setTimeout(() => entry.controller.abort(), PREPARATION_TIMEOUT_MS)
    try {
      const response = await fetch('/api/v1/speech', { method: 'POST', signal: entry.controller.signal,
        headers: { 'Content-Type': 'application/json', ...identityHeaders(identity) }, body: JSON.stringify({ text, voice }) })
      if (!response.ok) throw new Error('Audio unavailable')
      const value: SpeechAudio = await response.json()
      if (entry.controller.signal.aborted || value.contentType !== 'audio/mpeg' || !value.audio) throw new Error('Audio unavailable')
      entry.ready = true
      return value
    } catch {
      if (prepared.get(key) === entry) prepared.delete(key)
      return null
    } finally { clearTimeout(timeout) }
  })()
  return entry.result
}
export async function prepareSpeech(text: string, identity: Identity, voice: string) {
  return (await preparedSpeech(text, identity, voice)) !== null
}
type SpeechCallbacks = { onStart: () => void; onEnd: () => void; onNotice: (text: string) => void }
function browserSpeech(text: string, attempt: number, callbacks: SpeechCallbacks, slow: boolean) {
  if (attempt !== generation) return
  if (!('speechSynthesis' in window)) {
    callbacks.onNotice('Audio is unavailable in this browser. Your answer is available as text.')
    callbacks.onEnd(); return
  }
  callbacks.onNotice(slow ? 'Using your browser voice to start sooner.' : 'Using your browser voice because online audio is unavailable.')
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'en-IN'; utterance.rate = 1
  utterance.onstart = () => { if (attempt === generation) callbacks.onStart() }
  utterance.onend = () => { if (attempt === generation) callbacks.onEnd() }
  utterance.onerror = () => { if (attempt === generation) { callbacks.onNotice('Audio playback is unavailable. Your answer is available as text.'); callbacks.onEnd() } }
  window.speechSynthesis.speak(utterance)
}
export async function speakText(text: string, identity: Identity, voice: string, callbacks: SpeechCallbacks) {
  // Keep the matching preparation alive when Play is clicked.
  stopPlayback()
  const attempt = generation
  const result = preparedSpeech(text, identity, voice)
  let timeout: ReturnType<typeof setTimeout> | undefined
  try {
    const value = 'speechSynthesis' in window
      ? await Promise.race([result, new Promise<'slow'>(resolve => { timeout = setTimeout(() => resolve('slow'), PLAYBACK_WAIT_MS) })])
      : await result
    if (attempt !== generation) return
    if (value === 'slow' || !value) { browserSpeech(text, attempt, callbacks, value === 'slow'); return }
    const player = new Audio('data:' + value.contentType + ';base64,' + value.audio)
    audio = player
    player.onended = () => { if (attempt === generation) { audio = null; callbacks.onEnd() } }
    player.onerror = () => { if (attempt === generation) { callbacks.onNotice('Audio playback failed. Your answer is still available as text.'); callbacks.onEnd() } }
    await player.play()
    if (attempt === generation) callbacks.onStart()
  } catch {
    if (attempt === generation) {
      audio?.pause(); audio = null
      browserSpeech(text, attempt, callbacks, false)
    }
  } finally { clearTimeout(timeout) }
}
