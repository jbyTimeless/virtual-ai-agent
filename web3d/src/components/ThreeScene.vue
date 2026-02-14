<template>
  <div ref="containerRef" class="three-container">
    <canvas ref="canvasRef"></canvas>
    <div v-if="isModelLoading" class="loading-overlay">
      <div class="loading-spinner">
        <div class="spinner-ring"></div>
        <p class="loading-text neon-text">加载模型中...</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { loadPMX } from '@/utils/pmxLoader'
import { applySittingPose } from '@/utils/mmdPose'
import { initInteraction, disposeInteraction, updateGaze } from '@/utils/mmdInteraction'
import gsap from 'gsap'

const props = defineProps<{
  modelPath: string
}>()

const emit = defineEmits<{
  (e: 'modelLoaded'): void
  (e: 'animationList', names: string[]): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const isModelLoading = ref(false)

let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let renderer: THREE.WebGLRenderer
let controls: OrbitControls
let mixer: THREE.AnimationMixer | null = null
let currentModel: THREE.Object3D | null = null
let clock: THREE.Clock
let animFrameId: number
let animations: THREE.AnimationClip[] = []

let currentModelType: 'glb' | 'pmx' = 'glb'

/**
 * 根据文件扩展名判断模型类型
 */
function getModelType(path: string): 'glb' | 'pmx' {
  const ext = path.split('.').pop()?.toLowerCase()
  if (ext === 'pmx' || ext === 'pmd') return 'pmx'
  return 'glb'
}

function initScene() {
  const container = containerRef.value!
  const canvas = canvasRef.value!

  // Scene
  scene = new THREE.Scene()
  scene.background = null

  // Camera
  const aspect = container.clientWidth / container.clientHeight
  camera = new THREE.PerspectiveCamera(45, aspect, 0.1, 1000)
  camera.position.set(0, 1.2, 3)

  // Renderer
  renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    logarithmicDepthBuffer: true, // 核心优化：解决多层衣服导致的 Z 冲突和粗糙感
    powerPreference: 'high-performance'
  })
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.NoToneMapping // 动漫模型通常不需要强烈的色调映射
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap

  // Lights
  // Lights - 极致动漫调优：平衡光感，防止冲淡 Toon 效果
  const hemisphereLight = new THREE.HemisphereLight(0xffffff, 0x555555, 0.6)
  scene.add(hemisphereLight)

  const mainLight = new THREE.DirectionalLight(0xffffff, 0.7)
  mainLight.position.set(5, 10, 5)
  mainLight.castShadow = true
  mainLight.shadow.mapSize.set(2048, 2048)
  // 终极针对性优化：调高 NormalBias 彻底消除脸部褶皱
  mainLight.shadow.bias = -0.0005
  mainLight.shadow.normalBias = 0.04 
  scene.add(mainLight)

  const fillLight = new THREE.DirectionalLight(0xfff5ee, 0.4) // 暖白色侧光，让皮肤更红润高级
  fillLight.position.set(-5, 3, 2)
  scene.add(fillLight)

  const rimLight = new THREE.DirectionalLight(0xffffff, 0.4)
  rimLight.position.set(-2, 2, -3)
  scene.add(rimLight)

  // Ground circle
  const groundGeo = new THREE.CircleGeometry(1.5, 64)
  const groundMat = new THREE.MeshStandardMaterial({
    color: 0x7c3aed,
    transparent: true,
    opacity: 0.1,
    emissive: 0x7c3aed,
    emissiveIntensity: 0.3
  })
  const ground = new THREE.Mesh(groundGeo, groundMat)
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.01
  ground.receiveShadow = true
  scene.add(ground)

  // Orbit Controls
  controls = new OrbitControls(camera, canvas)
  controls.target.set(0, 1, 0)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 1.5
  controls.maxDistance = 6
  controls.minPolarAngle = Math.PI * 0.2
  controls.maxPolarAngle = Math.PI * 0.65
  controls.enablePan = false
  controls.update()

  // Clock
  clock = new THREE.Clock()
}

/**
 * 清除当前模型
 */
