import { onMounted, onUnmounted, ref } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { DRACOLoader } from 'three/addons/loaders/DRACOLoader.js'
import {
  EffectComposer,
  EffectPass,
  RenderPass,
  SelectiveBloomEffect,
} from 'postprocessing'
import {
  DOOR_NOT_LOGIN_BACKGROUND_WEBP_URL as backgroundUrl,
  DOOR_QIU_GLB_URL as qiuUrl,
  DOOR_HUAN_GLB_URL as huanUrl,
  DOOR_JIQI_GLB_URL as jiqiUrl,
} from '@/utils/clientOss'

const rootRef = ref(null)
const canvasRef = ref(null)

let renderer = null
let scene = null
let camera = null
let composer = null
let bloomEffect = null
let raycaster = null
let pointer = null
let rafId = 0
let disposed = false
let resizeObserver = null
let deskPlane = null
let dracoLoader = null

const clock = new THREE.Clock()
const models = { qiu: null, huan: null, jiqi: null }
const hitProxies = []
const bloomTargets = []
let hoverKey = null

const TMP_BOX = new THREE.Box3()
const TMP_SIZE = new THREE.Vector3()
const TMP_CENTER = new THREE.Vector3()

function lerp(a, b, t) {
  return a + (b - a) * t
}

function damp(current, target, lambda, dt) {
  return lerp(current, target, 1 - Math.exp(-lambda * dt))
}

function findNamed(root, name) {
  let found = null
  root.traverse((obj) => {
    if (!found && obj.name === name) found = obj
  })
  return found
}

function meshVolume(obj) {
  TMP_BOX.setFromObject(obj)
  TMP_BOX.getSize(TMP_SIZE)
  return Math.max(0, TMP_SIZE.x * TMP_SIZE.y * TMP_SIZE.z)
}

/** 用 Group 把物体挂到指定世界中心，修正旋转 pivot */
function wrapPivotAtWorldPoint(object, worldPoint) {
  const parent = object.parent
  if (!parent) return object
  parent.updateMatrixWorld(true)
  const pivot = new THREE.Group()
  pivot.name = `${object.name || 'part'}_pivot`
  parent.add(pivot)
  pivot.position.copy(parent.worldToLocal(worldPoint.clone()))
  pivot.attach(object)
  return pivot
}

/** 绕物体自身包围盒中心旋转（扇叶） */
function wrapPivotAtObjectCenter(object) {
  object.updateMatrixWorld(true)
  if (object.isMesh && object.geometry) {
    if (!object.geometry.boundingBox) object.geometry.computeBoundingBox()
    TMP_CENTER.copy(object.geometry.boundingBox.getCenter(new THREE.Vector3()))
    object.localToWorld(TMP_CENTER)
  } else {
    TMP_BOX.setFromObject(object)
    TMP_BOX.getCenter(TMP_CENTER)
  }
  return wrapPivotAtWorldPoint(object, TMP_CENTER)
}

/** 用几何最薄轴估计环平面法线（世界空间） */
function estimateRingPlaneNormal(mesh) {
  mesh.updateMatrixWorld(true)
  if (!mesh.geometry?.boundingBox) mesh.geometry?.computeBoundingBox?.()
  const bb = mesh.geometry?.boundingBox
  const localN = new THREE.Vector3(0, 0, 1)
  if (bb) {
    const sx = bb.max.x - bb.min.x
    const sy = bb.max.y - bb.min.y
    const sz = bb.max.z - bb.min.z
    if (sx <= sy && sx <= sz) localN.set(1, 0, 0)
    else if (sy <= sx && sy <= sz) localN.set(0, 1, 0)
    else localN.set(0, 0, 1)
  }
  return localN.transformDirection(mesh.matrixWorld).normalize()
}

/**
 * 球环双层枢轴：
 * swing = 整体绕球轻摆（限幅防穿模）
 * spin  = 环平面内顺时针自转
 */
