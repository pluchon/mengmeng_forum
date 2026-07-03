import { ref } from 'vue'
import { useThemeStore } from '@/stores/theme'
import '@/assets/styles/theme-mode-switch.css'

const themeStore = useThemeStore()
const switchRef = ref(null)
const motionMode = ref('')

function createThemeSweep(targetMode, sourceEl) {
  if (typeof document === 'undefined' || !sourceEl) return

  const rect = sourceEl.getBoundingClientRect()
  const x = rect.left + rect.width / 2
  const y = rect.top + rect.height / 2
  const maxX = Math.max(x, window.innerWidth - x)
  const maxY = Math.max(y, window.innerHeight - y)
  const radius = Math.ceil(Math.sqrt(maxX * maxX + maxY * maxY))
  const sweep = document.createElement('span')

  sweep.className = `theme-transition-sweep theme-transition-sweep--${targetMode}`
  sweep.style.left = `${x}px`
  sweep.style.top = `${y}px`
  sweep.style.setProperty('--theme-sweep-radius', `${radius}px`)
  document.body.appendChild(sweep)

  requestAnimationFrame(() => {
    sweep.classList.add('is-active')
  })

  window.setTimeout(() => {
    sweep.classList.add('is-fading')
  }, 420)

  window.setTimeout(() => {
    sweep.remove()
  }, 820)
}

function handleToggle(event) {
  const targetMode = themeStore.isDark ? 'light' : 'dark'
  motionMode.value = targetMode
  createThemeSweep(targetMode, event.currentTarget)

  window.setTimeout(() => {
    themeStore.setMode(targetMode)
  }, 120)

  window.setTimeout(() => {
    if (motionMode.value === targetMode) {
      motionMode.value = ''
    }
  }, 760)
}
