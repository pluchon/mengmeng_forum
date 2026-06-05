import { computed, onMounted, onUnmounted, ref, watch, nextTick, unref } from 'vue'
import { useRoute } from 'vue-router'
import { createAuthWallThree } from '@scripts/components/common/AuthWallThree'

/** 非认证页：原有粉色连线粒子（独立 2D canvas） */
function useClassicParticles(canvasRef, getSize) {
  let animationFrame = null
  let ctx = null
  let particles = []
  const particleCount = 100
  const connectionDistance = 150
  const mouse = { x: null, y: null, radius: 150 }

  class Particle {
    constructor(w, h) {
      this.w = w
      this.h = h
      this.x = Math.random() * w
      this.y = Math.random() * h
      this.size = Math.random() * 2 + 1
      this.speedX = Math.random() * 1 - 0.5
      this.speedY = Math.random() * 1 - 0.5
      this.color = 'rgba(245, 69, 104, 0.55)'
    }

    update() {
      this.x += this.speedX
      this.y += this.speedY

      if (this.x > this.w || this.x < 0) this.speedX = -this.speedX
      if (this.y > this.h || this.y < 0) this.speedY = -this.speedY

      if (mouse.x != null) {
        const dx = mouse.x - this.x
        const dy = mouse.y - this.y
        const distance = Math.sqrt(dx * dx + dy * dy)
        if (distance < mouse.radius) {
          const forceDirectionX = dx / distance
          const forceDirectionY = dy / distance
          const force = (mouse.radius - distance) / mouse.radius
          const directionX = forceDirectionX * force * 5
          const directionY = forceDirectionY * force * 5
          this.x -= directionX
          this.y -= directionY
        }
      }
    }

    draw() {
      ctx.fillStyle = this.color
      ctx.beginPath()
      ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  function init(w, h) {
    particles = []
    for (let i = 0; i < particleCount; i++) {
      particles.push(new Particle(w, h))
    }
  }

  function connect() {
    for (let a = 0; a < particles.length; a++) {
      for (let b = a; b < particles.length; b++) {
        const dx = particles[a].x - particles[b].x
        const dy = particles[a].y - particles[b].y
        const distance = Math.sqrt(dx * dx + dy * dy)

        if (distance < connectionDistance) {
          const opacity = 1 - distance / connectionDistance
          ctx.strokeStyle = `rgba(245, 69, 104, ${opacity * 0.42})`
          ctx.lineWidth = 1
          ctx.beginPath()
          ctx.moveTo(particles[a].x, particles[a].y)
          ctx.lineTo(particles[b].x, particles[b].y)
          ctx.stroke()
        }
      }
    }
  }

  function animateFrame() {
    if (!canvasRef.value) return
    const { width: w, height: h } = canvasRef.value
    ctx.clearRect(0, 0, w, h)
    particles.forEach((p) => {
      p.update()
      p.draw()
    })
    connect()
    animationFrame = requestAnimationFrame(animateFrame)
  }

  function handleResize() {
    if (!canvasRef.value) return
    const { w, h } = getSize()
    canvasRef.value.width = w
    canvasRef.value.height = h
    init(w, h)
  }

  function handleMouseMove(e) {
    mouse.x = e.x
    mouse.y = e.y
  }

  function handleMouseLeave() {
    mouse.x = null
    mouse.y = null
  }

  function start() {
    if (!canvasRef.value) return
    ctx = canvasRef.value.getContext('2d')
    handleResize()
    animateFrame()
    window.addEventListener('resize', handleResize)
    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseleave', handleMouseLeave)
  }

  function stop() {
    cancelAnimationFrame(animationFrame)
    animationFrame = null
    window.removeEventListener('resize', handleResize)
    window.removeEventListener('mousemove', handleMouseMove)
    window.removeEventListener('mouseleave', handleMouseLeave)
  }

  return { start, stop, handleResize }
}

/** 认证页：与 src/sea/wallbgcanvas.js 同构的 Three 粒子海（独立 WebGL canvas） */
function useAuthOcean(canvasRef) {
  let api = null

  async function start() {
    if (!canvasRef.value) {
      return
    }
    stop()
    try {
      api = await createAuthWallThree(canvasRef.value)
    } catch (err) {
      console.warn('createAuthWallThree failed', err)
    }
  }

  function stop() {
    if (api) {
      api.stop()
      api = null
    }
  }

  return { start, stop }
}

export function useParticleSea(options = {}) {
  const canvasClassicRef = ref(null)
  const canvasGlRef = ref(null)
  const route = useRoute()
  const isAuthLayout = computed(() => route.meta?.layout === 'auth')
  const embedded = computed(() => Boolean(unref(options.embedded)))
  const hostRef = options.hostRef

  let classic = null
  let ocean = null
  let hostResizeObserver = null

  function readHostSize() {
    const host = unref(hostRef)
    if (host) {
      const rect = host.getBoundingClientRect()
      const scrollH = Number(host.scrollHeight) || 0
      return {
        w: Math.max(1, Math.floor(rect.width)),
        h: Math.max(1, Math.floor(Math.max(rect.height, scrollH))),
      }
    }
    return { w: window.innerWidth, h: window.innerHeight }
  }

  function bindHostResize() {
    hostResizeObserver?.disconnect()
    const host = unref(hostRef)
    if (!embedded.value || !host) return
    hostResizeObserver = new ResizeObserver(() => {
      classic?.handleResize?.()
    })
    hostResizeObserver.observe(host)
    const scrollParent = host.closest('.shell-page-scroll')
    if (scrollParent && scrollParent !== host) {
      hostResizeObserver.observe(scrollParent)
    }
  }

  async function restart() {
    if (classic) classic.stop()
    if (ocean) ocean.stop()
    hostResizeObserver?.disconnect()
    hostResizeObserver = null

    classic = useClassicParticles(canvasClassicRef, readHostSize)
    ocean = useAuthOcean(canvasGlRef)
    await nextTick()

    if (isAuthLayout.value) {
      await ocean.start()
      return
    }
    if (embedded.value) bindHostResize()
    classic.start()
  }

  onMounted(async () => {
    await nextTick()
    await restart()
  })

  watch(isAuthLayout, async () => {
    await nextTick()
    await restart()
  })

  watch([embedded, () => unref(hostRef)], async () => {
    await nextTick()
    await restart()
  })

  onUnmounted(() => {
    if (classic) classic.stop()
    if (ocean) ocean.stop()
    hostResizeObserver?.disconnect()
    hostResizeObserver = null
  })

  return {
    canvasClassicRef,
    canvasGlRef,
    isAuthLayout,
  }
}