function buildQiuRingRig(ring, centerWorld) {
  const parent = ring.parent
  const swing = new THREE.Group()
  swing.name = 'qiu-ring-swing'
  const spin = new THREE.Group()
  spin.name = 'qiu-ring-spin'
  parent.add(swing)
  swing.position.copy(parent.worldToLocal(centerWorld.clone()))
  swing.add(spin)
  spin.attach(ring)
  swing.updateMatrixWorld(true)
  const nWorld = estimateRingPlaneNormal(ring)
  const nLocal = nWorld.clone().transformDirection(
    new THREE.Matrix4().copy(swing.matrixWorld).invert(),
  ).normalize()
  return { swing, spin, spinAxis: nLocal }
}

function cloneMeshMaterials(root) {
  root.traverse((obj) => {
    if (!obj.isMesh) return
    obj.castShadow = true
    obj.receiveShadow = true
    obj.frustumCulled = true
    if (Array.isArray(obj.material)) {
      obj.material = obj.material.map((m) => (m ? m.clone() : m))
    } else if (obj.material) {
      obj.material = obj.material.clone()
    }
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
    mats.forEach((mat) => {
      if (!mat) return
      mat.userData.baseEmissiveIntensity = mat.emissiveIntensity ?? 0
      if (mat.emissive) {
        mat.userData.baseEmissiveColor = mat.emissive.clone()
      }
    })
  })
}

function makeContactShadow(radius = 0.28) {
  const geo = new THREE.CircleGeometry(radius, 48)
  const mat = new THREE.MeshBasicMaterial({
    color: 0x1a0e18,
    transparent: true,
    opacity: 0.42,
    depthWrite: false,
  })
  const mesh = new THREE.Mesh(geo, mat)
  mesh.rotation.x = -Math.PI / 2
  mesh.renderOrder = -1
  mesh.name = 'contact-shadow'
  return mesh
}

function makeGlowOrb(colorHex, radius, name) {
  const mesh = new THREE.Mesh(
    new THREE.SphereGeometry(radius, 20, 20),
    new THREE.MeshBasicMaterial({
      color: colorHex,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      toneMapped: false,
    }),
  )
  mesh.name = name
  mesh.renderOrder = 2
  mesh.userData.isGlow = true
  bloomTargets.push(mesh)
  return mesh
}

function registerBloom(obj) {
  if (!obj || bloomTargets.includes(obj)) return
  bloomTargets.push(obj)
}

function applyPartEmissive(mesh, colorHex, intensity) {
  if (!mesh) return
  const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
  mats.forEach((mat) => {
    if (!mat || !mat.emissive) return
    mat.emissive.setHex(colorHex)
    mat.emissiveIntensity = intensity
  })
}

function resetPartEmissive(mesh, dt, lambda = 8) {
  if (!mesh) return
  const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
  mats.forEach((mat) => {
    if (!mat || !mat.emissive) return
    const baseI = mat.userData.baseEmissiveIntensity ?? 0
    mat.emissiveIntensity = damp(mat.emissiveIntensity, baseI, lambda, dt)
    const baseC = mat.userData.baseEmissiveColor
    if (baseC) {
      mat.emissive.r = damp(mat.emissive.r, baseC.r, lambda, dt)
      mat.emissive.g = damp(mat.emissive.g, baseC.g, lambda, dt)
      mat.emissive.b = damp(mat.emissive.b, baseC.b, lambda, dt)
    }
  })
}

