import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  state: {
    type: String,
    default: 'idle',
  },
  x: {
    type: Number,
    default: 16,
  },
  paused: {
    type: Boolean,
    default: false,
  },
  tipText: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['activate', 'animation-complete', 'hover-change', 'ready'])

const manifest = ref(null)
const atlasUrl = ref('')
const frame = ref(0)
const ready = ref(false)

let animationFrameId = 0
let lastFrameAt = 0
let completedRuns = 0
let completionEmitted = false

const stateConfig = computed(() => {
  const states = manifest.value?.states || {}
  return states[props.state] || states.idle || null
})

const rootStyle = computed(() => {
  const display = manifest.value?.display || {}
  return {
    width: `${display.width || 96}px`,
    height: `${display.height || 104}px`,
    bottom: `${display.bottom || 12}px`,
    transform: `translate3d(${Math.round(props.x)}px, 0, 0)`,
  }
})

const frameStyle = computed(() => {
  const atlas = manifest.value?.atlas
  const config = stateConfig.value
  if (!atlas || !config || !atlasUrl.value) return {}
  const xPercent = atlas.columns > 1 ? frame.value * 100 / (atlas.columns - 1) : 0
  const yPercent = atlas.rows > 1 ? config.row * 100 / (atlas.rows - 1) : 0
  return {
    backgroundImage: `url("${atlasUrl.value}")`,
    backgroundSize: `${atlas.columns * 100}% ${atlas.rows * 100}%`,
    backgroundPosition: `${xPercent}% ${yPercent}%`,
  }
})

function stopAnimation() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = 0
  }
}

function resetAnimation() {
  frame.value = 0
  lastFrameAt = 0
  completedRuns = 0
  completionEmitted = false
}

function tick(timestamp) {
  const config = stateConfig.value
  if (!ready.value || !config) return
  if (!props.paused) {
    if (!lastFrameAt) lastFrameAt = timestamp
    const frameDuration = 1000 / Math.max(1, Number(config.fps) || 1)
    if (timestamp - lastFrameAt >= frameDuration) {
      lastFrameAt = timestamp
      const nextFrame = frame.value + 1
      if (nextFrame < config.frames) {
        frame.value = nextFrame
      }
      else if (config.loop) {
        frame.value = 0
      }
      else {
        completedRuns += 1
        const repeats = Math.max(1, Number(config.repeats) || 1)
        if (completedRuns < repeats) {
          frame.value = 0
        }
        else {
          frame.value = Math.max(0, config.frames - 1)
          if (!completionEmitted) {
            completionEmitted = true
            emit('animation-complete', props.state)
          }
          return
        }
      }
    }
  }
  else {
    lastFrameAt = timestamp
  }
  animationFrameId = requestAnimationFrame(tick)
}

function startAnimation() {
  stopAnimation()
  if (!ready.value) return
  animationFrameId = requestAnimationFrame(tick)
}

async function loadManifest() {
  const base = (import.meta.env.BASE_URL || '/').replace(/\/+$/, '')
  const manifestUrl = `${base}/mascot-assets/xiaomeng/sprite-manifest.json`
  const response = await fetch(manifestUrl)
  if (!response.ok) throw new Error(`sprite manifest load failed: ${response.status}`)
  const data = await response.json()
  manifest.value = data
  atlasUrl.value = new URL(data.atlas.image, new URL(manifestUrl, window.location.href)).toString()
  await new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = resolve
    image.onerror = reject
    image.src = atlasUrl.value
  })
  ready.value = true
  emit('ready', data)
  resetAnimation()
  startAnimation()
}

watch(
  () => props.state,
  () => {
    resetAnimation()
    startAnimation()
  },
)

onMounted(() => {
  loadManifest().catch(() => {
    ready.value = false
  })
})

onBeforeUnmount(stopAnimation)
