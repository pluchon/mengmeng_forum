import { Camera, Mesh, Plane, Program, Renderer, Texture, Transform } from 'ogl'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  atlasUrl: {
    type: String,
    required: true,
  },
  atlasColumns: {
    type: Number,
    default: 8,
  },
  atlasRows: {
    type: Number,
    default: 9,
  },
  bend: {
    type: Number,
    default: 2.2,
  },
  textColor: {
    type: String,
    default: '#57465c',
  },
  borderRadius: {
    type: Number,
    default: 0.075,
  },
  scrollSpeed: {
    type: Number,
    default: 2,
  },
  scrollEase: {
    type: Number,
    default: 0.055,
  },
  autoPlayMs: {
    type: Number,
    default: 4200,
  },
})

const containerRef = ref(null)
const failed = ref(false)

let galleryApp = null
let rebuildFrame = 0

function lerp(from, to, amount) {
  return from + (to - from) * amount
}

function hexToRgb(value, fallback = [0.76, 0.65, 0.8]) {
  const hex = String(value || '').replace('#', '').trim()
  if (!/^[0-9a-f]{6}$/i.test(hex)) return fallback
  return [0, 2, 4].map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16) / 255)
}

function createTextTexture(gl, text, color) {
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  if (!context) throw new Error('CircularGallery 2D context unavailable')

  const fontSize = 24
  const font = `600 ${fontSize}px "Microsoft JhengHei", "PingFang TC", sans-serif`
  context.font = font
  const width = Math.ceil(context.measureText(text).width) + 28
  const height = fontSize + 22
  canvas.width = width * pixelRatio
  canvas.height = height * pixelRatio
  context.scale(pixelRatio, pixelRatio)
  context.clearRect(0, 0, width, height)
  context.font = font
  context.fillStyle = color
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(text, width / 2, height / 2)

  const texture = new Texture(gl, { generateMipmaps: false })
  texture.image = canvas
  return { texture, width, height }
}

class GalleryTitle {
  constructor({ gl, parent, text, color }) {
    const { texture, width, height } = createTextTexture(gl, text, color)
    const geometry = new Plane(gl)
    const program = new Program(gl, {
      vertex: `
        attribute vec3 position;
        attribute vec2 uv;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform sampler2D tMap;
        varying vec2 vUv;
        void main() {
          vec4 color = texture2D(tMap, vUv);
          if (color.a < 0.08) discard;
          gl_FragColor = color;
        }
      `,
      uniforms: { tMap: { value: texture } },
      transparent: true,
      depthTest: false,
      depthWrite: false,
    })
    this.mesh = new Mesh(gl, { geometry, program })
    this.mesh.scale.set((width / height) * 0.17, 0.17, 1)
    this.mesh.position.y = -0.64
    this.mesh.position.z = 0.02
    this.mesh.setParent(parent)
  }
}