function prepareRoot(gltf, key) {
  const root = new THREE.Group()
  root.name = `door-${key}`
  root.userData.doorKey = key

  const model = gltf.scene
  model.updateMatrixWorld(true)

  TMP_BOX.setFromObject(model)
  TMP_BOX.getSize(TMP_SIZE)
  TMP_BOX.getCenter(TMP_CENTER)
  model.position.sub(TMP_CENTER)

  const holder = new THREE.Group()
  holder.name = `${key}-holder`
  holder.add(model)
  root.add(holder)

  const maxDim = Math.max(TMP_SIZE.x, TMP_SIZE.y, TMP_SIZE.z) || 1
  const fit = 0.52 / maxDim
  root.scale.setScalar(fit)
  root.userData.baseScale = fit
  root.userData.holder = holder
  root.userData.model = model
  root.userData.hover = 0
  root.userData.scaleMul = 1
  root.userData.lift = 0
  root.userData.spinSpeed = 0
  root.userData.spinSpeedB = 0
  root.userData.angle = 0
  root.userData.angleB = 0
  root.userData.pulse = 0

  cloneMeshMaterials(root)

  const part0 = findNamed(model, 'part_0')
  const part1 = findNamed(model, 'part_1')
  const part2 = findNamed(model, 'part_2')
  const part3 = findNamed(model, 'part_3')

  // 模型中心（已居中）世界点
  model.updateMatrixWorld(true)
  const modelCenter = model.localToWorld(new THREE.Vector3(0, 0, 0))

  if (key === 'qiu') {
    // Planet: part_0 主体（含核心/底座），part_1 外环
    const body = part0
    const ring = part1
    const rig = ring ? buildQiuRingRig(ring, modelCenter) : null
    const coreGlow = makeGlowOrb(0xb44cff, 0.09, 'qiu-core-glow')
    holder.add(coreGlow)
    if (body) registerBloom(body)
    if (ring) registerBloom(ring)
    root.userData.parts = {
      body,
      ring,
      ringSwing: rig?.swing || null,
      ringSpin: rig?.spin || null,
      ringSpinAxis: rig?.spinAxis || new THREE.Vector3(0, 1, 0),
      coreGlow,
      labels: {
        body: 'part_0',
        ring: 'part_1',
        coreApprox: 'qiu-core-glow(+part_0 emissive)',
      },
    }
  } else if (key === 'huan') {
    // Rotating: part_3 外框+底座，part_0 中心柱/星，part_1/part_2 双环
    const frame = part3
    const pillar = part0
    const ringA = part1
    const ringB = part2
    // 粉色内环缩小并略抬高，避免与外框穿模
    if (ringA) ringA.scale.setScalar(0.82)
    if (ringB) {
      ringB.scale.setScalar(0.72)
      ringB.position.y += 0.05
    }
    const ringPivotA = ringA ? wrapPivotAtWorldPoint(ringA, modelCenter) : null
    const ringPivotB = ringB ? wrapPivotAtWorldPoint(ringB, modelCenter) : null
    const flowA = makeGlowOrb(0xffd0a8, 0.035, 'huan-flow-a')
    const flowB = makeGlowOrb(0xff9ad8, 0.028, 'huan-flow-b')
    if (ringPivotA) ringPivotA.add(flowA)
    if (ringPivotB) ringPivotB.add(flowB)
    flowA.position.set(0.2, 0.02, 0)
    flowB.position.set(-0.16, -0.01, 0.03)
    if (pillar) registerBloom(pillar)
    if (ringA) registerBloom(ringA)
    if (ringB) registerBloom(ringB)
    root.userData.parts = {
      frame,
      pillar,
      ringA,
      ringB,
      ringPivotA,
      ringPivotB,
      flowA,
      flowB,
      labels: {
        frame: 'part_3',
        pillar: 'part_0',
        ringA: 'part_1',
        ringB: 'part_2',
      },
    }
  } else if (key === 'jiqi') {
    // Robot: part_0 机身，part_1 扇叶
    // 转轴 = 机身中心指向桨心（头顶轴），每帧从静止姿态按该轴旋转，禁止 rotateY 累加
    const body = part0
    const propeller = part1
    let propPivot = null
    let propShaftLocal = new THREE.Vector3(0, 1, 0)
    if (body && propeller) {
      body.updateMatrixWorld(true)
      propeller.updateMatrixWorld(true)
      const bodyC = new THREE.Box3().setFromObject(body).getCenter(new THREE.Vector3())
      const propC = new THREE.Box3().setFromObject(propeller).getCenter(new THREE.Vector3())
      const shaftWorld = new THREE.Vector3().subVectors(propC, bodyC).normalize()
      propPivot = wrapPivotAtObjectCenter(propeller)
      propPivot.rotation.set(0, 0, 0)
      propPivot.updateMatrixWorld(true)
      propShaftLocal = shaftWorld.clone().transformDirection(
        new THREE.Matrix4().copy(propPivot.matrixWorld).invert(),
      ).normalize()
    }

    const screenGlow = makeGlowOrb(0x4aa8ff, 0.07, 'jiqi-screen-glow')
    const eyeL = makeGlowOrb(0xffc45a, 0.028, 'jiqi-eye-l')
    const eyeR = makeGlowOrb(0xffc45a, 0.028, 'jiqi-eye-r')
    if (body) {
      body.updateMatrixWorld(true)
      TMP_BOX.setFromObject(body)
      TMP_BOX.getSize(TMP_SIZE)
      TMP_BOX.getCenter(TMP_CENTER)
      const localCenter = body.worldToLocal(TMP_CENTER.clone())
      screenGlow.position.set(localCenter.x, localCenter.y + TMP_SIZE.y * 0.02, localCenter.z + TMP_SIZE.z * 0.22)
      eyeL.position.set(localCenter.x - TMP_SIZE.x * 0.12, localCenter.y + TMP_SIZE.y * 0.12, localCenter.z + TMP_SIZE.z * 0.24)
      eyeR.position.set(localCenter.x + TMP_SIZE.x * 0.12, localCenter.y + TMP_SIZE.y * 0.12, localCenter.z + TMP_SIZE.z * 0.24)
      body.add(screenGlow)
      body.add(eyeL)
      body.add(eyeR)
    }
    root.userData.parts = {
      body,
      propeller,
      propPivot,
      propShaftLocal,
      screenGlow,
      eyeL,
      eyeR,
      labels: {
        body: 'part_0',
        propeller: 'part_1',
        screenApprox: 'jiqi-screen-glow',
        eyesApprox: 'jiqi-eye-l/r',
      },
    }
  }

  const shadow = makeContactShadow(key === 'jiqi' ? 0.22 : 0.3)
  shadow.position.y = -0.26
  root.add(shadow)
  root.userData.shadow = shadow

  return root
}

