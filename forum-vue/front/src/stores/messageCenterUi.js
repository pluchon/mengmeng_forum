import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useMessageCenterUiStore = defineStore('messageCenterUi', () => {
  const visible = ref(false)
  const openTarget = ref(null)

  function open(target) {
    openTarget.value = target || null
    visible.value = true
  }

  function close() {
    visible.value = false
    openTarget.value = null
  }

  return { visible, openTarget, open, close }
})
