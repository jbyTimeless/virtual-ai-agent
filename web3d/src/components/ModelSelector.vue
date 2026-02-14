<template>
  <div class="model-selector" :class="{ open: isOpen }">
    <button class="toggle-btn btn-ghost" @click="isOpen = !isOpen">
      <span>🎭</span>
      <span v-if="isOpen">收起</span>
      <span v-else>切换角色</span>
    </button>

    <transition name="slide-panel">
      <div v-if="isOpen" class="selector-panel glass-card">
        <h3 class="panel-title neon-text">选择虚拟角色</h3>
        <div class="model-list">
          <div
            v-for="model in models"
            :key="model.id"
            class="model-item glass-card-hover"
            :class="{ active: currentModelId === model.id }"
            @click="selectModel(model)"
          >
            <div class="model-preview">
              <span class="model-icon">{{ model.thumbnail || '🧸' }}</span>
            </div>
            <div class="model-info">
              <p class="model-name">{{ model.name }}</p>
              <p class="model-desc">{{ model.description || '3D 虚拟角色' }}</p>
            </div>
            <div v-if="currentModelId === model.id" class="active-badge">
              <span>✦</span>
            </div>
          </div>
        </div>
        <p class="hint-text">将 .glb 或 .pmx 文件放入 public/models/ 目录</p>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { ModelInfo } from '@/api/types'

const emit = defineEmits<{
  (e: 'select', model: ModelInfo): void
}>()

const isOpen = ref(false)
const currentModelId = ref('')

// Default built-in models (users add their own GLB files to public/models/)
const models = ref<ModelInfo[]>([
  {
    id: 'default',
    name: '默认角色',
    path: '/models/default.glb',
    thumbnail: '🤖',
    description: '默认 3D 虚拟角色'
  },
  {
    id: 'anime-girl',
    name: '动漫少女',
    path: '/models/dafeng/dafeng.pmx',
    thumbnail: '👧',
    description: '二次元风格角色'
  },
  {
    id: 'mecha',
    name: '机甲战士',
    path: '/models/mecha.glb',
    thumbnail: '🤖',
    description: '未来科技风格'
  },
  {
    id: 'fairy',
    name: '精灵使者',
    path: '/models/ph/ph.pmx',
    thumbnail: '🧚',
    description: '魔法精灵角色'
  }
])

function selectModel(model: ModelInfo) {
  currentModelId.value = model.id
  emit('select', model)
  isOpen.value = false
}

onMounted(() => {
  if (models.value.length > 0) {
    currentModelId.value = models.value[0].id
  }
})
</script>

<style scoped>
.model-selector {
  position: relative;
  z-index: 20;
}

.toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  font-size: 0.85rem;
  border-radius: var(--radius-full);
}

.selector-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 280px;
  padding: 20px;
  z-index: 50;
}

.panel-title {
  font-size: 1rem;
  font-weight: 700;
  margin-bottom: 16px;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}

.model-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  position: relative;
}

.model-item.active {
  border-color: var(--primary);
  background: var(--bg-glass-hover);
}

.model-preview {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  background: var(--bg-glass);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.4rem;
  flex-shrink: 0;
}

.model-info {
  flex: 1;
  min-width: 0;
}

.model-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.model-desc {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 2px;
}

.active-badge {
  color: var(--primary-light);
  font-size: 1rem;
  animation: float 2s ease-in-out infinite;
}

.hint-text {
  margin-top: 16px;
  font-size: 0.7rem;
  color: var(--text-muted);
  text-align: center;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

/* Slide panel transition */
.slide-panel-enter-active,
.slide-panel-leave-active {
  transition: all 0.3s var(--ease-spring);
}
.slide-panel-enter-from,
.slide-panel-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}
</style>
