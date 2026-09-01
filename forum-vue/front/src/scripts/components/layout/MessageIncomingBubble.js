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

const isGroupTip = computed(() => preview.value?.kind === 'group' && !!preview.value?.groupName)

// 群聊卡片第一行是群名，私信第一行是对方昵称
const displayTitle = computed(() => {
  if (isGroupTip.value) return preview.value.groupName
  return preview.value?.sender || '新私信'
})

const displayAvatarUrl = computed(() => (isGroupTip.value ? preview.value?.groupAvatarUrl || '' : ''))

// 被 @ 时单独挑出来着色，一眼能看见是不是叫自己
const isMentioned = computed(() => isGroupTip.value && preview.value?.mentioned === true)

// 群聊第二行是「谁说了什么」，一行放不下就截断——卡片要轻
const displayPreview = computed(() => {
  const body = (preview.value?.preview || '').toString()
  const text = isGroupTip.value
    ? `${preview.value?.sender || '群成员'}：${body || '发来一条消息'}`
    : body
  if (!text) return '您收到一条新私信'
  // 前缀单独渲染，截断只算正文那部分
  const limit = isMentioned.value ? PREVIEW_MAX - 6 : PREVIEW_MAX
  return text.length > limit ? `${text.slice(0, limit)}…` : text
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
