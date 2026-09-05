// Sarvam AI Voice Engine & Speech Services (STT + TTS)

export interface SpeechRecognitionResultHandler {
  onTranscript: (text: string, isFinal: boolean) => void
  onError?: (err: string) => void
  onEnd?: () => void
}

/**
 * Speech-to-Text (STT) Manager using Web Speech API with Sarvam AI Fallback
 */
export class SpeechToTextEngine {
  private recognition: any = null
  private isListening = false

  constructor() {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (SpeechRecognition) {
      this.recognition = new SpeechRecognition()
      this.recognition.continuous = false
      this.recognition.interimResults = true
      this.recognition.lang = 'en-IN' // Default to Indian English
    }
  }

  public isSupported(): boolean {
    return !!this.recognition
  }

  public startListening(handler: SpeechRecognitionResultHandler) {
    if (!this.recognition) {
      handler.onError?.('Speech recognition is not supported in this browser.')
      return
    }

    if (this.isListening) {
      this.stopListening()
    }

    this.recognition.onresult = (event: any) => {
      let interimTranscript = ''
      let finalTranscript = ''

      for (let i = event.resultIndex; i < event.results.length; ++i) {
        if (event.results[i].isFinal) {
          finalTranscript += event.results[i][0].transcript
        } else {
          interimTranscript += event.results[i][0].transcript
        }
      }

      const text = finalTranscript || interimTranscript
      if (text) {
        handler.onTranscript(text, !!finalTranscript)
      }
    }

    this.recognition.onerror = (event: any) => {
      console.warn('STT Error:', event.error)
      handler.onError?.(`Speech error: ${event.error}`)
      this.isListening = false
    }

    this.recognition.onend = () => {
      this.isListening = false
      handler.onEnd?.()
    }

    try {
      this.recognition.start()
      this.isListening = true
    } catch (e) {
      console.error('Failed to start speech recognition', e)
      this.isListening = false
    }
  }

  public stopListening() {
    if (this.recognition && this.isListening) {
      try {
        this.recognition.stop()
      } catch (e) {
        // ignore
      }
      this.isListening = false
    }
  }
}

/**
 * Sarvam AI Persona to Voice Speaker Mapping
 * Model: bulbul:v3
 * Codec: mp3
 */
export const VOICE_MAP: Record<string, string> = {
  assistant: 'shubh',
  manager: 'ritu',
  expert: 'rahul',
  executive: 'arvind',
  analyst: 'meera',
}

interface SpeakerProfile {
  sarvamSpeaker: string
  targetLanguage: string
  webPitch: number
  webRate: number
  gender: 'female' | 'male'
}

const SPEAKER_PROFILES: Record<string, SpeakerProfile> = {
  shubh: {
    sarvamSpeaker: 'shubh',
    targetLanguage: 'en-IN',
    webPitch: 1.35,
    webRate: 1.05,
    gender: 'female',
  },
  ritu: {
    sarvamSpeaker: 'ritu',
    targetLanguage: 'en-IN',
    webPitch: 1.25,
    webRate: 1.0,
    gender: 'female',
  },
  rahul: {
    sarvamSpeaker: 'rahul',
    targetLanguage: 'en-IN',
    webPitch: 0.85,
    webRate: 1.0,
    gender: 'male',
  },
  arvind: {
    sarvamSpeaker: 'arvind',
    targetLanguage: 'en-IN',
    webPitch: 0.7,
    webRate: 0.95,
    gender: 'male',
  },
  meera: {
    sarvamSpeaker: 'meera',
    targetLanguage: 'en-IN',
    webPitch: 1.45,
    webRate: 0.98,
    gender: 'female',
  },
}

export interface SarvamAudioResponse {
  text: string
  speaker: string
  audio: string
}

let currentAudioElement: HTMLAudioElement | null = null

/**
 * Harness Sarvam AI Text-To-Speech API (bulbul:v3 model, mp3 codec)
 * Returns structured JSON { text, speaker, audio }
 */
