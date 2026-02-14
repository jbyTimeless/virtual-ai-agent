<template>
  <div class="chat-view">
    <!-- Top Bar -->
    <header class="top-bar glass-card">
      <div class="top-bar-left">
        <ModelSelector @select="handleModelSelect" />
      </div>
      <div class="top-bar-center">
        <h1 class="app-title neon-text">AI Virtual Mate</h1>
      </div>
      <div class="top-bar-right">
        <span class="user-greeting">{{ userStore.nickname || 'User' }}</span>
        <button class="btn btn-ghost logout-btn" @click="handleLogout">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          退出
        </button>
      </div>
    </header>

    <!-- Main content -->
    <main class="main-content">
      <!-- 3D Scene -->
      <section class="scene-section">
        <ThreeScene
          ref="threeSceneRef"
          :modelPath="currentModelPath"
          @modelLoaded="onModelLoaded"
          @animationList="onAnimationList"
        />
        <!-- Status bar overlay -->
        <div class="scene-status" v-if="animNames.length > 0">
          <div class="anim-chips">
            <button
              v-for="name in animNames"
              :key="name"
              class="anim-chip"
              @click="playAnim(name)"
            >
              ▶ {{ name }}
            </button>
          </div>
        </div>
      </section>

      <!-- Resize Handle -->
      <div
        class="resize-handle"
        @mousedown="startResize"
      >
        <div class="resize-dots">
          <span></span><span></span><span></span>
        </div>
      </div>

      <!-- Chat Panel -->
      <section class="chat-section" :style="{ width: chatWidth + 'px' }">
        <ChatPanel @aiReply="onAiReply" />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import ThreeScene from '@/components/ThreeScene.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import ModelSelector from '@/components/ModelSelector.vue'
import type { ModelInfo } from '@/api/types'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const threeSceneRef = ref<InstanceType<typeof ThreeScene> | null>(null)
const currentModelPath = ref('/models/default.glb')
const animNames = ref<string[]>([])

// Draggable chat panel width
const CHAT_MIN = 280
const CHAT_MAX = 650
const CHAT_DEFAULT = 400
const STORAGE_KEY = 'chat-panel-width'

const chatWidth = ref(Number(localStorage.getItem(STORAGE_KEY)) || CHAT_DEFAULT)
let isResizing = false

function startResize(e: MouseEvent) {
  e.preventDefault()
  isResizing = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
}

function onResize(e: MouseEvent) {
  if (!isResizing) return
  const newWidth = window.innerWidth - e.clientX
  chatWidth.value = Math.min(CHAT_MAX, Math.max(CHAT_MIN, newWidth))
}

function stopResize() {
  isResizing = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  localStorage.setItem(STORAGE_KEY, String(chatWidth.value))
}

function handleModelSelect(model: ModelInfo) {
  currentModelPath.value = model.path
  // 切换智能体 → 切换聊天记录
  chatStore.switchAgent(model.id)
}

function onModelLoaded() {
  // Model loaded successfully
}

function onAnimationList(names: string[]) {
  animNames.value = names
}

function playAnim(name: string) {
  threeSceneRef.value?.playAnimation(name)
}

function onAiReply() {
  // Trigger talk animation when AI replies
  threeSceneRef.value?.playTalkAnimation()
  // Return to idle after a delay
  setTimeout(() => {
    threeSceneRef.value?.playIdleAnimation()
  }, 3000)
}

function handleLogout() {
  userStore.clearAuth()
  router.push('/login')
}

onMounted(() => {
  // 首次加载默认智能体的聊天历史
  chatStore.switchAgent('default')
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.chat-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-deep);
  overflow: hidden;
}

/* === Top Bar === */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  border-radius: 0;
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
  z-index: 30;
}

.top-bar-left {
  flex: 1;
}

.top-bar-center {
  flex: 1;
  text-align: center;
}

.app-title {
  font-size: 1.15rem;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.top-bar-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.user-greeting {
  font-size: 0.85rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.logout-btn {
  padding: 8px 16px;
  font-size: 0.8rem;
  gap: 6px;
}

/* === Main Content === */
.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
  gap: 0;
}

.scene-section {
  flex: 1;
  position: relative;
  min-width: 0;
}

.scene-status {
  position: absolute;
  bottom: 12px;
  left: 12px;
  right: 12px;
  z-index: 10;
}

.anim-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.anim-chip {
  padding: 6px 14px;
  font-size: 0.7rem;
  font-family: var(--font-primary);
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  backdrop-filter: blur(8px);
}

.anim-chip:hover {
  color: var(--primary);
  border-color: var(--primary-light);
  background: var(--bg-glass-hover);
}

/* === Resize Handle === */
.resize-handle {
  width: 8px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: transparent;
  transition: background var(--duration-fast);
  position: relative;
  z-index: 20;
}

.resize-handle:hover,
.resize-handle:active {
  background: var(--bg-glass-hover);
}

.resize-dots {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.resize-dots span {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: background var(--duration-fast);
}

.resize-handle:hover .resize-dots span {
  background: var(--primary);
}

/* === Chat Section === */
.chat-section {
  flex-shrink: 0;
  display: flex;
  border-left: 1px solid var(--border-subtle);
}

.chat-section > * {
  flex: 1;
}

/* === Responsive === */
@media (max-width: 768px) {
  .main-content {
    flex-direction: column;
  }

  .scene-section {
    height: 40%;
  }

  .resize-handle {
    display: none;
  }

  .chat-section {
    width: 100% !important;
    height: 60%;
    border-left: none;
    border-top: 1px solid var(--border-subtle);
  }

  .top-bar-center {
    display: none;
  }
}
</style>