function clearCurrentModel() {
  if (currentModel) {
    scene.remove(currentModel)
    currentModel = null
  }
  if (mixer) {
    mixer.stopAllAction()
    mixer = null
  }
  animations = []
}

/**
 * 自动缩放 & 居中模型（GLB 和 PMX 通用）
 */
function autoScaleAndCenter(model: THREE.Object3D) {
  const box = new THREE.Box3().setFromObject(model)
  const size = new THREE.Vector3()
  const center = new THREE.Vector3()
  box.getSize(size)
  box.getCenter(center)

  console.log('ThreeScene: 模型原始包围盒最小点:', box.min)
  console.log('ThreeScene: 模型原始尺寸:', size)
  console.log('ThreeScene: 模型原始中心:', center)

  const maxDim = Math.max(size.x, size.y, size.z)
  if (maxDim > 0) {
    const scale = 2 / maxDim
    console.log('ThreeScene: 计算出的缩放倍数:', scale)
    model.scale.setScalar(scale)
    model.position.y = -(box.min.y * scale)
    model.position.x = -(center.x * scale)
    model.position.z = -(center.z * scale)
    console.log('ThreeScene: 调整后位置:', model.position)
  } else {
    console.warn('ThreeScene: 模型尺寸检测为 0, 无法自动缩放.')
  }
}

/**
 * 入场动画（GSAP）
 */
function playEntryAnimation(model: THREE.Object3D) {
  gsap.from(model.position, {
    y: model.position.y - 0.5,
    duration: 1,
    ease: 'elastic.out(1, 0.5)'
  })
  gsap.from(model.rotation, {
    y: Math.PI * 2,
    duration: 1.2,
    ease: 'power3.out'
  })
  gsap.from(model.scale, {
    x: 0, y: 0, z: 0,
    duration: 0.8,
    ease: 'back.out(1.7)'
  })
}

/**
 * 加载 GLB/GLTF 模型
 */
async function loadGLBModel(path: string) {
  const loader = new GLTFLoader()
  const gltf = await loader.loadAsync(path)
  const model = gltf.scene

  autoScaleAndCenter(model)

  model.traverse((child) => {
    if ((child as THREE.Mesh).isMesh) {
      child.castShadow = true
      child.receiveShadow = true
    }
  })

  scene.add(model)
  currentModel = model

  // 动画
  if (gltf.animations.length > 0) {
    mixer = new THREE.AnimationMixer(model)
    animations = gltf.animations
    const idleClip = animations.find(a =>
      a.name.toLowerCase().includes('idle') || a.name.toLowerCase().includes('stand')
    ) || animations[0]
    if (idleClip) {
      mixer.clipAction(idleClip).play()
    }
    emit('animationList', animations.map(a => a.name))
  }
}

/**
 * 加载 PMX/PMD 模型（MMD 格式）
 */
async function loadPMXModel(path: string) {
  console.log('ThreeScene: 准备加载 PMX 模型...', path)
  const mesh = await loadPMX(path, (progress: ProgressEvent) => {
    if (progress.total > 0) {
      const pct = Math.round((progress.loaded / progress.total) * 100)
      console.log(`PMX 加载进度: ${pct}%`)
    }
  })

  console.log('ThreeScene: PMX 网格构建完成, 开始自动缩放...', mesh)
  autoScaleAndCenter(mesh)
  
  // 应用坐姿 (FK)
  applySittingPose(mesh)

  mesh.castShadow = true
  mesh.receiveShadow = true

  scene.add(mesh)
  currentModel = mesh
  emit('animationList', [])
}

let currentLoadId = 0

/**
 * 加载模型（统一入口，自动检测格式）
 */
