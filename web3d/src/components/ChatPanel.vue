<template>
  <div class="chat-panel glass-card">
    <!-- Header -->
    <div class="chat-header">
      <h2 class="chat-title neon-text">💬 AI 对话</h2>
      <button class="btn-icon" @click="clearChat" title="清空对话">🗑️</button>
    </div>

    <!-- Messages -->
    <div ref="messagesRef" class="messages-container">
      <div v-if="chatStore.messages.length === 0" class="empty-state">
        <div class="empty-icon animate-float">💫</div>
        <p>发送一条消息开始对话吧~</p>
      </div>

      <transition-group name="message" tag="div">
        <div
          v-for="msg in chatStore.messages"
          :key="msg.id"
          class="message-wrapper"
          :class="msg.role"
        >
          <div class="message-avatar">
            {{ msg.role === 'user' ? '😊' : '🤖' }}
          </div>
          <div class="message-bubble" :class="msg.role">
            <p class="message-content">{{ msg.content }}</p>
            <span class="message-time">
              {{ formatTime(msg.timestamp) }}
              <span v-if="msg.type === 'voice'" class="voice-badge">🎤</span>
            </span>
          </div>
        </div>
      </transition-group>

      <!-- Typing indicator -->
      <div v-if="chatStore.isLoading" class="message-wrapper assistant">
        <div class="message-avatar">🤖</div>
        <div class="message-bubble assistant typing-bubble">
          <div class="typing-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="chat-input-area">
      <div class="input-row">
        <VoiceRecorder
          :disabled="chatStore.isLoading"
          @recorded="handleVoice"
          @transcribed="handleTranscribed"
        />
        <div class="text-input-wrapper">
          <input
            v-model="inputText"
            class="input-field chat-input"
            placeholder="输入消息..."
            @keyup.enter="handleSend"
            :disabled="chatStore.isLoading"
          />
        </div>
        <button
          class="send-btn btn-primary"
          @click="handleSend"
          :disabled="!inputText.trim() || chatStore.isLoading"
        >
          ✦
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { sendMessage, sendVoice } from '@/api/chat'
import VoiceRecorder from './VoiceRecorder.vue'
import type { ChatMessage } from '@/api/types'

const emit = defineEmits<{
  (e: 'aiReply'): void
}>()

const chatStore = useChatStore()
const inputText = ref('')
const messagesRef = ref<HTMLDivElement | null>(null)

function generateId() {
  return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

function formatTime(ts: number) {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return

  // Add user message
  const userMsg: ChatMessage = {
    id: generateId(),
    role: 'user',
    content: text,
    timestamp: Date.now(),
    type: 'text'
  }
  chatStore.addMessage(userMsg)
  inputText.value = ''
  scrollToBottom()

  // Call API
  chatStore.setLoading(true)
  try {
    const res = await sendMessage({
      message: text,
      agentId: chatStore.currentAgentId
    })
    const data = res.data.data
    if (data.conversationId) {
      chatStore.setConversationId(data.conversationId)
    }

    const aiMsg: ChatMessage = {
      id: generateId(),
      role: 'assistant',
      content: data.reply,
      timestamp: Date.now(),
      type: 'text'
    }
    chatStore.addMessage(aiMsg)
    emit('aiReply')
  } catch (err: any) {
    // Fallback mock response when backend is unavailable
    const aiMsg: ChatMessage = {
      id: generateId(),
      role: 'assistant',
      content: getMockReply(text),
      timestamp: Date.now(),
      type: 'text'
    }
    chatStore.addMessage(aiMsg)
    emit('aiReply')
  } finally {
    chatStore.setLoading(false)
    scrollToBottom()
  }
}

async function handleVoice(blob: Blob) {
  const userMsg: ChatMessage = {
    id: generateId(),
    role: 'user',
    content: '🎤 [语音消息]',
    timestamp: Date.now(),
    type: 'voice'
  }
  chatStore.addMessage(userMsg)
  scrollToBottom()

  chatStore.setLoading(true)
  try {
    const res = await sendVoice(blob, chatStore.conversationId || undefined)
    const data = res.data.data
    if (data.conversationId) {
      chatStore.setConversationId(data.conversationId)
    }
    const aiMsg: ChatMessage = {
      id: generateId(),
      role: 'assistant',
      content: data.reply,
      timestamp: Date.now(),
      type: 'text'
    }
    chatStore.addMessage(aiMsg)
    emit('aiReply')
  } catch {
    const aiMsg: ChatMessage = {
      id: generateId(),
      role: 'assistant',
      content: '收到你的语音啦~ 不过我暂时无法处理语音，请尝试文字对话哦 ✨',
      timestamp: Date.now(),
      type: 'text'
    }
    chatStore.addMessage(aiMsg)
    emit('aiReply')
  } finally {
    chatStore.setLoading(false)
    scrollToBottom()
  }
}

function clearChat() {
  chatStore.clearMessages()
}

function getMockReply(text: string): string {
  const replies = [
    `你说的"${text}"很有趣呢~ 让我想想... ✨`,
    '哇，这个问题好棒！让我来认真回答你~ 🌟',
    '嗯嗯，我明白了！作为你的 AI 伙伴，我很乐意和你讨论这个话题 💫',
    '这是一个很好的想法！我们可以一起深入探讨一下 🎯',
    '谢谢你的消息~ 我正在思考如何给你最好的回答 🤔✨'
  ]
  return replies[Math.floor(Math.random() * replies.length)]
}

function handleTranscribed(text: string) {
  inputText.value = text
}

watch(() => chatStore.messages.length, scrollToBottom)
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.chat-title {
  font-size: 1.1rem;
  font-weight: 700;
}

.btn-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--bg-glass);
  cursor: pointer;
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-smooth);
}
.btn-icon:hover {
  background: var(--bg-glass-hover);
  transform: scale(1.05);
}

/* === Messages === */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.empty-icon {
  font-size: 3rem;
}

.message-wrapper {
  display: flex;
  gap: 10px;
  max-width: 85%;
}

.message-wrapper.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-wrapper.assistant {
  align-self: flex-start;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-glass);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
  flex-shrink: 0;
  border: 1px solid var(--border-glass);
}

.message-bubble {
  padding: 12px 16px;
  border-radius: var(--radius-md);
  max-width: 100%;
  word-break: break-word;
}

.message-bubble.user {
  background: linear-gradient(135deg, var(--primary), var(--accent));
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-bubble.assistant {
  background: #f1f5f9;
  border: 1px solid var(--border-subtle);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

.message-content {
  font-size: 0.9rem;
  line-height: 1.6;
}

.message-time {
  display: block;
  font-size: 0.7rem;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 6px;
  text-align: right;
}

.message-bubble.assistant .message-time {
  color: var(--text-muted);
}

.voice-badge {
  margin-left: 4px;
}

/* === Typing indicator === */
.typing-bubble {
  padding: 14px 20px;
}

.typing-dots {
  display: flex;
  gap: 4px;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  background: var(--primary-light);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* === Message transition === */
.message-enter-active {
  transition: all 0.3s var(--ease-spring);
}
.message-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}

/* === Input area === */
.chat-input-area {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--border-subtle);
}

.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.text-input-wrapper {
  flex: 1;
}

.chat-input {
  border-radius: var(--radius-full);
  padding: 12px 20px;
}

.send-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  padding: 0;
  font-size: 1.1rem;
  flex-shrink: 0;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}
</style>
