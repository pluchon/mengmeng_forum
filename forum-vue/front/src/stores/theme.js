import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const THEME_STORAGE_KEY = 'forum_theme_mode_v1'
const THEME_LIGHT = 'light'
const THEME_DARK = 'dark'

function readInitialTheme() {
  if (typeof localStorage === 'undefined') return THEME_LIGHT
  return localStorage.getItem(THEME_STORAGE_KEY) === THEME_DARK ? THEME_DARK : THEME_LIGHT
}

function applyTheme(mode) {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = mode
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref(readInitialTheme())
  const isDark = computed(() => mode.value === THEME_DARK)
  const modeLabel = computed(() => (isDark.value ? '深色模式' : '浅色模式'))

  function setMode(nextMode) {
    mode.value = nextMode === THEME_DARK ? THEME_DARK : THEME_LIGHT
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(THEME_STORAGE_KEY, mode.value)
    }
    applyTheme(mode.value)
  }

  function toggleMode() {
    setMode(isDark.value ? THEME_LIGHT : THEME_DARK)
  }

  applyTheme(mode.value)

  return {
    isDark,
    mode,
    modeLabel,
    setMode,
    toggleMode,
  }
})