function addHitProxy(root, key) {
  const proxy = new THREE.Mesh(
    new THREE.SphereGeometry(0.48, 10, 10),
    new THREE.MeshBasicMaterial({ visible: false }),
  )
  proxy.userData.doorKey = key
  proxy.position.y = 0.05
  root.add(proxy)
  hitProxies.push(proxy)
}

async function loadAll() {
  // 压缩后的 GLB 使用 KHR_draco_mesh_compression，必须挂 DRACOLoader
  dracoLoader = new DRACOLoader()
  dracoLoader.setDecoderPath('/draco/')
  const loader = new GLTFLoader()
  loader.setDRACOLoader(dracoLoader)
  const specs = [
    ['qiu', qiuUrl],
    ['huan', huanUrl],
    ['jiqi', jiqiUrl],
  ]
  const out = {}
  for (const [key, url] of specs) {
    if (disposed) break
    const gltf = await loader.loadAsync(url)
    out[key] = prepareRoot(gltf, key)
  }
  return out
}

function layoutAdaptive() {
  if (!camera) return
  const aspect = Math.max(0.5, camera.aspect || 1)
  // 收紧间距：球右移、机器人左移；中间环略靠近镜头
  const spread = aspect > 1.45 ? 0.58 : aspect > 1.15 ? 0.5 : 0.42
  const y = -0.92
  const z = 0.95
  const scaleBoost = aspect > 1.45 ? 1 : aspect > 1.15 ? 0.94 : 0.86

  const specs = [
    {
      key: 'qiu',
      x: -spread,
      y,
      z: z - 0.02,
      rotY: 0.18,
      rotX: -0.08,
      rotZ: 0.03,
    },
    {
      key: 'huan',
      x: 0,
      y: y + 0.01,
      z: z + 0.1,
      rotY: 0,
      rotX: -0.05,
      rotZ: 0,
    },
    {
      key: 'jiqi',
      x: spread,
      y: y + 0.1,
      z: z + 0.02,
      rotY: -0.18,
      rotX: 0,
      rotZ: 0,
    },
  ]

  specs.forEach((s) => {
    const root = models[s.key]
    if (!root) return
    root.position.set(s.x, s.y, s.z)
    root.rotation.set(s.rotX, s.rotY, s.rotZ)
    root.userData.baseY = s.y
    root.userData.layoutScale = scaleBoost
    root.scale.setScalar(root.userData.baseScale * scaleBoost * root.userData.scaleMul)
    if (root.userData.shadow) {
      root.userData.shadow.position.y = s.key === 'jiqi' ? -0.34 : -0.27
      root.userData.shadow.scale.setScalar(s.key === 'jiqi' ? 0.85 : 1)
    }
  })
}