class GalleryMedia {
  constructor({
    geometry,
    gl,
    index,
    item,
    length,
    scene,
    screen,
    texture,
    viewport,
    atlasColumns,
    atlasRows,
    bend,
    borderRadius,
    textColor,
    reducedMotion,
  }) {
    this.extra = 0
    this.geometry = geometry
    this.gl = gl
    this.index = index
    this.item = item
    this.length = length
    this.scene = scene
    this.screen = screen
    this.viewport = viewport
    this.bend = bend
    this.reducedMotion = reducedMotion
    this.speed = 0

    const tone = hexToRgb(item.tone)
    this.program = new Program(gl, {
      vertex: `
        precision highp float;
        attribute vec3 position;
        attribute vec2 uv;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        uniform float uTime;
        uniform float uSpeed;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          vec3 point = position;
          point.z += sin(point.x * 3.2 + uTime) * 0.08 * min(abs(uSpeed) * 3.0, 1.0);
          gl_Position = projectionMatrix * modelViewMatrix * vec4(point, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform sampler2D tMap;
        uniform float uColumns;
        uniform float uRows;
        uniform float uFrame;
        uniform float uRow;
        uniform float uBorderRadius;
        uniform vec3 uTone;
        varying vec2 vUv;

        float roundedBox(vec2 point, vec2 bounds, float radius) {
          vec2 delta = abs(point) - bounds + radius;
          return min(max(delta.x, delta.y), 0.0) + length(max(delta, 0.0)) - radius;
        }

        void main() {
          float distance = roundedBox(vUv - 0.5, vec2(0.5), uBorderRadius);
          float mask = 1.0 - smoothstep(-0.004, 0.004, distance);
          float innerDistance = roundedBox(vUv - 0.5, vec2(0.488), max(0.0, uBorderRadius - 0.012));
          float innerMask = 1.0 - smoothstep(-0.004, 0.004, innerDistance);
          float border = max(0.0, mask - innerMask);

          vec2 atlasUv = vec2(
            (floor(uFrame) + vUv.x) / uColumns,
            (uRows - uRow - 1.0 + vUv.y) / uRows
          );
          vec4 sprite = texture2D(tMap, atlasUv);
          vec3 paper = mix(vec3(1.0, 0.988, 0.996), uTone, 0.12 + vUv.y * 0.05);
          vec3 color = mix(paper, sprite.rgb, sprite.a);
          color = mix(color, uTone, border * 0.32);
          gl_FragColor = vec4(color, mask);
        }
      `,
      uniforms: {
        tMap: { value: texture },
        uColumns: { value: atlasColumns },
        uRows: { value: atlasRows },
        uFrame: { value: 0 },
        uRow: { value: Number(item.row) || 0 },
        uBorderRadius: { value: borderRadius },
        uTone: { value: tone },
        uSpeed: { value: 0 },
        uTime: { value: index * 0.37 },
      },
      transparent: true,
      depthTest: false,
      depthWrite: false,
    })
    this.plane = new Mesh(gl, { geometry, program: this.program })
    this.plane.setParent(scene)
    this.title = new GalleryTitle({ gl, parent: this.plane, text: item.text, color: textColor })
    this.onResize({ screen, viewport })
  }

  onResize({ screen, viewport }) {
    this.screen = screen || this.screen
    this.viewport = viewport || this.viewport
    const cardHeight = this.viewport.height * 0.53
    this.plane.scale.y = cardHeight
    this.plane.scale.x = cardHeight * 0.76
    this.padding = this.plane.scale.x * 0.16
    this.width = this.plane.scale.x + this.padding
    this.widthTotal = this.width * this.length
    this.x = this.width * this.index
  }

  update(scroll, direction, elapsedMs) {
    this.plane.position.x = this.x - scroll.current - this.extra
    const x = this.plane.position.x
    const halfViewport = this.viewport.width / 2

    if (this.bend === 0) {
      this.plane.position.y = 0.34
      this.plane.rotation.z = 0
    }
    else {
      const bend = Math.max(0.001, Math.abs(this.bend))
      const radius = (halfViewport * halfViewport + bend * bend) / (2 * bend)
      const effectiveX = Math.min(Math.abs(x), halfViewport)
      const arc = radius - Math.sqrt(Math.max(0, radius * radius - effectiveX * effectiveX))
      this.plane.position.y = this.bend > 0 ? 0.55 - arc : 0.25 + arc
      this.plane.rotation.z = this.bend > 0
        ? -Math.sign(x) * Math.asin(effectiveX / radius)
        : Math.sign(x) * Math.asin(effectiveX / radius)
    }

    this.speed = scroll.current - scroll.last
    this.program.uniforms.uSpeed.value = this.speed
    if (!this.reducedMotion) {
      this.program.uniforms.uTime.value += 0.035
      const frameCount = Math.max(1, Number(this.item.frames) || 1)
      const fps = Math.max(1, Number(this.item.fps) || 6)
      this.program.uniforms.uFrame.value = Math.floor(elapsedMs * fps / 1000) % frameCount
    }

    const planeOffset = this.plane.scale.x / 2
    const viewportOffset = this.viewport.width / 2
    const before = this.plane.position.x + planeOffset < -viewportOffset
    const after = this.plane.position.x - planeOffset > viewportOffset
    if (direction === 'right' && before) this.extra -= this.widthTotal
    if (direction === 'left' && after) this.extra += this.widthTotal
  }
}

