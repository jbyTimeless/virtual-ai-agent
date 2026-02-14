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
let currentModel: THREE.Group | null = null
let clock: THREE.Clock
let animFrameId: number
let animations: THREE.AnimationClip[] = []

function initScene() {
  const container = containerRef.value!
  const canvas = canvasRef.value!

  // Scene
  scene = new THREE.Scene()
  scene.background = null // transparent, CSS handles background

  // Camera
  const aspect = container.clientWidth / container.clientHeight
  camera = new THREE.PerspectiveCamera(45, aspect, 0.1, 1000)
  camera.position.set(0, 1.2, 3)

  // Renderer
  renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance'
  })
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.2
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap

  // Lights — anime-style rim + ambient
  const ambientLight = new THREE.AmbientLight(0x8888cc, 0.6)
  scene.add(ambientLight)

  const mainLight = new THREE.DirectionalLight(0xffffff, 1.2)
  mainLight.position.set(2, 4, 3)
  mainLight.castShadow = true
  mainLight.shadow.mapSize.set(1024, 1024)
  scene.add(mainLight)

  // Rim light (purple tint from behind)
  const rimLight = new THREE.DirectionalLight(0x7c3aed, 0.8)
  rimLight.position.set(-2, 2, -3)
  scene.add(rimLight)

  // Accent light (cyan from side)
  const accentLight = new THREE.PointLight(0x06b6d4, 0.6, 10)
  accentLight.position.set(3, 1, 0)
  scene.add(accentLight)

  // Bottom fill light
  const fillLight = new THREE.DirectionalLight(0xec4899, 0.3)
  fillLight.position.set(0, -2, 2)
  scene.add(fillLight)

  // Ground circle (subtle glow platform)
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

async function loadModel(path: string) {
  if (!path) return
  isModelLoading.value = true

  // Remove current model
  if (currentModel) {
    scene.remove(currentModel)
    currentModel = null
    mixer = null
    animations = []
  }

  const loader = new GLTFLoader()

  try {
    const gltf = await loader.loadAsync(path)
    const model = gltf.scene

    // Auto-scale and center
    const box = new THREE.Box3().setFromObject(model)
    const size = new THREE.Vector3()
    const center = new THREE.Vector3()
    box.getSize(size)
    box.getCenter(center)

    const maxDim = Math.max(size.x, size.y, size.z)
    const scale = 2 / maxDim
    model.scale.setScalar(scale)

    // Position so feet are at y=0
    model.position.y = -(box.min.y * scale)
    model.position.x = -(center.x * scale)
    model.position.z = -(center.z * scale)

    // Enable shadows
    model.traverse((child) => {
      if ((child as THREE.Mesh).isMesh) {
        child.castShadow = true
        child.receiveShadow = true
      }
    })

    scene.add(model)
    currentModel = model as THREE.Group

    // Animations
    if (gltf.animations.length > 0) {
      mixer = new THREE.AnimationMixer(model)
      animations = gltf.animations
      // Play first animation by default (idle)
      const idleClip = animations.find(a =>
        a.name.toLowerCase().includes('idle') || a.name.toLowerCase().includes('stand')
      ) || animations[0]
      if (idleClip) {
        mixer.clipAction(idleClip).play()
      }
      emit('animationList', animations.map(a => a.name))
    }

    // Entry animation with GSAP
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

    emit('modelLoaded')
  } catch (error) {
    console.error('Failed to load model:', error)
  } finally {
    isModelLoading.value = false
  }
}

function playAnimation(name: string) {
  if (!mixer || animations.length === 0) return
  const clip = animations.find(a => a.name === name)
  if (!clip) return

  // Fade out all current
  mixer.stopAllAction()
  const action = mixer.clipAction(clip)
  action.reset().fadeIn(0.3).play()
}

function playTalkAnimation() {
  if (!currentModel) return
  // If there's a talk animation, play it
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

  // Fallback: GSAP head bob / subtle movement
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
  if (mixer) mixer.update(delta)
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
  animate()
  window.addEventListener('resize', handleResize)
  if (props.modelPath) loadModel(props.modelPath)
})

onUnmounted(() => {
  cancelAnimationFrame(animFrameId)
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
