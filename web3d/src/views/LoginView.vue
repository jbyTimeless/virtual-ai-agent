<template>
  <div class="login-page">
    <!-- Animated background -->
    <canvas ref="bgCanvas" class="bg-canvas"></canvas>

    <!-- Floating particles overlay -->
    <div class="particles-overlay">
      <div v-for="i in 20" :key="i" class="particle" :style="particleStyle(i)"></div>
    </div>

    <!-- Login Card -->
    <div class="login-container animate-fade-in-up">
      <div class="login-card glass-card">
        <!-- Logo / Title -->
        <div class="login-header">
          <div class="logo-icon">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="24" cy="24" r="22" stroke="url(#grad)" stroke-width="2" fill="none" />
              <path d="M16 28C16 28 19 32 24 32C29 32 32 28 32 28" stroke="url(#grad)" stroke-width="2" stroke-linecap="round"/>
              <circle cx="18" cy="20" r="2" fill="#a78bfa"/>
              <circle cx="30" cy="20" r="2" fill="#67e8f9"/>
              <defs>
                <linearGradient id="grad" x1="0" y1="0" x2="48" y2="48">
                  <stop stop-color="#a78bfa"/>
                  <stop offset="1" stop-color="#06b6d4"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h1 class="neon-text">AI Virtual Mate</h1>
          <p class="login-subtitle">与你的 AI 虚拟伙伴开始对话吧 ✨</p>
        </div>

        <!-- Tab Switch -->
        <div class="tab-switch">
          <button
            :class="['tab-btn', { active: mode === 'login' }]"
            @click="mode = 'login'"
          >
            登录
          </button>
          <button
            :class="['tab-btn', { active: mode === 'register' }]"
            @click="mode = 'register'"
          >
            注册
          </button>
          <div class="tab-indicator" :style="{ left: mode === 'login' ? '4px' : 'calc(50% + 2px)' }"></div>
        </div>

        <!-- Form -->
        <form @submit.prevent="handleSubmit" class="login-form">
          <div class="form-group">
            <label class="form-label">用户名</label>
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input
                v-model="form.username"
                type="text"
                class="input-field"
                placeholder="请输入用户名"
                autocomplete="username"
              />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">密码</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                v-model="form.password"
                type="password"
                class="input-field"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
            </div>
          </div>

          <div v-if="mode === 'register'" class="form-group slide-down">
            <label class="form-label">昵称</label>
            <div class="input-wrapper">
              <span class="input-icon">✨</span>
              <input
                v-model="form.nickname"
                type="text"
                class="input-field"
                placeholder="给自己取个昵称吧"
              />
            </div>
          </div>

          <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

          <button type="submit" class="btn btn-primary submit-btn" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            <span v-else>{{ mode === 'login' ? '✦ 登录' : '✦ 注册' }}</span>
          </button>
        </form>

        <!-- Decorative sparkles -->
        <div class="sparkle sparkle-1">✦</div>
        <div class="sparkle sparkle-2">✧</div>
        <div class="sparkle sparkle-3">✦</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login, register } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const errorMsg = ref('')
const bgCanvas = ref<HTMLCanvasElement | null>(null)
let animFrameId: number

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

function particleStyle(i: number) {
  const size = 4 + Math.random() * 8
  const left = Math.random() * 100
  const delay = Math.random() * 8
  const duration = 6 + Math.random() * 10
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${left}%`,
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`
  }
}