function placeModels(map) {
  ;['qiu', 'huan', 'jiqi'].forEach((key) => {
    const root = map[key]
    if (!root) return
    scene.add(root)
    models[key] = root
    addHitProxy(root, key)
  })
  layoutAdaptive()
}

function setupLights() {
  scene.add(new THREE.AmbientLight(0x7a6a88, 0.5))
  scene.add(new THREE.HemisphereLight(0xd0dcff, 0x2a1824, 0.48))

  // 主光：背景左侧灯笼方向 → 阴影落向右侧
  const key = new THREE.DirectionalLight(0xffc090, 1.65)
  key.position.set(-3.4, 2.9, 1.6)
  key.target.position.set(0.55, -1.0, 0.85)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 16
  key.shadow.camera.left = -3
  key.shadow.camera.right = 3
  key.shadow.camera.top = 3
  key.shadow.camera.bottom = -3
  key.shadow.bias = -0.0008
  key.shadow.normalBias = 0.02
  key.shadow.radius = 3
  scene.add(key)
  scene.add(key.target)

  const rim = new THREE.DirectionalLight(0xa8c4ff, 0.28)
  rim.position.set(2.2, 1.6, -1.0)
  scene.add(rim)

  // 左侧暖色点光贴近灯笼
  const lantern = new THREE.PointLight(0xffb06a, 0.85, 8, 2)
  lantern.position.set(-2.4, 0.55, 1.35)
  scene.add(lantern)

  const fill = new THREE.PointLight(0xff78c8, 0.28, 10, 2)
  fill.position.set(1.2, 1.0, 2.0)
  scene.add(fill)

  // 隐形接影平面（贴桌垫）
  deskPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(6.5, 3.2),
    new THREE.ShadowMaterial({ opacity: 0.28 }),
  )
  deskPlane.rotation.x = -Math.PI / 2
  deskPlane.position.set(0, -1.18, 0.9)
  deskPlane.receiveShadow = true
  scene.add(deskPlane)
}

function setSize() {
  const host = rootRef.value
  if (!host || !renderer || !camera) return
  const w = Math.max(1, host.clientWidth)
  const h = Math.max(1, host.clientHeight)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.15))
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  composer?.setSize(w, h)
  layoutAdaptive()
}

function pickHover(clientX, clientY) {
  const canvas = canvasRef.value
  if (!canvas || !camera || !raycaster) return null
  const rect = canvas.getBoundingClientRect()
  pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects(hitProxies, false)
  return hits[0]?.object?.userData?.doorKey || null
}

function onPointerMove(e) {
  hoverKey = pickHover(e.clientX, e.clientY)
  if (canvasRef.value) canvasRef.value.style.cursor = hoverKey ? 'pointer' : 'default'
}

function onPointerLeave() {
  hoverKey = null
}

function setGlowOpacity(mesh, opacity) {
  if (!mesh?.material) return
  mesh.material.opacity = opacity
  mesh.visible = opacity > 0.01
}

function animateQiu(root, dt, h) {
  const p = root.userData.parts || {}
  // 平面内顺时针自转
  root.userData.spinSpeed = damp(root.userData.spinSpeed, 1.05 * h, 3.2, dt)
  root.userData.angle += root.userData.spinSpeed * dt
  if (p.ringSpin && p.ringSpinAxis) {
    p.ringSpin.setRotationFromAxisAngle(p.ringSpinAxis, -root.userData.angle)
  }
  // 整体绕球轻摆，限幅避免穿模
  if (p.ringSwing) {
    const t = clock.elapsedTime
    const amp = 0.1 * h
    p.ringSwing.rotation.x = Math.sin(t * 0.7) * amp
    p.ringSwing.rotation.z = Math.cos(t * 0.55) * amp * 0.7
    p.ringSwing.rotation.y = Math.sin(t * 0.35) * amp * 0.25
  }

  root.userData.pulse = damp(root.userData.pulse, h, 2.5, dt)
  const breath = 0.55 + Math.sin(clock.elapsedTime * 1.4) * 0.2
  const coreI = root.userData.pulse * breath
  setGlowOpacity(p.coreGlow, coreI * 0.85)
  if (p.body) {
    applyPartEmissive(p.body, 0x7a2dff, 0.15 + coreI * 1.1)
  }
  // 外环轻微微光，hover 时略增强
  if (p.ring) {
    applyPartEmissive(p.ring, 0xd8c4ff, 0.22 + h * 0.55)
  }
}

