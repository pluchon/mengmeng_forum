import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useMessageCenterUiStore = defineStore('messageCenterUi', () => {
  const visible = ref(false)
  /** @type {import('vue').Ref<{ userId?: number, groupId?: number, nickname?: string, avatarUrl?: string } | null>} */
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