async function loadModel(path: string) {
  if (!path) return
  
  const loadId = ++currentLoadId
  isModelLoading.value = true
  console.log(`ThreeScene: [Load#${loadId}] 开始加载任务: ${path}`)

  const type = getModelType(path)
  currentModelType = type

  try {
    // 异步加载前先清理
    clearCurrentModel()

    if (type === 'pmx') {
      await loadPMXModel(path)
    } else {
      await loadGLBModel(path)
    }

    // 检查是否已经是过时的加载请求
    if (loadId !== currentLoadId) {
      console.warn(`ThreeScene: [Load#${loadId}] 请求已过时，取消渲染`)
      return
    }

    if (currentModel) {
      console.log(`ThreeScene: [Load#${loadId}] 模型加载完毕`)
      playEntryAnimation(currentModel)
    }
    emit('modelLoaded')
  } catch (error) {
    if (loadId === currentLoadId) {
      console.error(`ThreeScene: [Load#${loadId}] 加载发生错误:`, error)
    }
  } finally {
    if (loadId === currentLoadId) {
      isModelLoading.value = false
    }
  }
}

function playAnimation(name: string) {
  if (!mixer || animations.length === 0) return
  const clip = animations.find(a => a.name === name)
  if (!clip) return
  mixer.stopAllAction()
  const action = mixer.clipAction(clip)
  action.reset().fadeIn(0.3).play()
}

function playTalkAnimation() {
  if (!currentModel) return

  // GLB: 尝试播放 talk 动画
  if (currentModelType === 'glb') {
    const talkClip = animations.find(a =>
      a.name.toLowerCase().includes('talk') || a.name.toLowerCase().includes('speak')
    )
    if (talkClip && mixer) {
      const current = mixer.existingAction(talkClip)
      if (current) {
        current.reset().fadeIn(0.3).play()
      } else {
        mixer.clipAction(talkClip).reset().fadeIn(0.3).play()
      }
      return
    }
  }

  // 通用后备：GSAP 晃动
  if (currentModel) {
    gsap.to(currentModel.rotation, {
      y: currentModel.rotation.y + 0.1,
      duration: 0.3,
      yoyo: true,
      repeat: 3,
      ease: 'sine.inOut'
    })
    gsap.to(currentModel.position, {
      y: currentModel.position.y + 0.02,
      duration: 0.4,
      yoyo: true,
      repeat: 2,
      ease: 'sine.inOut'
    })
  }
}

function playIdleAnimation() {
  if (!mixer || animations.length === 0) return
  const idleClip = animations.find(a =>
    a.name.toLowerCase().includes('idle') || a.name.toLowerCase().includes('stand')
  ) || animations[0]
  if (idleClip) {
    mixer.stopAllAction()
    mixer.clipAction(idleClip).reset().fadeIn(0.5).play()
  }
}

// Animation loop
function animate() {
  animFrameId = requestAnimationFrame(animate)
  const delta = clock.getDelta()

  // 动画更新
  if (mixer) mixer.update(delta)

  // 眼神跟随更新
  if (currentModelType === 'pmx' && currentModel) {
      updateGaze(currentModel as THREE.SkinnedMesh)
  }

  controls.update()
  renderer.render(scene, camera)
}

function handleResize() {
  const container = containerRef.value
  if (!container) return
  const w = container.clientWidth
  const h = container.clientHeight
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

// Watch for model path changes
watch(() => props.modelPath, (newPath) => {
  if (newPath) loadModel(newPath)
})

onMounted(() => {
  initScene()
  initInteraction() // 初始化交互监听
  animate()
  window.addEventListener('resize', handleResize)
  if (props.modelPath) loadModel(props.modelPath)
})

onUnmounted(() => {
  cancelAnimationFrame(animFrameId)
  disposeInteraction() // 销毁交互监听
  window.removeEventListener('resize', handleResize)
  renderer?.dispose()
  controls?.dispose()
})

// Expose methods to parent
defineExpose({ playAnimation, playTalkAnimation, playIdleAnimation, loadModel })
</script>

<style scoped>
.three-container {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: radial-gradient(ellipse at center bottom,
    rgba(124, 58, 237, 0.08) 0%,
    rgba(10, 10, 26, 0.95) 70%
  );
}

canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
}

.loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(10, 10, 26, 0.8);
  backdrop-filter: blur(8px);
  z-index: 10;
}

.loading-spinner {
  text-align: center;
}

.spinner-ring {
  width: 48px;
  height: 48px;
  border: 3px solid var(--border-glass);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin-slow 0.8s linear infinite;
  margin: 0 auto 16px;
}

.loading-text {
  font-size: 0.9rem;
  font-weight: 600;
}
</style>
