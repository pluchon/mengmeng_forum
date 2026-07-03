import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'forum_theme_mode_v1'
const THEME_LIGHT = 'light'
const THEME_DARK = 'dark'

function readSavedMode() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === THEME_DARK ? THEME_DARK : THEME_LIGHT
  }
  catch {
    return THEME_LIGHT
  }
}

function writeSavedMode(mode) {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  }
  catch {
    /* ignore */
  }
}

function applyDocumentTheme(mode) {
  if (typeof document === 'undefined')
    return
  const root = document.documentElement
  root.dataset.theme = mode
  root.classList.toggle('dark', mode === THEME_DARK)
  root.style.colorScheme = mode
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref(THEME_LIGHT)
  const isDark = computed(() => mode.value === THEME_DARK)
  const modeText = computed(() => (isDark.value ? '深色' : '浅色'))

  function initTheme() {
    mode.value = readSavedMode()
    applyDocumentTheme(mode.value)
  }

  function setMode(nextMode) {
    mode.value = nextMode === THEME_DARK ? THEME_DARK : THEME_LIGHT
    writeSavedMode(mode.value)
    applyDocumentTheme(mode.value)
  }

  function toggleTheme() {
    setMode(isDark.value ? THEME_LIGHT : THEME_DARK)
  }

  return {
    isDark,
    mode,
    modeText,
    initTheme,
    setMode,
    toggleTheme,
  }
})
