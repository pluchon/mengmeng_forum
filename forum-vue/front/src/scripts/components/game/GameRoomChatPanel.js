import { nextTick, ref, watch } from 'vue'
import { ChatDotRound, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useGameRoomEmojiPicker } from '@/composables/useGameRoomEmojiPicker'

import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'

function useGameRoomChatPanel(props, emit) {
  const chatText = ref('')
  const chatListRef = ref(null)

  const {
    emojiShopStore,
    emojiPanelOpen,
    packBarRef,
    packBarCanScrollLeft,
    packBarCanScrollRight,
    visiblePacks,
    selectedPack,
    onPackBarScroll,
    scrollPackBarLeft,
    scrollPackBarRight,
    selectPack,
    onEmojiPanelShow,
    pickEmojiUrl,
  } = useGameRoomEmojiPicker()

  function scrollChatToBottom() {
    nextTick(() => {
      const el = chatListRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  watch(
    () => props.messages?.length,
    () => scrollChatToBottom(),
  )

  function sendChat() {
    const content = chatText.value.trim()
    if (!content || !props.canChat) return
    emit('send-text', content)
    chatText.value = ''
  }

  function sendEmoji(url) {
    if (!url || !props.canChat) return
    emit('send-emoji', url)
  }

  function onPickEmoji(url) {
    pickEmojiUrl(url, sendEmoji)
  }

  function displayName(userId) {
    if (typeof props.resolveName === 'function') {
      return props.resolveName(userId)
    }
    return userId === props.currentUserId ? '我' : `用户 ${userId}`
  }

  function isEmojiMessage(msg) {
    const type = String(msg?.messageType || '').toUpperCase()
    return type === 'EMOJI' || Boolean(msg?.emojiUrl)
  }

  return {
    ChatDotRound,
    ArrowLeft,
    ArrowRight,
    emojiPackIconUrl,
    chatText,
    chatListRef,
    emojiShopStore,
    emojiPanelOpen,
    packBarRef,
    packBarCanScrollLeft,
    packBarCanScrollRight,
    visiblePacks,
    selectedPack,
    onPackBarScroll,
    scrollPackBarLeft,
    scrollPackBarRight,
    selectPack,
    onEmojiPanelShow,
    sendChat,
    onPickEmoji,
    displayName,
    isEmojiMessage,
  }
}

const props = defineProps({
  messages: { type: Array, default: () => [] },
  canChat: { type: Boolean, default: true },
  currentUserId: { type: [Number, String], default: null },
  resolveName: { type: Function, default: null },
})
const emit = defineEmits(['send-text', 'send-emoji'])

const {
  chatText,
  chatListRef,
  emojiShopStore,
  emojiPanelOpen,
  packBarRef,
  packBarCanScrollLeft,
  packBarCanScrollRight,
  visiblePacks,
  selectedPack,
  onPackBarScroll,
  scrollPackBarLeft,
  scrollPackBarRight,
  selectPack,
  onEmojiPanelShow,
  sendChat,
  onPickEmoji,
  displayName,
  isEmojiMessage,
} = useGameRoomChatPanel(props, emit)
