/**
 * 认证页背景：对齐智慧树 wallbgcanvas（src/sea/wallbgcanvas.js）
 * 使用现代 three.js：BufferGeometry + Points + WebGLRenderer（原仓库为旧版 Particle + CanvasRenderer）
 */
export async function createAuthWallThree(canvas) {
  const THREE = await import('three')

  const SEPARATION = 118
  const PAD_CELLS = 34
  const MIN_CELLS = 72
  const MAX_CELLS = 120

  let animationId = null
  let count = 0

  const windowHalfX = () => window.innerWidth / 2
  const windowHalfY = () => window.innerHeight / 2

  let mouseX = 0
  let mouseY = 0

  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(
    75,
    window.innerWidth / Math.max(1, window.innerHeight),
    1,
    10000,
  )
  camera.position.z = 1550

  let amountX = 0
  let amountY = 0
  let positions = new Float32Array(0)
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

  const material = new THREE.PointsMaterial({
    color: 0x6b7a8f,
    size: 5.2,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.95,
    depthWrite: false,
  })

  const points = new THREE.Points(geometry, material)
  scene.add(points)

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setClearColor(0xd9dee6, 1)
  renderer.setSize(window.innerWidth, window.innerHeight, false)

  function computeGrid(w, h) {
    const baseX = Math.ceil(w / SEPARATION) + PAD_CELLS
    const baseY = Math.ceil(h / SEPARATION) + PAD_CELLS
    return {
      x: Math.max(MIN_CELLS, Math.min(MAX_CELLS, baseX)),
      y: Math.max(MIN_CELLS, Math.min(MAX_CELLS, baseY)),
    }
  }

  function ensureGeometry(w, h) {
    const grid = computeGrid(w, h)
    if (grid.x === amountX && grid.y === amountY) return
    amountX = grid.x
    amountY = grid.y
    positions = new Float32Array(amountX * amountY * 3)
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  }

  function onMouseMove(event) {
    mouseX = event.clientX - windowHalfX()
    mouseY = event.clientY - windowHalfY()
  }

  function onResize() {
    const w = window.innerWidth
    const h = Math.max(1, window.innerHeight)
    ensureGeometry(w, h)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h, false)
  }

  function animate() {
    animationId = requestAnimationFrame(animate)

    camera.position.x += (mouseX - camera.position.x) * 0.05
    camera.position.y += (-mouseY - camera.position.y) * 0.05
    camera.lookAt(0, 0, 0)

    let i = 0
    for (let ix = 0; ix < amountX; ix++) {
      for (let iy = 0; iy < amountY; iy++) {
        positions[i] = ix * SEPARATION - (amountX * SEPARATION) / 2
        positions[i + 1] =
          Math.sin((ix + count) * 0.3) * 50 + Math.sin((iy + count) * 0.5) * 50
        positions[i + 2] = iy * SEPARATION - (amountY * SEPARATION) / 2
        i += 3
      }
    }
    geometry.attributes.position.needsUpdate = true

    renderer.render(scene, camera)
    count += 0.08
  }

  window.addEventListener('mousemove', onMouseMove, false)
  window.addEventListener('resize', onResize, false)
  onResize()
  animate()

  function stop() {
    if (animationId != null) {
      cancelAnimationFrame(animationId)
      animationId = null
    }
    window.removeEventListener('mousemove', onMouseMove, false)
    window.removeEventListener('resize', onResize, false)
    geometry.dispose()
    material.dispose()
    renderer.dispose()
    scene.clear()
  }

  return { stop }
}
