<template>
  <div class="voice-recorder">
    <button
      class="record-btn"
      :class="{ recording: isRecording, pulse: isRecording }"
      @mousedown="startRecording"
      @mouseup="stopRecording"
      @mouseleave="isRecording && stopRecording()"
      @touchstart.prevent="startRecording"
      @touchend.prevent="stopRecording"
      :disabled="disabled"
      :title="isRecording ? '松开结束' : '按住说话（语音转文字）'"
    >
      <svg v-if="!isRecording" class="mic-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
        <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
        <line x1="12" y1="19" x2="12" y2="23"/>
        <line x1="8" y1="23" x2="16" y2="23"/>
      </svg>
      <span class="recording-dot" v-else></span>
    </button>
    <span v-if="isRecording" class="recording-label">语音识别中...</span>
    <!-- Waveform visualization -->
    <canvas v-if="isRecording" ref="waveCanvas" class="wave-canvas"></canvas>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'recorded', blob: Blob): void
  (e: 'transcribed', text: string): void
}>()

const isRecording = ref(false)
const waveCanvas = ref<HTMLCanvasElement | null>(null)
let mediaRecorder: MediaRecorder | null = null
let audioChunks: Blob[] = []
let audioContext: AudioContext | null = null
let analyser: AnalyserNode | null = null
let animFrameId: number
let stream: MediaStream | null = null
let recognition: any = null

// Setup SpeechRecognition (Web Speech API)
function createRecognition() {
  const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  if (!SpeechRecognition) return null
  const r = new SpeechRecognition()
  r.continuous = true
  r.interimResults = true
  r.lang = 'zh-CN'
  return r
}

async function startRecording() {
  if (props.disabled) return
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' })
    audioChunks = []

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) audioChunks.push(e.data)
    }

    mediaRecorder.onstop = () => {
      const blob = new Blob(audioChunks, { type: 'audio/webm' })
      emit('recorded', blob)
      cleanup()
    }

    mediaRecorder.start()
    isRecording.value = true

    // Start speech recognition
    recognition = createRecognition()
    if (recognition) {
      let finalTranscript = ''
      recognition.onresult = (event: any) => {
        let interim = ''
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const t = event.results[i][0].transcript
          if (event.results[i].isFinal) {
            finalTranscript += t
          } else {
            interim += t
          }
        }
        // Emit whatever we have so far
        const text = (finalTranscript + interim).trim()
        if (text) emit('transcribed', text)
      }
      recognition.onerror = () => { /* silently ignore */ }
      recognition.start()
    }

    // Waveform visualization
    audioContext = new AudioContext()
    const source = audioContext.createMediaStreamSource(stream)
    analyser = audioContext.createAnalyser()
    analyser.fftSize = 256
    source.connect(analyser)
    drawWaveform()
  } catch (err) {
    console.error('Microphone access denied:', err)
  }
}

function stopRecording() {
  if (mediaRecorder && isRecording.value) {
    mediaRecorder.stop()
    isRecording.value = false
    cancelAnimationFrame(animFrameId)
  }
  if (recognition) {
    recognition.stop()
    recognition = null
  }
}

function drawWaveform() {
  if (!analyser || !waveCanvas.value) return
  const canvas = waveCanvas.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  canvas.width = canvas.offsetWidth * 2
  canvas.height = canvas.offsetHeight * 2

  const bufferLength = analyser.frequencyBinCount
  const dataArray = new Uint8Array(bufferLength)

  function draw() {
    if (!analyser || !ctx) return
    animFrameId = requestAnimationFrame(draw)
    analyser.getByteFrequencyData(dataArray)

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    const barWidth = (canvas.width / bufferLength) * 2
    let x = 0

    for (let i = 0; i < bufferLength; i++) {
      const barHeight = (dataArray[i] / 255) * canvas.height * 0.8
      const gradient = ctx.createLinearGradient(0, canvas.height, 0, canvas.height - barHeight)
      gradient.addColorStop(0, 'rgba(99, 102, 241, 0.6)')
      gradient.addColorStop(1, 'rgba(14, 165, 233, 0.4)')
      ctx.fillStyle = gradient
      ctx.fillRect(x, canvas.height - barHeight, barWidth - 1, barHeight)
      x += barWidth
    }
  }
  draw()
}

function cleanup() {
  if (stream) {
    stream.getTracks().forEach(t => t.stop())
    stream = null
  }
  if (audioContext) {
    audioContext.close()
    audioContext = null
  }
  analyser = null
}

onUnmounted(() => {
  cancelAnimationFrame(animFrameId)
  cleanup()
  if (mediaRecorder && isRecording.value) {
    mediaRecorder.stop()
  }
  if (recognition) {
    recognition.stop()
    recognition = null
  }
})
</script>

<style scoped>
.voice-recorder {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
}

.record-btn {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1.5px solid var(--border-glass);
  background: var(--bg-glass);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-spring);
  flex-shrink: 0;
  color: var(--text-secondary);
}

.record-btn:hover {
  background: var(--bg-glass-hover);
  border-color: var(--primary-light);
  color: var(--primary);
  transform: scale(1.08);
}

.record-btn.recording {
  background: rgba(244, 63, 94, 0.1);
  border-color: var(--neon-pink);
  box-shadow: 0 0 16px rgba(244, 63, 94, 0.25);
}

.record-btn.pulse {
  animation: mic-pulse 1.2s ease-in-out infinite;
}

@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 12px rgba(244, 63, 94, 0.2); }
  50% { box-shadow: 0 0 24px rgba(244, 63, 94, 0.4); }
}

.record-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.mic-svg {
  width: 20px;
  height: 20px;
}

.recording-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--neon-pink);
  animation: mic-pulse 0.8s ease-in-out infinite;
}

.recording-label {
  font-size: 0.72rem;
  color: var(--neon-pink);
  font-weight: 500;
  white-space: nowrap;
}

.wave-canvas {
  width: 80px;
  height: 28px;
  border-radius: 4px;
}
</style>
