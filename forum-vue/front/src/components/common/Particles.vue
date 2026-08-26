<template>
  <div
    ref="containerRef"
    :class="['particles-wrap', className]"
  />
</template>

<script setup>
import {
  Camera,
  Geometry,
  Mesh,
  Program,
  Renderer,
} from 'ogl'
import {
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

const props = defineProps({
  particleCount: { type: Number, default: 200 },
  particleSpread: { type: Number, default: 10 },
  speed: { type: Number, default: 0.1 },
  particleColors: {
    type: Array,
    default: () => ['#ffffff', '#ffffff', '#ffffff'],
  },
  moveParticlesOnHover: { type: Boolean, default: false },
  particleHoverFactor: { type: Number, default: 1 },
  alphaParticles: { type: Boolean, default: false },
  particleBaseSize: { type: Number, default: 100 },
  sizeRandomness: { type: Number, default: 1 },
  cameraDistance: { type: Number, default: 20 },
  disableRotation: { type: Boolean, default: false },
  pixelRatio: { type: Number, default: 1 },
  className: { type: String, default: '' },
})

const containerRef = ref(null)
let cleanup = null

const hexToRgb = (hex) => {
  let normalized = String(hex || '#ffffff').replace(/^#/, '')
  if (normalized.length === 3) {
    normalized = normalized
      .split('')
      .map((char) => char + char)
      .join('')
  }
  const intValue = parseInt(normalized, 16) || 0
  const r = ((intValue >> 16) & 255) / 255
  const g = ((intValue >> 8) & 255) / 255
  const b = (intValue & 255) / 255
  return [r, g, b]
}

const vertex = /* glsl */ `
  attribute vec3 position;
  attribute vec4 random;
  attribute vec3 color;
  
  uniform mat4 modelMatrix;
  uniform mat4 viewMatrix;
  uniform mat4 projectionMatrix;
  uniform float uTime;
  uniform float uSpread;
  uniform float uBaseSize;
  uniform float uSizeRandomness;
  
  varying vec4 vRandom;
  varying vec3 vColor;
  
  void main() {
    vRandom = random;
    vColor = color;
    
    vec3 pos = position * uSpread;
    pos.z *= 10.0;
    
    vec4 mPos = modelMatrix * vec4(pos, 1.0);
    float t = uTime;
    mPos.x += sin(t * random.z + 6.28 * random.w) * mix(0.1, 1.5, random.x);
    mPos.y += sin(t * random.y + 6.28 * random.x) * mix(0.1, 1.5, random.w);
    mPos.z += sin(t * random.w + 6.28 * random.y) * mix(0.1, 1.5, random.z);
    
    vec4 mvPos = viewMatrix * mPos;
    if (uSizeRandomness == 0.0) {
      gl_PointSize = uBaseSize;
    } else {
      gl_PointSize = (uBaseSize * (1.0 + uSizeRandomness * (random.x - 0.5))) / length(mvPos.xyz);
    }
    
    gl_Position = projectionMatrix * mvPos;
  }
`

const fragment = /* glsl */ `
  precision highp float;
  
  uniform float uTime;
  uniform float uAlphaParticles;
  varying vec4 vRandom;
  varying vec3 vColor;
  
  void main() {
    vec2 uv = gl_PointCoord.xy;
    float d = length(uv - vec2(0.5));
    
    if(uAlphaParticles < 0.5) {
      if(d > 0.5) {
        discard;
      }
      gl_FragColor = vec4(vColor + 0.2 * sin(uv.yxx + uTime + vRandom.y * 6.28), 1.0);
    } else {
      float circle = smoothstep(0.5, 0.4, d) * 0.8;
      gl_FragColor = vec4(vColor + 0.2 * sin(uv.yxx + uTime + vRandom.y * 6.28), circle);
    }
  }
`

const initParticles = () => {
  const container = containerRef.value
  if (!container) return

  cleanup?.()

  const renderer = new Renderer({ dpr: props.pixelRatio, depth: false, alpha: true })
  const gl = renderer.gl
  const program = new Program(gl, {
    vertex,
    fragment,
    uniforms: {
      uTime: { value: 0 },
      uSpread: { value: props.particleSpread },
      uBaseSize: { value: props.particleBaseSize * props.pixelRatio },
      uSizeRandomness: { value: props.sizeRandomness },
      uAlphaParticles: { value: props.alphaParticles ? 1 : 0 },
    },
    transparent: true,
    depthTest: false,
  })

  const camera = new Camera(gl, { fov: 15 })
  camera.position.set(0, 0, props.cameraDistance)

  const resize = () => {
    const width = container.clientWidth
    const height = container.clientHeight
    renderer.setSize(width, height)
    camera.perspective({ aspect: gl.canvas.width / gl.canvas.height })
  }

  window.addEventListener('resize', resize, false)
  resize()

  container.appendChild(gl.canvas)

  const mouseRef = { x: 0, y: 0 }
  const handleMouseMove = (event) => {
    const rect = container.getBoundingClientRect()
    if (!rect.width || !rect.height) {
      mouseRef.x = 0
      mouseRef.y = 0
      return
    }
    const x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    const y = -(((event.clientY - rect.top) / rect.height) * 2 - 1)
    mouseRef.x = x
    mouseRef.y = y
  }

  if (props.moveParticlesOnHover) {
    window.addEventListener('mousemove', handleMouseMove, false)
  }

  const count = Math.max(1, Number(props.particleCount) || 200)
  const positions = new Float32Array(count * 3)
  const randoms = new Float32Array(count * 4)
  const colors = new Float32Array(count * 3)
  const palette = Array.isArray(props.particleColors) ? props.particleColors : ['#ffffff']

  for (let i = 0; i < count; i++) {
    let x = 0
    let y = 0
    let z = 0
    let len = 0
    do {
      x = Math.random() * 2 - 1
      y = Math.random() * 2 - 1
      z = Math.random() * 2 - 1
      len = x * x + y * y + z * z
    } while (len > 1 || len === 0)

    const r = Math.cbrt(Math.random())
    positions.set([x * r, y * r, z * r], i * 3)
    randoms.set([Math.random(), Math.random(), Math.random(), Math.random()], i * 4)

    const color = hexToRgb(palette[Math.floor(Math.random() * palette.length)])
    colors.set(color, i * 3)
  }

  const geometry = new Geometry(gl, {
    position: { size: 3, data: positions },
    random: { size: 4, data: randoms },
    color: { size: 3, data: colors },
  })

  const particles = new Mesh(gl, { mode: gl.POINTS, geometry, program })
  let frameId = 0
  let lastTime = performance.now()
  let elapsed = 0

  const update = (time) => {
    frameId = requestAnimationFrame(update)
    const delta = time - lastTime
    lastTime = time
    elapsed += delta * Number(props.speed || 0.1)
    program.uniforms.uTime.value = elapsed * 0.001

    if (props.moveParticlesOnHover) {
      particles.position.x = -mouseRef.x * Number(props.particleHoverFactor || 1)
      particles.position.y = -mouseRef.y * Number(props.particleHoverFactor || 1)
    } else {
      particles.position.x = 0
      particles.position.y = 0
    }

    if (!props.disableRotation) {
      particles.rotation.x = Math.sin(elapsed * 0.0002) * 0.1
      particles.rotation.y = Math.cos(elapsed * 0.0005) * 0.15
      particles.rotation.z += 0.01 * Number(props.speed || 0.1)
    }
    renderer.render({ scene: particles, camera })
  }

  frameId = requestAnimationFrame(update)

  cleanup = () => {
    window.removeEventListener('resize', resize)
    if (props.moveParticlesOnHover) {
      window.removeEventListener('mousemove', handleMouseMove)
    }
    cancelAnimationFrame(frameId)
    if (container.contains(gl.canvas)) {
      container.removeChild(gl.canvas)
    }
    gl.clear(gl.COLOR_BUFFER_BIT)
  }
}

onMounted(() => {
  initParticles()
})

onBeforeUnmount(() => {
  cleanup?.()
})

watch(
  () => props,
  () => {
    initParticles()
  },
  { deep: true },
)
</script>

<style scoped>
.particles-wrap {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.particles-wrap canvas {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