function animateHuan(root, dt, h) {
  const p = root.userData.parts || {}
  root.userData.spinSpeed = damp(root.userData.spinSpeed, 1.15 * h, 3.0, dt)
  root.userData.spinSpeedB = damp(root.userData.spinSpeedB, -0.85 * h, 3.0, dt)
  root.userData.angle += root.userData.spinSpeed * dt
  root.userData.angleB += root.userData.spinSpeedB * dt

  if (p.ringPivotA) p.ringPivotA.rotation.y = root.userData.angle
  if (p.ringPivotB) p.ringPivotB.rotation.y = root.userData.angleB

  const flow = h * (0.45 + Math.sin(clock.elapsedTime * 3.2) * 0.15)
  setGlowOpacity(p.flowA, flow)
  setGlowOpacity(p.flowB, flow * 0.85)
  if (p.flowA) p.flowA.rotation.y += dt * 2.5 * h
  if (p.flowB) p.flowB.rotation.y -= dt * 2.1 * h

  if (p.ringA) applyPartEmissive(p.ringA, 0xffc8a0, 0.05 + h * (0.55 + Math.sin(clock.elapsedTime * 2.1) * 0.12))
  if (p.ringB) applyPartEmissive(p.ringB, 0xff9ad0, 0.05 + h * (0.45 + Math.sin(clock.elapsedTime * 2.4 + 1) * 0.1))
  if (p.pillar) applyPartEmissive(p.pillar, 0xffe6a8, 0.08 + h * 0.7)
}

function animateJiqi(root, dt, h) {
  const p = root.userData.parts || {}
  root.userData.spinSpeed = damp(root.userData.spinSpeed, 8.5 * h, 2.8, dt)
  root.userData.angle += root.userData.spinSpeed * dt
  // 每帧按头顶轴设绝对角，桨保持在头顶平面转，不会甩到侧面
  if (p.propPivot && p.propShaftLocal) {
    p.propPivot.setRotationFromAxisAngle(p.propShaftLocal, root.userData.angle)
  }

  const screenOp = h * 0.7
  const eyePulse = 0.75 + Math.sin(clock.elapsedTime * 2.2) * 0.2
  setGlowOpacity(p.screenGlow, screenOp)
  setGlowOpacity(p.eyeL, h * eyePulse)
  setGlowOpacity(p.eyeR, h * eyePulse)

  if (p.body) {
    applyPartEmissive(p.body, 0x2a4060, 0.04 + h * 0.18)
  }
}

function animateOne(key, dt) {
  const root = models[key]
  if (!root) return
  const target = hoverKey === key ? 1 : 0
  root.userData.hover = damp(root.userData.hover, target, 6.5, dt)
  const h = root.userData.hover

  root.userData.scaleMul = damp(root.userData.scaleMul, lerp(1, 1.025, h), 7, dt)
  // 机器人不上浮，避免螺旋桨看起来在晃；其它模型轻微抬起
  const liftTarget = key === 'jiqi' ? 0 : 0.02 * h
  root.userData.lift = damp(root.userData.lift, liftTarget, 7, dt)
  const layoutScale = root.userData.layoutScale ?? 1
  root.scale.setScalar(root.userData.baseScale * layoutScale * root.userData.scaleMul)
  root.position.y = root.userData.baseY + root.userData.lift

  if (key === 'qiu') animateQiu(root, dt, h)
  else if (key === 'huan') animateHuan(root, dt, h)
  else if (key === 'jiqi') animateJiqi(root, dt, h)

  // 非 hover 时把零件 emissive 平滑收回（球环保留微光，不在此重置）
  if (h < 0.02) {
    const p = root.userData.parts || {}
    ;[p.body, p.ringA, p.ringB, p.pillar].forEach((m) => resetPartEmissive(m, dt))
  }

  if (root.userData.shadow?.material) {
    const baseOp = key === 'jiqi' ? 0.28 : 0.42
    root.userData.shadow.material.opacity = lerp(baseOp, baseOp * 0.75, h)
    root.userData.shadow.scale.setScalar(lerp(1, 0.92, h) * (key === 'jiqi' ? 0.85 : 1))
  }
}

