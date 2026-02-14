import * as THREE from 'three'

/**
 * MMD 模型姿态控制工具
 * 用于实现非动作文件控制的静态姿态
 */

// 站立垂手姿态 (角度制)
const STANDING_POSE = {
    // 手臂下垂 (T-Pose -> A-Pose)
    // 调整为向外张开 30度 (相对于T-Pose水平线)，形成极宽的 A 字型，彻底解决穿模
    '左腕': { x: -5, y: 0, z: -25 },    // 左臂下放 30度 (较平)
    '右腕': { x: -5, y: 0, z: 25 },     // 右臂下放 30度 (镜像)
    '左ひじ': { x: 0, y: 0, z: -5 },   // 左肘进一步舒展
    '右ひじ': { x: 0, y: 0, z: 5 },    // 右肘进一步舒展

    // 腿部回归直立 (重置之前的坐姿)
    '左足': { x: 0, y: 0, z: 0 },
    '右足': { x: 0, y: 0, z: 0 },
    '左ひざ': { x: 0, y: 0, z: 0 },
    '右ひざ': { x: 0, y: 0, z: 0 }
}

/**
 * 强制设置模型为自然站立垂手姿态
 * @param mesh SkinnedMesh 对象
 */
export function applyStandingPose(mesh: THREE.SkinnedMesh) {
    if (!mesh.skeleton) return

    mesh.skeleton.bones.forEach(bone => {
        const name = bone.name
        const pose = STANDING_POSE[name as keyof typeof STANDING_POSE]

        if (pose) {
            // 转换为四元数
            const euler = new THREE.Euler(
                THREE.MathUtils.degToRad(pose.x),
                THREE.MathUtils.degToRad(pose.y),
                THREE.MathUtils.degToRad(pose.z),
                'XYZ'
            )
            bone.quaternion.setFromEuler(euler)
        }
    })

    mesh.updateMatrixWorld(true)
}

// 兼容导出 (保留坐姿以备未来切换)
const SITTING_POSE = {
    '左足': { x: -85, y: 10, z: -5 },
    '右足': { x: -85, y: -10, z: 5 },
    '左ひざ': { x: 100, y: 0, z: 0 },
    '右ひざ': { x: 100, y: 0, z: 0 },
    '左腕': { x: 0, y: 0, z: -35 },   // 坐着时手臂稍微张开一点
    '右腕': { x: 0, y: 0, z: 35 }
}

export function applySittingPose(mesh: THREE.SkinnedMesh) {
    if (!mesh.skeleton) return

    mesh.skeleton.bones.forEach(bone => {
        const name = bone.name
        const pose = SITTING_POSE[name as keyof typeof SITTING_POSE]
        if (pose) {
            const euler = new THREE.Euler(
                THREE.MathUtils.degToRad(pose.x),
                THREE.MathUtils.degToRad(pose.y),
                THREE.MathUtils.degToRad(pose.z),
                'XYZ'
            )
            bone.quaternion.setFromEuler(euler)
        }
    })
    mesh.updateMatrixWorld(true)
}