export async function textToSpeech(
  text: string,
  personaOrSpeaker: string = 'assistant'
): Promise<SarvamAudioResponse> {
  const sarvamApiKey = import.meta.env.VITE_SARVAM_API_KEY
  const speaker = VOICE_MAP[personaOrSpeaker] || personaOrSpeaker || 'shubh'

  if (!sarvamApiKey || import.meta.env.VITE_USE_SARVAM_TTS === 'false') {
    throw new Error('Sarvam AI API key is missing or disabled')
  }

  const response = await fetch('https://api.sarvam.ai/text-to-speech', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'api-subscription-key': sarvamApiKey,
    },
    body: JSON.stringify({
      inputs: [text.substring(0, 500)],
      target_language_code: 'en-IN',
      model: 'bulbul:v3',
      speaker: speaker,
      output_audio_codec: 'mp3',
    }),
  })

  if (!response.ok) {
    const errText = await response.text()
    throw new Error(`Sarvam AI API error (${response.status}): ${errText}`)
  }

  const data = await response.json()
  const audioBase64 = data.audios?.[0] || data.audio || ''

  return {
    text: text,
    speaker: personaOrSpeaker,
    audio: audioBase64,
  }
}

/**
 * Text-to-Speech (TTS) Execution with Instant Cancel, Loading Callback & Web Speech Fallback
 */
export async function speakText(
  text: string,
  personaOrSpeaker: string = 'assistant',
  onStart?: () => void,
  onEnd?: () => void,
  onLoadingChange?: (isLoading: boolean) => void
): Promise<void> {
  stopSpeaking()

  const speakerKey = VOICE_MAP[personaOrSpeaker] || personaOrSpeaker || 'shubh'
  const profile = SPEAKER_PROFILES[speakerKey] || SPEAKER_PROFILES['shubh']

  const sarvamApiKey = import.meta.env.VITE_SARVAM_API_KEY
  if (sarvamApiKey && import.meta.env.VITE_USE_SARVAM_TTS === 'true') {
    try {
      onLoadingChange?.(true)
      const ttsResult = await textToSpeech(text, personaOrSpeaker)
      onLoadingChange?.(false)
      if (ttsResult.audio) {
        onStart?.()
        const audioSrc = ttsResult.audio.startsWith('data:')
          ? ttsResult.audio
          : `data:audio/mp3;base64,${ttsResult.audio}`
        
        currentAudioElement = new Audio(audioSrc)
        currentAudioElement.onended = () => {
          currentAudioElement = null
          onEnd?.()
        }
        currentAudioElement.onerror = () => {
          currentAudioElement = null
          fallbackWebSpeech(text, profile, onStart, onEnd)
        }
        await currentAudioElement.play()
        return
      }
    } catch (err) {
      onLoadingChange?.(false)
      console.warn('Sarvam AI bulbul:v3 TTS API call failed, using tuned Web Speech fallback:', err)
    }
  }

  // Fallback to tuned Web Speech API
  onLoadingChange?.(false)
  fallbackWebSpeech(text, profile, onStart, onEnd)
}

function fallbackWebSpeech(
  text: string,
  profile: SpeakerProfile,
  onStart?: () => void,
  onEnd?: () => void
) {
  if (!('speechSynthesis' in window)) {
    onEnd?.()
    return
  }

  window.speechSynthesis.cancel()

  const utterance = new SpeechSynthesisUtterance(text.substring(0, 350))
  utterance.pitch = profile.webPitch
  utterance.rate = profile.webRate
  utterance.lang = 'en-IN'

  const voices = window.speechSynthesis.getVoices()
  if (voices && voices.length > 0) {
    const matchingVoice = voices.find((v) => {
      const name = v.name.toLowerCase()
      if (profile.gender === 'female') {
        return name.includes('female') || name.includes('zira') || name.includes('samantha') || name.includes('veena') || name.includes('karen')
      } else {
        return name.includes('male') || name.includes('david') || name.includes('daniel') || name.includes('alex') || name.includes('rishi')
      }
    })
    if (matchingVoice) {
      utterance.voice = matchingVoice
    }
  }

  utterance.onstart = () => onStart?.()
  utterance.onend = () => onEnd?.()
  utterance.onerror = () => onEnd?.()

  window.speechSynthesis.speak(utterance)
}

export function stopSpeaking() {
  if (currentAudioElement) {
    try {
      currentAudioElement.pause()
      currentAudioElement.currentTime = 0
    } catch (e) {
      // ignore
    }
    currentAudioElement = null
  }
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel()
  }
}


