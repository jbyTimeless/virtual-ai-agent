import * as THREE from 'three'

/**
 * MMD 模型姿态控制工具
 * 用于实现非动作文件控制的静态姿态（如坐姿）
 */

// 坐姿骨骼旋转定义 (角度制)
const SITTING_POSE = {
    '左足': { x: -85, y: 10, z: -5 },   // 左大腿抬起
    '右足': { x: -85, y: -10, z: 5 },   // 右大腿抬起
    '左ひざ': { x: 100, y: 0, z: 0 },   // 左膝弯曲
    '右ひざ': { x: 100, y: 0, z: 0 },   // 右膝弯曲
    '左足首': { x: -20, y: 0, z: 0 },    // 左脚踝放松
    '右足首': { x: -20, y: 0, z: 0 },    // 右脚踝放松
    'センター': { x: 0, y: -4, z: 0 }    // 中心下沉 (Position, not Rotation)
}

/**
 * 强制设置模型为二次元坐姿
 * @param mesh SkinnedMesh 对象
 */
export function applySittingPose(mesh: THREE.SkinnedMesh) {
    if (!mesh.skeleton) return

    mesh.skeleton.bones.forEach(bone => {
        const name = bone.name
        const pose = SITTING_POSE[name as keyof typeof SITTING_POSE]

        if (pose) {
            if (name === 'センター') {
                // 中心骨骼移动位置
                // bone.position.y -= 4 (需要根据模型缩放调整，这里暂不移动位置，避免穿地，依靠物理刚体或动画更好，FK简单旋转即可)
                // 仅旋转大腿和膝盖已经足够看起来像坐着
            } else {
                // 骨骼旋转 (欧拉角 -> 四元数)
                // MMD 骨骼坐标系通常不同，Three.js 中 bone.rotation 是局部空间
                // 尝试直接设置局部旋转
                const euler = new THREE.Euler(
                    THREE.MathUtils.degToRad(pose.x),
                    THREE.MathUtils.degToRad(pose.y),
                    THREE.MathUtils.degToRad(pose.z),
                    'XYZ'
                )
                bone.quaternion.setFromEuler(euler)
            }
        }
    })

    // 强制更新矩阵，确保修改立即生效
    mesh.updateMatrixWorld(true)
}
