import * as THREE from 'three'

/**
 * MMD 交互交互控制工具
 * 实现眼神跟随、头部转动等 LookAt 逻辑
 * 核心修复：使用相对旋转 (Relative Rotation) 防止骨骼飞出
 */

// 需要控制的骨骼名称 (MMD标准名)
const TARGET_BONES = {
    HEAD: ['頭', 'Head'],
    NECK: ['首', 'Neck'],
    EYES: ['両目', 'Eyes', '左目', 'LeftEye', '右目', 'RightEye']
}

// 限制角度 (弧度) - 进一步缩小范围以保证安全
const LIMITS = {
    HEAD: { h: 0.3, v: 0.2 }, // ~17度
    EYE: { h: 0.15, v: 0.1 }   // ~8度
}

interface InteractionState {
    mouse: THREE.Vector2
    target: THREE.Vector3
    windowHalfX: number
    windowHalfY: number
}

const state: InteractionState = {
    mouse: new THREE.Vector2(),
    target: new THREE.Vector3(),
    windowHalfX: window.innerWidth / 2,
    windowHalfY: window.innerHeight / 2
}

// 存储骨骼的初始旋转 (Bind Pose)
const initialQuaternions: Map<string, THREE.Quaternion> = new Map()

/**
 * 初始化监听器
 */
export function initInteraction() {
    window.addEventListener('mousemove', onDocumentMouseMove, false)
    window.addEventListener('resize', onWindowResize, false)
    // 重置初始状态，防止跨模型污染
    initialQuaternions.clear()
}

/**
 * 销毁监听器
 */
export function disposeInteraction() {
    window.removeEventListener('mousemove', onDocumentMouseMove)
    window.removeEventListener('resize', onWindowResize)
    initialQuaternions.clear()
}

function onDocumentMouseMove(event: MouseEvent) {
    state.mouse.x = (event.clientX - state.windowHalfX) / 2;
    state.mouse.y = (event.clientY - state.windowHalfY) / 2;
}

function onWindowResize() {
    state.windowHalfX = window.innerWidth / 2;
    state.windowHalfY = window.innerHeight / 2;
}

// 手臂摆动相关骨骼
const SWAY_BONES = ['左腕', '右腕', '左ひじ', '右ひじ']

/**
 * 更新手臂自然摆动 (呼吸感)
 * @param mesh SkinnedMesh
 * @param elapsed 总运行时间 (秒)
 */
export function updateArmSway(mesh: THREE.SkinnedMesh, elapsed: number) {
    if (!mesh.skeleton) return

    mesh.skeleton.bones.forEach(bone => {
        const name = bone.name
        if (SWAY_BONES.includes(name)) {
            // 首次访问时记录初始旋转 (此时应该是 Standing Pose 设置好的状态)
            if (!initialQuaternions.has(name)) {
                initialQuaternions.set(name, bone.quaternion.clone())
            }
            const initialQ = initialQuaternions.get(name)!

            // 计算摆动增量
            // 频率: 0.5Hz (2秒一次呼吸)
            // 幅度: 0.05弧度 (~3度)
            const angle = Math.sin(elapsed * 2.0) * 0.05

            // 左臂负向，右臂正向 (镜像摆动)
            const isLeft = name.includes('左')
            const axis = new THREE.Vector3(0, 0, 1) // 绕 Z 轴摆动 (前后/上下)

            const deltaQ = new THREE.Quaternion().setFromAxisAngle(axis, isLeft ? angle : -angle)

            // 应用旋转
            const targetQ = initialQ.clone().multiply(deltaQ)
            bone.quaternion.slerp(targetQ, 0.1)
        }
    })
}

/**
 * 在渲染循环中调用，更新骨骼旋转 (Gaze)
 * @param mesh SkinnedMesh
 */
export function updateGaze(mesh: THREE.SkinnedMesh) {
    if (!mesh.skeleton) return

    // 目标角度 (降低系数以防过激)
    // 鼠标 x (-1 to 1) -> Yaw (摇头)
    // 鼠标 y (-1 to 1) -> Pitch (点头)
    const yawScale = 0.0003
    const pitchScale = 0.0003

    const targetYaw = THREE.MathUtils.clamp(state.mouse.x * yawScale, -LIMITS.HEAD.h, LIMITS.HEAD.h)
    const targetPitch = THREE.MathUtils.clamp(state.mouse.y * pitchScale, -LIMITS.HEAD.v, LIMITS.HEAD.v)

    mesh.skeleton.bones.forEach(bone => {
        const name = bone.name

        // 检查是否是目标骨骼
        const isHead = TARGET_BONES.HEAD.includes(name) || TARGET_BONES.NECK.includes(name)
        const isEye = TARGET_BONES.EYES.includes(name)

        if (isHead || isEye) {
            // 首次访问时记录初始旋转
            if (!initialQuaternions.has(name)) {
                initialQuaternions.set(name, bone.quaternion.clone())
            }

            const initialQ = initialQuaternions.get(name)!

            // 计算相对旋转
            // 注意：Three.js 骨骼旋转顺序可能影响，通常 YXZ 适合摇头点头
            let deltaQ = new THREE.Quaternion()

            if (isHead) {
                // 头部：全量跟随
                deltaQ.setFromEuler(new THREE.Euler(targetPitch, targetYaw, 0, 'YXZ'))
            } else if (isEye) {
                // 眼睛：更加灵敏但范围更小
                const eyeYaw = THREE.MathUtils.clamp(state.mouse.x * 0.0006, -LIMITS.EYE.h, LIMITS.EYE.h)
                const eyePitch = THREE.MathUtils.clamp(state.mouse.y * 0.0006, -LIMITS.EYE.v, LIMITS.EYE.v)
                deltaQ.setFromEuler(new THREE.Euler(eyePitch, eyeYaw, 0, 'YXZ'))
            }

            // 目标旋转 = 初始旋转 * 增量旋转 (局部空间右乘)
            const targetQ = initialQ.clone().multiply(deltaQ)

            // 平滑插值
            bone.quaternion.slerp(targetQ, 0.1)
        }
    })
}