class CircularGalleryApp {
  constructor(container, config) {
    this.container = container
    this.config = config
    this.scroll = { ease: config.scrollEase, current: 0, target: 0, last: 0 }
    this.isDown = false
    this.isHovering = false
    this.pointerStart = 0
    this.pointerScrollStart = 0
    this.raf = 0
    this.startedAt = performance.now()
    this.nextAutoAt = this.startedAt + config.autoPlayMs
    this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    this.createRenderer()
    this.createCamera()
    this.scene = new Transform()
    this.geometry = new Plane(this.gl, { widthSegments: 80, heightSegments: 48 })
    this.createTexture()
    this.onResize()
    this.createMedias()
    this.addEventListeners()
    this.update(this.startedAt)
  }

  createRenderer() {
    this.renderer = new Renderer({
      alpha: true,
      antialias: true,
      dpr: Math.min(window.devicePixelRatio || 1, 2),
    })
    this.gl = this.renderer.gl
    this.gl.clearColor(0, 0, 0, 0)
    this.container.appendChild(this.gl.canvas)
  }

  createCamera() {
    this.camera = new Camera(this.gl)
    this.camera.fov = 45
    this.camera.position.z = 20
  }

  createTexture() {
    this.texture = new Texture(this.gl, { generateMipmaps: false })
    const image = new Image()
    image.onload = () => {
      this.texture.image = image
    }
    image.onerror = () => {
      failed.value = true
    }
    image.src = this.config.atlasUrl
  }

  createMedias() {
    const sourceItems = this.config.items.length ? this.config.items : []
    const items = sourceItems.concat(sourceItems)
    this.medias = items.map((item, index) => new GalleryMedia({
      geometry: this.geometry,
      gl: this.gl,
      index,
      item,
      length: items.length,
      scene: this.scene,
      screen: this.screen,
      texture: this.texture,
      viewport: this.viewport,
      atlasColumns: this.config.atlasColumns,
      atlasRows: this.config.atlasRows,
      bend: this.config.bend,
      borderRadius: this.config.borderRadius,
      textColor: this.config.textColor,
      reducedMotion: this.reducedMotion,
    }))
  }

  onResize() {
    const width = Math.max(1, this.container.clientWidth)
    const height = Math.max(1, this.container.clientHeight)
    this.screen = { width, height }
    this.renderer.setSize(width, height)
    this.camera.perspective({ aspect: width / height })
    const fov = this.camera.fov * Math.PI / 180
    const viewportHeight = 2 * Math.tan(fov / 2) * this.camera.position.z
    this.viewport = { width: viewportHeight * this.camera.aspect, height: viewportHeight }
    this.medias?.forEach((media) => media.onResize({ screen: this.screen, viewport: this.viewport }))
  }

  postponeAutoPlay() {
    this.nextAutoAt = performance.now() + this.config.autoPlayMs
  }

  onPointerDown(event) {
    this.isDown = true
    this.pointerStart = event.clientX
    this.pointerScrollStart = this.scroll.current
    this.container.setPointerCapture?.(event.pointerId)
    this.postponeAutoPlay()
  }

  onPointerMove(event) {
    if (!this.isDown) return
    const distance = (this.pointerStart - event.clientX) * (this.config.scrollSpeed * 0.018)
    this.scroll.target = this.pointerScrollStart + distance
  }

  onPointerUp(event) {
    if (!this.isDown) return
    this.isDown = false
    if (this.container.hasPointerCapture?.(event.pointerId)) {
      this.container.releasePointerCapture(event.pointerId)
    }
    this.snapToCard()
    this.postponeAutoPlay()
  }

  onWheel(event) {
    if (Math.abs(event.deltaX) <= Math.abs(event.deltaY) && !event.shiftKey) return
    event.preventDefault()
    const delta = event.deltaX || event.deltaY
    this.scroll.target += Math.sign(delta) * this.config.scrollSpeed * 0.9
    this.snapToCard()
    this.postponeAutoPlay()
  }

  snapToCard() {
    const width = this.medias?.[0]?.width
    if (!width) return
    this.scroll.target = Math.round(this.scroll.target / width) * width
  }

