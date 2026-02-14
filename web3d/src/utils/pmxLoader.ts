/**
 * 顶级 PMX 加载器 — 基于 mmd-parser + Three.js
 * 极致空间对位 + MMD Flag 深度适配 + 纹理坐标修正
 */
import * as THREE from 'three'
// @ts-ignore
import { Parser } from 'mmd-parser'
import { TGALoader } from 'three/addons/loaders/TGALoader.js'

// 适配 MMD 标准的 Toon 渐变
const createMMDToonGradient = () => {
    const canvas = document.createElement('canvas')
    canvas.width = 256; canvas.height = 4
    const ctx = canvas.getContext('2d')!
    const gradient = ctx.createLinearGradient(0, 0, 256, 0)
    gradient.addColorStop(0.0, '#333333')
    gradient.addColorStop(0.3, '#777777')
    gradient.addColorStop(0.5, '#999999')
    gradient.addColorStop(0.7, '#bbbbbb')
    gradient.addColorStop(1.0, '#ffffff')
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, 256, 4)
    const texture = new THREE.CanvasTexture(canvas)
    texture.magFilter = texture.minFilter = THREE.LinearFilter
    texture.wrapS = texture.wrapT = THREE.ClampToEdgeWrapping
    return texture
}
const mmdToonGradient = createMMDToonGradient()

export async function loadPMX(
    url: string,
    onProgress?: (event: ProgressEvent) => void
): Promise<THREE.SkinnedMesh> {
    const buffer = await fetchArrayBuffer(url, onProgress)
    const parser = new Parser()
    const data = url.toLowerCase().endsWith('.pmd')
        ? parser.parsePmd(buffer, true)
        : parser.parsePmx(buffer, true)

    const resourcePath = url.substring(0, url.lastIndexOf('/') + 1)

    // 1. 构建几何体 (空间镜像转换 + UV修正)
    const geometry = buildGeometry(data)

    // 2. 构建材质 (Flag 深度适配 + 纹理翻转修正)
    const materials = buildMaterials(data, resourcePath)

    // 3. 构建骨骼 (Z轴同步镜像)
    const { skeleton, rootBone } = buildSkeleton(data)

    const mesh = new THREE.SkinnedMesh(geometry, materials)
    mesh.add(rootBone)
    mesh.bind(skeleton)

    // 4. 构建勾边 (严格遵循 Edge Flag)
    const outlineMesh = buildOutlineMesh(geometry, data, skeleton)
    if (outlineMesh) mesh.add(outlineMesh)

    mesh.frustumCulled = false
    mesh.updateMatrixWorld(true)

    // 脸部阴影优化：脸部不产生自阴影，只接收阴影
    mesh.onBeforeRender = function () {
        if (Array.isArray(this.material)) {
            this.material.forEach((m: any) => {
                if (m.userData.isFace) {
                    this.castShadow = false
                    m.polygonOffset = true
                    m.polygonOffsetFactor = -1.0
                }
            })
        }
    }

    return mesh
}

async function fetchArrayBuffer(url: string, onProgress?: (event: ProgressEvent) => void): Promise<ArrayBuffer> {
    return new Promise((resolve, reject) => {
        const loader = new THREE.FileLoader()
        loader.setResponseType('arraybuffer')
        loader.load(url, (data) => resolve(data as ArrayBuffer), onProgress, (err) => reject(err))
    })
}

