import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Close, Headset, Microphone, Phone, PhoneFilled } from '@element-plus/icons-vue'
import { useGroupVoiceStore } from '@/stores/groupVoice'
import { useUserStore } from '@/stores/user'
import { isVipActive } from '@/utils/vip'

const MAX_SEATS = 6

export function useGroupVoiceDock() {
  const route = useRoute()
  const voiceStore = useGroupVoiceStore()
  const userStore = useUserStore()
  const failedAvatarMap = ref({})

  const isBlockedPage = computed(() =>
    route.meta.layout === 'auth' || route.path === '/games' || route.path.startsWith('/games/'),
  )

  const showDock = computed(() =>
    userStore.isLoggedIn && voiceStore.active && voiceStore.joined && !isBlockedPage.value,
  )

  const dockStyle = computed(() => ({
    top: `${voiceStore.floatPosition.top}px`,
    right: `${voiceStore.floatPosition.right}px`,
  }))

  const seats = computed(() => {
    const participants = voiceStore.participants.slice(0, MAX_SEATS)
    return Array.from({ length: MAX_SEATS }, (_, index) => ({
      key: participants[index]?.user?.id || `empty-${index}`,
      participant: participants[index] || null,
    }))
  })

  const volumeBars = computed(() => {
    const level = voiceStore.muted || voiceStore.deafened ? 0 : voiceStore.volumeLevel
    return [0.32, 0.58, 0.82].map((factor, index) => ({
      index,
      height: `${Math.max(3, Math.round(4 + level * 16 * factor))}px`,
    }))
  })

  let dragState = null
  let suppressNextClick = false

  function startDrag(event) {
    if (event.button !== 0) return
    dragState = {
      x: event.clientX,
      y: event.clientY,
      top: voiceStore.floatPosition.top,
      right: voiceStore.floatPosition.right,
      moved: false,
    }
    window.addEventListener('pointermove', onDrag)
    window.addEventListener('pointerup', stopDrag, { once: true })
  }

  function onDrag(event) {
    if (!dragState) return
    const dx = event.clientX - dragState.x
    const dy = event.clientY - dragState.y
    if (Math.abs(dx) + Math.abs(dy) > 3) dragState.moved = true
    const maxTop = Math.max(12, window.innerHeight - 64)
    const maxRight = Math.max(12, window.innerWidth - 64)
    voiceStore.floatPosition = {
      top: clamp(dragState.top + dy, 12, maxTop),
      right: clamp(dragState.right - dx, 12, maxRight),
    }
  }

  function stopDrag() {
    suppressNextClick = dragState?.moved === true
    window.removeEventListener('pointermove', onDrag)
    dragState = null
  }

  async function openDialog(event) {
    if (suppressNextClick) {
      suppressNextClick = false
      return
    }
    try {
      await voiceStore.openVoiceDialog()
    } catch {
      /* 已提示 */
    }
    event?.currentTarget?.blur()
  }

  async function leaveVoice() {
    await ElMessageBox.confirm('确认退出当前群语音吗？', '退出语音', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await voiceStore.leave()
  }

  watch(
    isBlockedPage,
    async (blocked) => {
      if (blocked && voiceStore.joined) {
        await voiceStore.leave()
      }
    },
    { immediate: true },
  )

  onUnmounted(() => {
    window.removeEventListener('pointermove', onDrag)
  })

  function avatarSrc(participant) {
    const key = avatarKey(participant)
    if (!key || failedAvatarMap.value[key]) return ''
    return participant?.user?.avatarUrl || ''
  }

  function avatarText(participant) {
    const name = voiceStore.nameFor(participant)
    return name.slice(0, 1).toUpperCase()
  }

  function markAvatarFailed(participant) {
    const key = avatarKey(participant)
    if (!key) return
    failedAvatarMap.value = { ...failedAvatarMap.value, [key]: true }
  }

  function isVipParticipant(participant) {
    return isVipActive(Number(participant?.user?.vipTier) || 0, participant?.user?.vipExpireAt)
  }

  function connectionLabel(participant) {
    return voiceStore.isParticipantConnected(participant) ? '已连接' : '未连接'
  }

  return {
    Close,
    Headset,
    Microphone,
    Phone,
    PhoneFilled,
    avatarSrc,
    avatarText,
    dockStyle,
    connectionLabel,
    isVipParticipant,
    leaveVoice,
    markAvatarFailed,
    openDialog,
    seats,
    showDock,
    startDrag,
    voiceStore,
    volumeBars,
  }
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function avatarKey(participant) {
  return participant?.user?.id == null ? '' : String(participant.user.id)
}
