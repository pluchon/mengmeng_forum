import { ref } from 'vue'
import { useThemeStore } from '@/stores/theme'
import '@/assets/styles/theme-mode-switch.css'

const themeStore = useThemeStore()
const switchRef = ref(null)
const motionMode = ref('')
const isTransitioning = ref(false)

const THEME_TRANSITION_DURATION = 520
const THEME_TRANSITION_EASING = 'ease-in-out'

function prefersReducedMotion() {
  return typeof window !== 'undefined'
    && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function resolveTogglePoint(event, sourceEl) {
  if (event && Number.isFinite(event.clientX) && Number.isFinite(event.clientY)) {
    return {
      x: event.clientX,
      y: event.clientY,
    }
  }

  if (!sourceEl) {
    return {
      x: window.innerWidth / 2,
      y: window.innerHeight / 2,
    }
  }

  const rect = sourceEl.getBoundingClientRect()
  return {
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2,
  }
}

function calcThemeSweepRadius(x, y) {
  const maxX = Math.max(x, window.innerWidth - x)
  const maxY = Math.max(y, window.innerHeight - y)
  return Math.ceil(Math.sqrt(maxX * maxX + maxY * maxY))
}

function createThemeSweepFallback(targetMode, x, y, radius) {
  if (typeof document === 'undefined') return null

  const sweep = document.createElement('span')

  sweep.className = `theme-transition-sweep theme-transition-sweep--${targetMode}`
  sweep.style.setProperty('--theme-sweep-x', `${x}px`)
  sweep.style.setProperty('--theme-sweep-y', `${y}px`)
  sweep.style.setProperty('--theme-sweep-radius', `${radius}px`)
  document.body.appendChild(sweep)

  requestAnimationFrame(() => {
    sweep.classList.add('is-active')
  })

  window.setTimeout(() => {
    sweep.remove()
  }, THEME_TRANSITION_DURATION + 80)

  return sweep
}

async function runViewTransition(targetMode, x, y, radius) {
  if (typeof document === 'undefined' || !document.startViewTransition) {
    return false
  }

  const transition = document.startViewTransition(() => {
    themeStore.setMode(targetMode)
  })

  try {
    await transition.ready
    const animation = document.documentElement.animate(
      {
        clipPath: [
          `circle(0px at ${x}px ${y}px)`,
          `circle(${radius}px at ${x}px ${y}px)`,
        ],
      },
      {
        duration: THEME_TRANSITION_DURATION,
        easing: THEME_TRANSITION_EASING,
        pseudoElement: '::view-transition-new(root)',
      },
    )
    await animation.finished
  } catch {
    try {
      await transition.finished
    } catch {
      // 页面切换或浏览器取消 View Transition 时，主题状态已经完成切换。
    }
  }

  return true
}

async function handleToggle(event) {
  if (isTransitioning.value) return

  const targetMode = themeStore.isDark ? 'light' : 'dark'
  const sourceEl = event?.currentTarget || switchRef.value
  const { x, y } = resolveTogglePoint(event, sourceEl)
  const radius = calcThemeSweepRadius(x, y)

  isTransitioning.value = true
  motionMode.value = targetMode

  try {
    if (prefersReducedMotion()) {
      themeStore.setMode(targetMode)
      return
    }

    const handledByViewTransition = await runViewTransition(targetMode, x, y, radius)

    if (!handledByViewTransition) {
      createThemeSweepFallback(targetMode, x, y, radius)
      themeStore.setMode(targetMode)
      await new Promise((resolve) => {
        window.setTimeout(resolve, THEME_TRANSITION_DURATION)
      })
    }
  } finally {
    if (motionMode.value === targetMode) {
      motionMode.value = ''
    }

    isTransitioning.value = false
  }
}
