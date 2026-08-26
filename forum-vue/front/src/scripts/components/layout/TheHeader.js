import { ref, onUnmounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, Message, Picture, Trophy } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCheckinSnapshotStore } from '@/stores/checkinSnapshot'
import { getUnReadCount } from '@/api/message'
import { getSystemMessageUnreadCount } from '@/api/systemMessage'
import { useWebSocket } from '@/composables/useWebSocket'
import { useMessageStore } from '@/stores/message'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { blockIfMuted } from '@/utils/userMute'
import { useVipStatusEntry } from '@/composables/useVipStatusEntry'
import '@/assets/styles/layout.css'
import '@/assets/styles/vip-status-pill.css'

export function useTheHeader() {
  const router = useRouter()
  const route = useRoute()
  const userStore = useUserStore()
  const checkinSnapshotStore = useCheckinSnapshotStore()
  const defaultAvatar = DEFAULT_AVATAR

  const checkinLoaded = computed(() => checkinSnapshotStore.loaded)
  const checkinTotalPoints = computed(() => checkinSnapshotStore.totalPoints)
  const {
    vipDialogVisible,
    vipStatusIcon,
    vipStatusLabel,
    vipStatusPillClass,
    openVipPurchase,
  } = useVipStatusEntry(userStore)

  const searchQuery = ref('')
  const { initWebSocket, closeWebSocket } = useWebSocket()
  const messageStore = useMessageStore()
  const messageCenterUi = useMessageCenterUiStore()

  function openMessageCenter() {
    messageCenterUi.open()
  }

  // 使用 computed 与 store 同步，确保实时性
  const msgUnread = computed(
    () => (Number(messageStore.unreadCount) || 0) + (Number(messageStore.systemUnreadCount) || 0),
  )
  // 用户协议 / 隐私政策页：导航不出现表情商城等入口
  const isLegalDocPage = computed(() => route.path === '/terms' || route.path === '/privacy')
  let timer = null

  const fetchUnread = async () => {
    if (!userStore.isLoggedIn) return
    try {
      const [msgRes, sysRes] = await Promise.all([getUnReadCount(), getSystemMessageUnreadCount()])
      const privateCount = msgRes?.code === 0 ? Number(msgRes.data) || 0 : 0
      const systemCount = sysRes?.code === 0 ? Number(sysRes.data) || 0 : 0
      messageStore.setUnreadCount(privateCount, { keepTip: messageStore.showTip })
      messageStore.setSystemUnreadCount(systemCount)
    } catch {}
  }

  // 监听登录状态变化，重新初始化
  watch(() => userStore.isLoggedIn, (val) => {
    if (val) {
      // 页面刷新后 token 走持久化，但用户资料不会自动刷新；这里补一次拉取
      userStore.fetchUserInfo()
      initWebSocket()
      fetchUnread()
      checkinSnapshotStore.refresh()
    } else {
      closeWebSocket()
      checkinSnapshotStore.clear()
    }
  }, { immediate: true })

  watch(
    () => route.path,
    (p) => {
      if (!userStore.isLoggedIn) return
      if (p === '/' || p === '/checkin') checkinSnapshotStore.refresh()
    },
  )

  let incomingUnreadTimer = null
  watch(
    () => messageStore.incomingSignal?.seq,
    async () => {
      if (!messageStore.incomingSignal?.seq) return
      clearTimeout(incomingUnreadTimer)
      incomingUnreadTimer = setTimeout(async () => {
        await fetchUnread()
        messageStore.showIncomingTip()
      }, 500)
    },
  )

  watch(
    () => [messageStore.auditResultSignal?.seq, messageStore.systemMessageSignal?.seq],
    () => {
      fetchUnread()
    },
  )

  onUnmounted(() => {
    if (timer) clearInterval(timer)
    if (incomingUnreadTimer) clearTimeout(incomingUnreadTimer)
  })

  const handleLogout = () => {
    userStore.logout({ remote: true })
  }

  const goToCreative = () => {
    if (blockIfMuted(userStore)) return
    router.push('/creative')
  }

  const submitSearch = () => {
    const kw = searchQuery.value?.trim()
    if (!kw) return
    const query = { keyword: kw }
    try {
      if (localStorage.getItem('luntan_home_ai_search') === '1') {
        query.ai = '1'
      }
    } catch {
      // 忽略
    }
    router.push({ path: '/search', query })
  }

  return {
    checkinLoaded,
    checkinTotalPoints,
    Message,
    Picture,
    Search,
    Trophy,
    defaultAvatar,
    goToCreative,
    handleLogout,
    openMessageCenter,
    openVipPurchase,
    messageStore,
    msgUnread,
    isLegalDocPage,
    route,
    searchQuery,
    submitSearch,
    userStore,
    vipDialogVisible,
    vipStatusIcon,
    vipStatusLabel,
    vipStatusPillClass,
  }
}
