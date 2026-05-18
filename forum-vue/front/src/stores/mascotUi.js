import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

const PASS_THROUGH_KEY = 'mascot_pointer_pass_through_v1'

export const useMascotUiStore = defineStore('mascotUi', () => {
  const pointerPassThrough = ref(false)

  try {
    const raw = localStorage.getItem(PASS_THROUGH_KEY)
    if (raw === '1')
      pointerPassThrough.value = true
  }
  catch {
    /* ignore */
  }

  watch(pointerPassThrough, (v) => {
    try {
      localStorage.setItem(PASS_THROUGH_KEY, v ? '1' : '0')
    }
    catch {
      /* ignore */
    }
  })

  function togglePointerPassThrough() {
    pointerPassThrough.value = !pointerPassThrough.value
  }

  return {
    pointerPassThrough,
    togglePointerPassThrough,
  }
})
