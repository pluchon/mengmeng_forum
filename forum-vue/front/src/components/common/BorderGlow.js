import { computed, onBeforeUnmount, ref, useTemplateRef, watch } from 'vue'

const props = defineProps({
  className: {
    type: String,
    default: '',
  },
  edgeSensitivity: {
    type: Number,
    default: 30,
  },
  glowColor: {
    type: String,
    default: '40 80 80',
  },
  backgroundColor: {
    type: String,
    default: 'transparent',
  },
  borderRadius: {
    type: Number,
    default: 28,
  },
  glowRadius: {
    type: Number,
    default: 40,
  },
  glowIntensity: {
    type: Number,
    default: 1.0,
  },
  coneSpread: {
    type: Number,
    default: 25,
  },
  // AI 工作中为 true：持续顺时针环绕光效
  animated: {
    type: Boolean,
    default: false,
  },
  colors: {
    type: Array,
    default: () => ['#c084fc', '#f472b6', '#38bdf8'],
  },
  fillOpacity: {
    type: Number,
    default: 0.5,
  },
  // 环绕角速度（度/秒），顺时针增大
  sweepSpeed: {
    type: Number,
    default: 96,
  },
})

function parseHSL(hslStr) {
  const match = String(hslStr || '').match(/([\d.]+)\s*([\d.]+)%?\s*([\d.]+)%?/)
  if (!match) return { h: 40, s: 80, l: 80 }
  return { h: parseFloat(match[1]), s: parseFloat(match[2]), l: parseFloat(match[3]) }
}

function buildBoxShadow(glowColor, intensity) {
  const { h, s, l } = parseHSL(glowColor)
  const base = `${h}deg ${s}% ${l}%`
  const layers = [
    [0, 0, 0, 1, 100, true],
    [0, 0, 1, 0, 60, true],
    [0, 0, 3, 0, 50, true],
    [0, 0, 6, 0, 40, true],
    [0, 0, 15, 0, 30, true],
    [0, 0, 25, 2, 20, true],
    [0, 0, 50, 2, 10, true],
    [0, 0, 1, 0, 60, false],
    [0, 0, 3, 0, 50, false],
    [0, 0, 6, 0, 40, false],
    [0, 0, 15, 0, 30, false],
    [0, 0, 25, 2, 20, false],
    [0, 0, 50, 2, 10, false],
  ]
  return layers
    .map(([x, y, blur, spread, alpha, inset]) => {
      const a = Math.min(alpha * intensity, 100)
      return `${inset ? 'inset ' : ''}${x}px ${y}px ${blur}px ${spread}px hsl(${base} / ${a}%)`
    })
    .join(', ')
}

function easeOutCubic(x) {
  return 1 - Math.pow(1 - x, 3)
}

function animateValue({
  start = 0,
  end = 100,
  duration = 1000,
  delay = 0,
  ease = easeOutCubic,
  onUpdate,
  onEnd,
}) {
  let rafId = 0
  let timeoutId = 0
  let cancelled = false

  const run = () => {
    const t0 = performance.now()
    const tick = (now) => {
      if (cancelled) return
      const t = Math.min((now - t0) / duration, 1)
      onUpdate(start + (end - start) * ease(t))
      if (t < 1) {
        rafId = requestAnimationFrame(tick)
      } else if (onEnd) {
        onEnd()
      }
    }
    rafId = requestAnimationFrame(tick)
  }

  if (delay > 0) {
    timeoutId = window.setTimeout(run, delay)
  } else {
    run()
  }

  return () => {
    cancelled = true
    if (timeoutId) window.clearTimeout(timeoutId)
    if (rafId) cancelAnimationFrame(rafId)
  }
}

const GRADIENT_POSITIONS = ['80% 55%', '69% 34%', '8% 6%', '41% 38%', '86% 85%', '82% 18%', '51% 4%']
const COLOR_MAP = [0, 1, 2, 0, 1, 2, 1]