async function handleSubmit() {
  errorMsg.value = ''
  if (!form.username || !form.password) {
    errorMsg.value = '请填写用户名和密码'
    return
  }

  loading.value = true
  try {
    let res
    if (mode.value === 'login') {
      res = await login({ username: form.username, password: form.password })
    } else {
      if (!form.nickname) {
        form.nickname = form.username
      }
      res = await register({
        username: form.username,
        password: form.password,
        nickname: form.nickname
      })
    }
    const { token, user } = res.data.data
    userStore.setAuth(token, user)
    router.push('/chat')
  } catch (err: any) {
    errorMsg.value = err.message || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}

// === Background animated gradient canvas ===
function initBgCanvas() {
  const canvas = bgCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  function resize() {
    canvas!.width = window.innerWidth
    canvas!.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  let time = 0
  function draw() {
    time += 0.003
    const w = canvas!.width, h = canvas!.height

    // Create animated gradient
    const grad = ctx!.createRadialGradient(
      w * (0.3 + 0.2 * Math.sin(time)),
      h * (0.3 + 0.2 * Math.cos(time * 0.7)),
      0,
      w * 0.5, h * 0.5, w * 0.8
    )
    grad.addColorStop(0, 'rgba(99, 102, 241, 0.12)')
    grad.addColorStop(0.4, 'rgba(14, 165, 233, 0.08)')
    grad.addColorStop(0.7, 'rgba(236, 72, 153, 0.04)')
    grad.addColorStop(1, 'rgba(240, 242, 248, 1)')

    ctx!.fillStyle = grad
    ctx!.fillRect(0, 0, w, h)

    // Draw floating circles
    for (let i = 0; i < 5; i++) {
      const x = w * (0.2 + 0.6 * Math.sin(time * (0.3 + i * 0.1) + i * 1.5))
      const y = h * (0.2 + 0.6 * Math.cos(time * (0.2 + i * 0.15) + i))
      const r = 60 + 40 * Math.sin(time + i)
      const g = ctx!.createRadialGradient(x, y, 0, x, y, r)
      g.addColorStop(0, `rgba(${i % 2 === 0 ? '124,58,237' : '6,182,212'}, 0.08)`)
      g.addColorStop(1, 'rgba(0,0,0,0)')
      ctx!.fillStyle = g
      ctx!.fillRect(0, 0, w, h)
    }

    animFrameId = requestAnimationFrame(draw)
  }
  draw()
}

onMounted(() => {
  initBgCanvas()
})

onUnmounted(() => {
  cancelAnimationFrame(animFrameId)
})
</script>

<style scoped>
.login-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.bg-canvas {
  position: absolute;
  inset: 0;
  z-index: 0;
}

/* === Particles === */
.particles-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.particle {
  position: absolute;
  bottom: -20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(167, 139, 250, 0.6), rgba(6, 182, 212, 0.3));
  animation: rise linear infinite;
  opacity: 0;
}

@keyframes rise {
  0% {
    transform: translateY(0) scale(1);
    opacity: 0;
  }
  10% {
    opacity: 0.8;
  }
  90% {
    opacity: 0.3;
  }
  100% {
    transform: translateY(-100vh) scale(0.3);
    opacity: 0;
  }
}

/* === Login Container === */
.login-container {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 420px;
  padding: 0 20px;
}

.login-card {
  padding: 40px 36px;
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: conic-gradient(
    from 0deg,
    transparent,
    rgba(124, 58, 237, 0.05),
    transparent,
    rgba(6, 182, 212, 0.05),
    transparent
  );
  animation: spin-slow 12s linear infinite;
  z-index: -1;
}

/* === Header === */
.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  animation: float 3s ease-in-out infinite;
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.login-header h1 {
  font-size: 1.8rem;
  font-weight: 700;
  margin-bottom: 8px;
}

.login-subtitle {
  color: var(--text-secondary);
  font-size: 0.9rem;
}

/* === Tab Switch === */
.tab-switch {
  display: flex;
  position: relative;
  background: var(--bg-input);
  border-radius: var(--radius-full);
  padding: 4px;
  margin-bottom: 28px;
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  font-family: var(--font-primary);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-muted);
  background: none;
  border: none;
  cursor: pointer;
  position: relative;
  z-index: 1;
  transition: color var(--duration-normal) var(--ease-smooth);
}

.tab-btn.active {
  color: var(--text-primary);
}

.tab-indicator {
  position: absolute;
  top: 4px;
  width: calc(50% - 6px);
  height: calc(100% - 8px);
  background: var(--bg-glass-hover);
  border-radius: var(--radius-full);
  transition: left var(--duration-normal) var(--ease-spring);
  border: 1px solid var(--border-glass);
}

/* === Form === */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.input-wrapper {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 1rem;
  pointer-events: none;
}

.input-wrapper .input-field {
  padding-left: 42px;
}

.slide-down {
  animation: fadeInUp 0.3s var(--ease-spring) forwards;
}

.error-msg {
  color: var(--neon-pink);
  font-size: 0.85rem;
  text-align: center;
}

.submit-btn {
  width: 100%;
  padding: 16px;
  font-size: 1rem;
  margin-top: 4px;
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin-slow 0.6s linear infinite;
}

/* === Sparkles === */
.sparkle {
  position: absolute;
  font-size: 1.2rem;
  color: var(--primary-light);
  animation: float 3s ease-in-out infinite;
  opacity: 0.5;
  pointer-events: none;
}

.sparkle-1 {
  top: 20px;
  right: 30px;
  animation-delay: 0s;
}

.sparkle-2 {
  bottom: 30px;
  left: 20px;
  animation-delay: 1s;
  color: var(--accent-light);
}

.sparkle-3 {
  top: 50%;
  right: 15px;
  animation-delay: 2s;
  color: var(--accent-pink-light);
}
</style>
