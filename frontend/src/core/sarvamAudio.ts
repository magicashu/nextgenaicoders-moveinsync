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
 * Text-to-Speech (TTS) Engine supporting Sarvam AI API and Web Speech API
 */
export async function speakText(
  text: string,
  onStart?: () => void,
  onEnd?: () => void
): Promise<void> {
  const sarvamApiKey = import.meta.env.VITE_SARVAM_API_KEY

  // If Sarvam API key is available and enabled, use Sarvam AI TTS endpoint
  if (sarvamApiKey && import.meta.env.VITE_USE_SARVAM_TTS === 'true') {
    try {
      onStart?.()
      const response = await fetch('https://api.sarvam.ai/text-to-speech', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'api-subscription-key': sarvamApiKey,
        },
        body: JSON.stringify({
          inputs: [text.substring(0, 500)],
          target_language_code: 'en-IN',
          speaker: 'shubhi',
          pitch: 0,
          pace: 1.05,
          loudness: 1.5,
          speech_sample_rate: 22050,
          enable_preprocessing: true,
          model: 'bulbul:v1',
        }),
      })

      if (response.ok) {
        const data = await response.json()
        if (data.audios && data.audios[0]) {
          const audio = new Audio(`data:audio/wav;base64,${data.audios[0]}`)
          audio.onended = () => onEnd?.()
          audio.onerror = () => fallbackWebSpeech(text, onStart, onEnd)
          await audio.play()
          return
        }
      }
    } catch (err) {
      console.warn('Sarvam TTS API failed, falling back to Web Speech API', err)
    }
  }

  // Fallback to Web Speech API
  fallbackWebSpeech(text, onStart, onEnd)
}

function fallbackWebSpeech(text: string, onStart?: () => void, onEnd?: () => void) {
  if (!('speechSynthesis' in window)) {
    onEnd?.()
    return
  }

  // Cancel any ongoing speech
  window.speechSynthesis.cancel()

  const utterance = new SpeechSynthesisUtterance(text.substring(0, 300))
  utterance.rate = 1.05
  utterance.pitch = 1.0
  utterance.lang = 'en-US'

  utterance.onstart = () => onStart?.()
  utterance.onend = () => onEnd?.()
  utterance.onerror = () => onEnd?.()

  window.speechSynthesis.speak(utterance)
}

export function stopSpeaking() {
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel()
  }
}
