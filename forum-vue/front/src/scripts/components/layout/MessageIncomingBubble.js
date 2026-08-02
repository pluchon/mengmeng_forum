import { computed, watch, onUnmounted } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { useMessageStore } from '@/stores/message'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'

const PREVIEW_MAX = 48
const AUTO_HIDE_MS = 8000

const messageStore = useMessageStore()
const messageCenterUi = useMessageCenterUiStore()

const visible = computed(() =>
  messageStore.showTip && !!messageStore.incomingPreview && !messageCenterUi.visible,
)
const preview = computed(() => messageStore.incomingPreview)
const unread = computed(() => Number(messageStore.unreadCount) || 0)

const displayPreview = computed(() => {
  const text = (preview.value?.preview || '').toString()
  if (!text) return '您收到一条新私信'
  return text.length > PREVIEW_MAX ? `${text.slice(0, PREVIEW_MAX)}…` : text
})

function onOpen() {
  messageCenterUi.open()
}

function onClose() {
  messageStore.hideTip()
}

let hideTimer = null
watch(
  () => messageStore.incomingSignal?.seq,
  (seq) => {
    if (!seq) return
    if (hideTimer) clearTimeout(hideTimer)
    hideTimer = setTimeout(() => {
      messageStore.hideTip()
    }, AUTO_HIDE_MS)
  },
)

onUnmounted(() => {
  if (hideTimer) clearTimeout(hideTimer)
})