function buildMeshGradients(colors) {
  const safeColors = Array.isArray(colors) && colors.length ? colors : ['#c084fc', '#f472b6', '#38bdf8']
  const gradients = []
  for (let i = 0; i < 7; i += 1) {
    const c = safeColors[Math.min(COLOR_MAP[i], safeColors.length - 1)]
    gradients.push(`radial-gradient(at ${GRADIENT_POSITIONS[i]}, ${c} 0px, transparent 50%)`)
  }
  gradients.push(`linear-gradient(${safeColors[0]} 0 100%)`)
  return gradients
}

const cardRef = useTemplateRef('cardRef')
const isHovered = ref(false)
const cursorAngle = ref(45)
const edgeProximity = ref(0)
const sweepActive = ref(false)

let stopFade = null
let sweepRafId = 0
let sweepStartedAt = 0
let sweepBaseAngle = 0

function cancelSweepLoop() {
  if (sweepRafId) {
    cancelAnimationFrame(sweepRafId)
    sweepRafId = 0
  }
}

function cancelFade() {
  if (stopFade) {
    stopFade()
    stopFade = null
  }
}

function stopAllMotion() {
  cancelSweepLoop()
  cancelFade()
}

function getCenterOfElement(el) {
  const { width, height } = el.getBoundingClientRect()
  return [width / 2, height / 2]
}

function getEdgeProximity(el, x, y) {
  const [cx, cy] = getCenterOfElement(el)
  const dx = x - cx
  const dy = y - cy
  let kx = Infinity
  let ky = Infinity
  if (dx !== 0) kx = cx / Math.abs(dx)
  if (dy !== 0) ky = cy / Math.abs(dy)
  return Math.min(Math.max(1 / Math.min(kx, ky), 0), 1)
}

function getCursorAngle(el, x, y) {
  const [cx, cy] = getCenterOfElement(el)
  const dx = x - cx
  const dy = y - cy
  if (dx === 0 && dy === 0) return 0
  const radians = Math.atan2(dy, dx)
  let degrees = radians * (180 / Math.PI) + 90
  if (degrees < 0) degrees += 360
  return degrees
}

function handlePointerMove(e) {
  if (props.animated) return
  const card = cardRef.value
  if (!card) return
  const rect = card.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  edgeProximity.value = getEdgeProximity(card, x, y)
  cursorAngle.value = getCursorAngle(card, x, y)
}

function handlePointerEnter() {
  isHovered.value = true
}

function handlePointerLeave() {
  isHovered.value = false
  if (!props.animated) {
    edgeProximity.value = 0
  }
}

function startClockwiseSweep() {
  cancelSweepLoop()
  cancelFade()
  sweepActive.value = true
  sweepBaseAngle = cursorAngle.value
  sweepStartedAt = performance.now()

  // 先点亮光效，再持续顺时针旋转
  stopFade = animateValue({
    start: Math.max(edgeProximity.value * 100, 0),
    end: 100,
    duration: 320,
    onUpdate: (v) => {
      edgeProximity.value = v / 100
    },
  })

  const tick = (now) => {
    if (!props.animated) return
    const elapsedSec = (now - sweepStartedAt) / 1000
    // conic-gradient 角度增大 = 顺时针
    cursorAngle.value = (sweepBaseAngle + elapsedSec * props.sweepSpeed) % 360
    sweepRafId = requestAnimationFrame(tick)
  }
  sweepRafId = requestAnimationFrame(tick)
}

function stopClockwiseSweep() {
  cancelSweepLoop()
  cancelFade()
  const from = edgeProximity.value * 100
  stopFade = animateValue({
    start: from,
    end: 0,
    duration: 520,
    onUpdate: (v) => {
      edgeProximity.value = v / 100
    },
    onEnd: () => {
      sweepActive.value = false
      stopFade = null
    },
  })
}

watch(
  () => props.animated,
  (active) => {
    if (active) {
      startClockwiseSweep()
      return
    }
    stopClockwiseSweep()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopAllMotion()
})

const colorSensitivity = computed(() => props.edgeSensitivity + 20)
const isVisible = computed(() => isHovered.value || sweepActive.value)
const borderOpacity = computed(() => {
  if (!isVisible.value) return 0
  return Math.max(0, (edgeProximity.value * 100 - colorSensitivity.value) / (100 - colorSensitivity.value))
})
const glowOpacity = computed(() => {
  if (!isVisible.value) return 0
  return Math.max(0, (edgeProximity.value * 100 - props.edgeSensitivity) / (100 - props.edgeSensitivity))
})