function syncBloomSelection() {
  if (!bloomEffect) return
  bloomEffect.selection.clear()
  bloomTargets.forEach((obj) => {
    if (!obj) return
    const op = obj.material?.opacity
    if (typeof op === 'number' && op < 0.05 && obj.userData.isGlow) return
    bloomEffect.selection.add(obj)
  })
  const anyHover = Object.values(models).some((m) => (m?.userData?.hover || 0) > 0.02)
  bloomEffect.intensity = anyHover ? 1.35 : 0.15
}

function tick() {
  if (disposed || !renderer || !scene || !camera) return
  rafId = requestAnimationFrame(tick)
  const dt = Math.min(0.05, clock.getDelta())
  animateOne('qiu', dt)
  animateOne('huan', dt)
  animateOne('jiqi', dt)
  syncBloomSelection()
  if (composer) composer.render(dt)
  else renderer.render(scene, camera)
}

async function init() {
  const host = rootRef.value
  const canvas = canvasRef.value
  if (!host || !canvas) return
  disposed = false

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(30, 1, 0.05, 40)
  camera.position.set(0, 0.95, 3.85)
  camera.lookAt(0, -0.78, 0.85)

  renderer = new THREE.WebGLRenderer({
    canvas,
    alpha: true,
    antialias: true,
    powerPreference: 'high-performance',
  })
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.08
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap

  setupLights()

  composer = new EffectComposer(renderer)
  bloomEffect = new SelectiveBloomEffect(scene, camera, {
    intensity: 0.2,
    luminanceThreshold: 0.15,
    luminanceSmoothing: 0.35,
    mipmapBlur: true,
    radius: 0.55,
  })
  bloomEffect.ignoreBackground = true
  composer.addPass(new RenderPass(scene, camera))
  composer.addPass(new EffectPass(camera, bloomEffect))

  raycaster = new THREE.Raycaster()
  pointer = new THREE.Vector2()
  setSize()
  resizeObserver = new ResizeObserver(() => setSize())
  resizeObserver.observe(host)
  canvas.addEventListener('pointermove', onPointerMove, { passive: true })
  canvas.addEventListener('pointerleave', onPointerLeave)

  const map = await loadAll()
  if (disposed) return
  placeModels(map)
  bloomTargets.forEach((t) => bloomEffect.selection.add(t))
  clock.start()
  tick()
}

function disposeObject(obj) {
  obj.traverse((child) => {
    if (child.geometry) child.geometry.dispose()
    const mats = child.material
      ? (Array.isArray(child.material) ? child.material : [child.material])
      : []
    mats.forEach((m) => m?.dispose?.())
  })
}

function teardown() {
  disposed = true
  cancelAnimationFrame(rafId)
  rafId = 0
  hoverKey = null
  hitProxies.length = 0
  bloomTargets.length = 0
  canvasRef.value?.removeEventListener('pointermove', onPointerMove)
  canvasRef.value?.removeEventListener('pointerleave', onPointerLeave)
  resizeObserver?.disconnect()
  resizeObserver = null
  Object.keys(models).forEach((k) => {
    const m = models[k]
    if (m) {
      scene?.remove(m)
      disposeObject(m)
      models[k] = null
    }
  })
  if (deskPlane) {
    scene?.remove(deskPlane)
    deskPlane.geometry?.dispose()
    deskPlane.material?.dispose()
    deskPlane = null
  }
  composer?.dispose()
  composer = null
  bloomEffect = null
  dracoLoader?.dispose()
  dracoLoader = null
  renderer?.dispose()
  renderer = null
  scene = null
  camera = null
}

onMounted(() => {
  init().catch((err) => {
    // eslint-disable-next-line no-console
    console.error('[DoorGuestScene] init failed', err)
  })
})

onUnmounted(() => {
  teardown()
})
