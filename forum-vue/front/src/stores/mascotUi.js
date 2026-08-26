import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

const VISIBLE_KEY = 'mascot_visible_v1'

export const useMascotUiStore = defineStore('mascotUi', () => {
  const visible = ref(true)

  try {
    const raw = localStorage.getItem(VISIBLE_KEY)
    if (raw === '0') visible.value = false
  }
  catch {
    // 忽略
  }

  watch(visible, (value) => {
    try {
      localStorage.setItem(VISIBLE_KEY, value ? '1' : '0')
    }
    catch {
      // 忽略
    }
  })

  function setVisible(value) {
    visible.value = Boolean(value)
  }

  return {
    setVisible,
    visible,
  }
})