const meshGradients = computed(() => buildMeshGradients(props.colors))
const borderBg = computed(() => meshGradients.value.map((g) => `${g} border-box`))
const fillBg = computed(() => meshGradients.value.map((g) => `${g} padding-box`))
const angleDeg = computed(() => `${cursorAngle.value.toFixed(3)}deg`)

const rootStyle = computed(() => ({
  background: props.backgroundColor,
  borderRadius: `${props.borderRadius}px`,
}))

const borderLayerStyle = computed(() => {
  const spread = props.coneSpread
  const padColor = props.backgroundColor === 'transparent' ? '#ffffff' : props.backgroundColor
  return {
    border: '1px solid transparent',
    background: [
      `linear-gradient(${padColor} 0 100%) padding-box`,
      'linear-gradient(rgb(255 255 255 / 0%) 0% 100%) border-box',
      ...borderBg.value,
    ].join(', '),
    opacity: borderOpacity.value,
    maskImage: `conic-gradient(from ${angleDeg.value} at center, black ${spread}%, transparent ${
      spread + 15
    }%, transparent ${100 - spread - 15}%, black ${100 - spread}%)`,
    WebkitMaskImage: `conic-gradient(from ${angleDeg.value} at center, black ${spread}%, transparent ${
      spread + 15
    }%, transparent ${100 - spread - 15}%, black ${100 - spread}%)`,
    transition: isVisible.value ? 'opacity 0.25s ease-out' : 'opacity 0.75s ease-in-out',
  }
})

const fillLayerStyle = computed(() => ({
  border: '1px solid transparent',
  background: fillBg.value.join(', '),
  maskImage: [
    'linear-gradient(to bottom, black, black)',
    'radial-gradient(ellipse at 50% 50%, black 40%, transparent 65%)',
    'radial-gradient(ellipse at 66% 66%, black 5%, transparent 40%)',
    'radial-gradient(ellipse at 33% 33%, black 5%, transparent 40%)',
    'radial-gradient(ellipse at 66% 33%, black 5%, transparent 40%)',
    'radial-gradient(ellipse at 33% 66%, black 5%, transparent 40%)',
    `conic-gradient(from ${angleDeg.value} at center, transparent 5%, black 15%, black 85%, transparent 95%)`,
  ].join(', '),
  WebkitMaskImage: [
    'linear-gradient(to bottom, black, black)',
    'radial-gradient(ellipse at 50% 50%, black 40%, transparent 65%)',
    'radial-gradient(ellipse at 66% 66%, black 5%, transparent 40%)',
    'radial-gradient(ellipse at 33% 33%, black 5%, transparent 40%)',
    'radial-gradient(ellipse at 66% 33%, black 5%, transparent 40%)',
    'radial-gradient(ellipse at 33% 66%, black 5%, transparent 40%)',
    `conic-gradient(from ${angleDeg.value} at center, transparent 5%, black 15%, black 85%, transparent 95%)`,
  ].join(', '),
  maskComposite: 'subtract, add, add, add, add, add',
  WebkitMaskComposite: 'source-out, source-over, source-over, source-over, source-over, source-over',
  opacity: borderOpacity.value * props.fillOpacity,
  mixBlendMode: 'soft-light',
  transition: isVisible.value ? 'opacity 0.25s ease-out' : 'opacity 0.75s ease-in-out',
}))

const glowWrapStyle = computed(() => ({
  inset: `-${props.glowRadius}px`,
  maskImage: `conic-gradient(from ${angleDeg.value} at center, black 2.5%, transparent 10%, transparent 90%, black 97.5%)`,
  WebkitMaskImage: `conic-gradient(from ${angleDeg.value} at center, black 2.5%, transparent 10%, transparent 90%, black 97.5%)`,
  opacity: glowOpacity.value,
  mixBlendMode: 'plus-lighter',
  transition: isVisible.value ? 'opacity 0.25s ease-out' : 'opacity 0.75s ease-in-out',
}))

const glowInnerStyle = computed(() => ({
  inset: `${props.glowRadius}px`,
  boxShadow: buildBoxShadow(props.glowColor, props.glowIntensity),
}))