function buildGeometry(data: any): THREE.BufferGeometry {
    const geo = new THREE.BufferGeometry()
    const vertexCount = data.metadata.vertexCount
    const positions = new Float32Array(vertexCount * 3)
    const normals = new Float32Array(vertexCount * 3)
    const uvs = new Float32Array(vertexCount * 2)
    const skinIndices = new Float32Array(vertexCount * 4)
    const skinWeights = new Float32Array(vertexCount * 4)

    for (let i = 0; i < vertexCount; i++) {
        const v = data.vertices[i]
        // 核心：Z 轴取反 (MMD Left-Handed -> Three.js Right-Handed)
        positions[i * 3] = v.position[0]
        positions[i * 3 + 1] = v.position[1]
        positions[i * 3 + 2] = -v.position[2]

        normals[i * 3] = v.normal[0]
        normals[i * 3 + 1] = v.normal[1]
        normals[i * 3 + 2] = -v.normal[2]

        // 核心：使用 (1 - v) 以匹配 OpenGL 纹理坐标系 (结合 flipY=true)
        uvs[i * 2] = v.uv[0]
        uvs[i * 2 + 1] = 1.0 - v.uv[1]

        const boneType = v.type ?? 0
        if (boneType === 0) {
            skinIndices[i * 4] = v.skinIndices[0] ?? 0
            skinWeights[i * 4] = 1.0
        } else if (boneType === 1) {
            skinIndices[i * 4] = v.skinIndices[0] ?? 0; skinIndices[i * 4 + 1] = v.skinIndices[1] ?? 0
            skinWeights[i * 4] = v.skinWeights[0] ?? 1.0; skinWeights[i * 4 + 1] = 1.0 - (v.skinWeights[0] ?? 1.0)
        } else {
            for (let j = 0; j < 4; j++) {
                skinIndices[i * 4 + j] = v.skinIndices?.[j] ?? 0
                skinWeights[i * 4 + j] = v.skinWeights?.[j] ?? 0
            }
        }
    }

    geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
    geo.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3))
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
    geo.setAttribute('skinIndex', new THREE.Uint16BufferAttribute(skinIndices, 4))
    geo.setAttribute('skinWeight', new THREE.Float32BufferAttribute(skinWeights, 4))

    // 核心：翻转绕序 (0, 1, 2 -> 0, 2, 1)
    let indices: Uint32Array
    if (data.faces && data.faces.length > 0) {
        let rawIndices: number[] = []
        if (typeof data.faces[0] === 'number') {
            for (let i = 0; i < data.faces.length; i += 3) {
                rawIndices.push(data.faces[i], data.faces[i + 2], data.faces[i + 1])
            }
        } else {
            for (let i = 0; i < data.faces.length; i++) {
                const face = data.faces[i].indices
                if (face) rawIndices.push(face[0], face[2], face[1])
            }
        }
        indices = new Uint32Array(rawIndices)
    } else indices = new Uint32Array(0)

    geo.setIndex(new THREE.BufferAttribute(indices, 1))

    let offset = 0
    for (let i = 0; i < data.materials.length; i++) {
        const mat = data.materials[i]
        const count = (mat.faceCount ?? (mat.indexCount ? mat.indexCount / 3 : 0)) * 3
        geo.addGroup(offset, count, i)
        offset += count
    }
    return geo
}

function buildMaterials(data: any, resourcePath: string): THREE.Material[] {
    const textureLoader = new THREE.TextureLoader()
    const tgaLoader = new TGALoader()
    const materials: THREE.Material[] = []

    for (const mat of data.materials) {
        const hasTex = mat.textureIndex != null && mat.textureIndex >= 0
        const hasToon = mat.toonIndex != null && mat.toonIndex >= 0
        const hasSphere = mat.sphereIndex != null && mat.sphereIndex >= 0

        // MMD Flag 解析 (Bit 0: DoubleSided)
        const flag = mat.flag ?? 0
        const isDoubleSide = (flag & 0x01) > 0

        const name = mat.name || ''
        const isFace = /脸|Face|Mouth|Eye/.test(name)
        const isSkin = /皮肤|Skin/.test(name) || isFace
        const isTransparent = mat.diffuse[3] < 1.0

        const material = new THREE.MeshToonMaterial({
            color: new THREE.Color(mat.diffuse[0], mat.diffuse[1], mat.diffuse[2]),
            opacity: mat.diffuse[3],
            transparent: isTransparent || isSkin,
            alphaTest: 0.1,
            side: isDoubleSide ? THREE.DoubleSide : THREE.FrontSide,
            gradientMap: mmdToonGradient
        })

        material.userData.isFace = isFace

        if (hasTex) {
            loadTex(data.textures[mat.textureIndex], (tex) => {
                // 核心修复：开启 flipY = true 以匹配 geometry 的 (1 - v)
                // 这解决了贴图垂直翻倒的问题
                tex.flipY = true
                tex.colorSpace = THREE.SRGBColorSpace
                material.map = tex
                material.needsUpdate = true
            })
        }

        if (hasToon) {
            loadTex(data.textures[mat.toonIndex], (tex) => {
                tex.flipY = true
                tex.magFilter = tex.minFilter = THREE.NearestFilter
                material.gradientMap = tex
                material.needsUpdate = true
            })
        }

        const sphereMode = mat.sphereMode ?? 0
        if (hasSphere && sphereMode !== 0) {
            const path = data.textures[mat.sphereIndex]
            loadTex(path, (tex) => {
                tex.flipY = true
                material.onBeforeCompile = (shader) => {
                    shader.uniforms.sphereMap = { value: tex }
                    shader.vertexShader = shader.vertexShader.replace('#include <common>', '#include <common>\nvarying vec3 vSphereNormal;')
                        .replace('#include <beginnormal_vertex>', '#include <beginnormal_vertex>\nvSphereNormal = normalize(normalMatrix * objectNormal);')

                    let logic = ''
                    if (sphereMode === 1) logic = 'outgoingLight *= texture2D(sphereMap, vSphereUV).rgb;'
                    else if (sphereMode === 2) logic = 'outgoingLight += texelColor.rgb * texture2D(sphereMap, vSphereUV).rgb;'

                    shader.fragmentShader = shader.fragmentShader.replace('#include <common>', '#include <common>\nvarying vec3 vSphereNormal;\nuniform sampler2D sphereMap;')
                        .replace('#include <dithering_fragment>', `#include <dithering_fragment>\nvec2 vSphereUV = vSphereNormal.xy * 0.5 + 0.5;\n${logic}`)
                }
                material.needsUpdate = true
            })
        }
        materials.push(material)
    }

    function loadTex(path: string, cb: (t: THREE.Texture) => void) {
        const url = resourcePath + path.replace(/\\/g, '/')
        const loader = url.toLowerCase().endsWith('.tga') ? tgaLoader : textureLoader
        loader.load(url, (t) => {
            t.wrapS = t.wrapT = THREE.RepeatWrapping
            t.anisotropy = 16
            t.minFilter = THREE.LinearMipmapLinearFilter
            t.magFilter = THREE.LinearFilter
            t.generateMipmaps = true
            cb(t)
        }, undefined, (err) => console.error('贴图失败:', url, err))
    }
    return materials
}