  update(timestamp) {
    if (!this.reducedMotion && !this.isDown && !this.isHovering && timestamp >= this.nextAutoAt) {
      const width = this.medias?.[0]?.width
      if (width) this.scroll.target += width
      this.nextAutoAt = timestamp + this.config.autoPlayMs
    }
    this.scroll.current = lerp(this.scroll.current, this.scroll.target, this.scroll.ease)
    const direction = this.scroll.current > this.scroll.last ? 'right' : 'left'
    const elapsedMs = timestamp - this.startedAt
    this.medias.forEach((media) => media.update(this.scroll, direction, elapsedMs))
    this.renderer.render({ scene: this.scene, camera: this.camera })
    this.scroll.last = this.scroll.current
    this.raf = requestAnimationFrame((nextTimestamp) => this.update(nextTimestamp))
  }

  addEventListeners() {
    this.onResizeBound = () => this.onResize()
    this.onPointerDownBound = (event) => this.onPointerDown(event)
    this.onPointerMoveBound = (event) => this.onPointerMove(event)
    this.onPointerUpBound = (event) => this.onPointerUp(event)
    this.onWheelBound = (event) => this.onWheel(event)
    this.onPointerEnterBound = () => {
      this.isHovering = true
    }
    this.onPointerLeaveBound = (event) => {
      this.isHovering = false
      this.onPointerUp(event)
      this.postponeAutoPlay()
    }
    this.resizeObserver = new ResizeObserver(this.onResizeBound)
    this.resizeObserver.observe(this.container)
    this.container.addEventListener('pointerdown', this.onPointerDownBound)
    this.container.addEventListener('pointermove', this.onPointerMoveBound)
    this.container.addEventListener('pointerup', this.onPointerUpBound)
    this.container.addEventListener('pointercancel', this.onPointerUpBound)
    this.container.addEventListener('pointerenter', this.onPointerEnterBound)
    this.container.addEventListener('pointerleave', this.onPointerLeaveBound)
    this.container.addEventListener('wheel', this.onWheelBound, { passive: false })
  }

  destroy() {
    cancelAnimationFrame(this.raf)
    this.resizeObserver?.disconnect()
    this.container.removeEventListener('pointerdown', this.onPointerDownBound)
    this.container.removeEventListener('pointermove', this.onPointerMoveBound)
    this.container.removeEventListener('pointerup', this.onPointerUpBound)
    this.container.removeEventListener('pointercancel', this.onPointerUpBound)
    this.container.removeEventListener('pointerenter', this.onPointerEnterBound)
    this.container.removeEventListener('pointerleave', this.onPointerLeaveBound)
    this.container.removeEventListener('wheel', this.onWheelBound)
    this.gl?.canvas?.remove()
  }
}

function fallbackStyle(item) {
  const row = Math.max(0, Number(item.row) || 0)
  const y = props.atlasRows > 1 ? row * 100 / (props.atlasRows - 1) : 0
  return {
    backgroundImage: `url("${props.atlasUrl}")`,
    backgroundSize: `${props.atlasColumns * 100}% ${props.atlasRows * 100}%`,
    backgroundPosition: `0% ${y}%`,
  }
}

function destroyGallery() {
  if (rebuildFrame) {
    cancelAnimationFrame(rebuildFrame)
    rebuildFrame = 0
  }
  galleryApp?.destroy()
  galleryApp = null
}

function buildGallery() {
  destroyGallery()
  failed.value = false
  if (!containerRef.value || !props.items.length) return
  rebuildFrame = requestAnimationFrame(() => {
    rebuildFrame = 0
    try {
      galleryApp = new CircularGalleryApp(containerRef.value, {
        items: props.items,
        atlasUrl: props.atlasUrl,
        atlasColumns: props.atlasColumns,
        atlasRows: props.atlasRows,
        bend: props.bend,
        textColor: props.textColor,
        borderRadius: props.borderRadius,
        scrollSpeed: props.scrollSpeed,
        scrollEase: props.scrollEase,
        autoPlayMs: props.autoPlayMs,
      })
    }
    catch {
      containerRef.value?.replaceChildren()
      failed.value = true
    }
  })
}

onMounted(buildGallery)

watch(
  () => [
    props.items,
    props.atlasUrl,
    props.atlasColumns,
    props.atlasRows,
    props.bend,
    props.textColor,
    props.borderRadius,
    props.scrollSpeed,
    props.scrollEase,
    props.autoPlayMs,
  ],
  buildGallery,
  { deep: true },
)

onBeforeUnmount(destroyGallery)