function buildOutlineMesh(geo: THREE.BufferGeometry, data: any, skeleton: THREE.Skeleton) {
    const materials: THREE.Material[] = []

    let hasEdge = false

    for (const mat of data.materials) {
        // MMD Flag Bit 4: Edge
        const flag = mat.flag ?? 0
        const enableEdge = (flag & 0x10) > 0
        if (enableEdge) hasEdge = true

        const size = mat.edgeSize ?? 1.0
        const finalSize = enableEdge ? size * 0.002 : 0

        const color = mat.edgeColor ?? [0, 0, 0, 1]

        materials.push(new THREE.MeshBasicMaterial({
            color: new THREE.Color(color[0], color[1], color[2]),
            opacity: enableEdge ? color[3] : 0,
            transparent: true,
            side: THREE.BackSide,
            onBeforeCompile: (shader) => {
                shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', `vec3 transformed = vec3(position) + normal * ${finalSize.toFixed(5)};`)
            }
        }))
    }

    if (!hasEdge) return null

    const mesh = new THREE.SkinnedMesh(geo, materials)
    mesh.bind(skeleton)
    mesh.renderOrder = -1
    return mesh
}

function buildSkeleton(data: any) {
    if (!data.bones || data.bones.length === 0) return { skeleton: new THREE.Skeleton([]), rootBone: new THREE.Bone() }

    const bones: THREE.Bone[] = []
    const bonePositions: THREE.Vector3[] = [] // 存储所有骨骼的“绝对世界坐标”（经过 Z轴取反后）

    // 1. 创建所有 Bone 对象并记录绝对跟坐标
    for (let i = 0; i < data.bones.length; i++) {
        const b = new THREE.Bone()
        const d = data.bones[i]
        b.name = d?.name ?? `bone_${i}`

        // 记录绝对坐标 (Z轴取反)
        const x = d?.position?.[0] ?? 0
        const y = d?.position?.[1] ?? 0
        const z = -(d?.position?.[2] ?? 0) // Z flip

        b.position.set(x, y, z)
        bonePositions.push(new THREE.Vector3(x, y, z))

        // 应用初始旋转 (如果有) - MMD通常只有位置，但PMX可能有默认旋转
        // 注意：这里暂不应用 d.rotation，因为 MMD 骨骼定义主要是 Position

        bones.push(b)
    }

    const root = new THREE.Bone(); root.name = '__root__'

    // 2. 建立层级关系并计算相对坐标
    for (let i = 0; i < data.bones.length; i++) {
        const d = data.bones[i]
        const pIdx = d?.parentIndex ?? -1

        if (pIdx >= 0 && pIdx < bones.length) {
            // 父子连接
            bones[pIdx].add(bones[i])

            // 核心修复：相对位置 = 子绝对 - 父绝对
            // 之前的错误代码使用了已经变成相对坐标的 bones[pIdx].position，导致层级越深偏移越严重
            const childGlobal = bonePositions[i]!
            const parentGlobal = bonePositions[pIdx]!

            bones[i].position.subVectors(childGlobal, parentGlobal)
        } else {
            root.add(bones[i])
            // 根节点保持绝对坐标 (相对于模型原点)
        }
    }

    return { skeleton: new THREE.Skeleton(bones), rootBone: root }
}