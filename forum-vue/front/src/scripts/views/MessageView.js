import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Close,
  ChatDotRound,
  Setting,
  ChatLineRound,
  Search,
  Picture,
  Plus,
  ArrowRight,
  ArrowLeft,
  Bell,
  CircleCheck,
  Star,
  ChatLineSquare,
  Warning,
  Document,
  UserFilled,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import {
  getSessionList,
  getMessageList,
  getMessageDetailById,
  getUnReadCount,
  sendMessage,
  markRead,
  recallMessage,
  uploadChatImage,
  sendImageMessage,
} from '@/api/message'
import { getUserIsOnline } from '@/api/user'
import {
  getSystemMessageList,
  getSystemMessageUnreadCount,
  markSystemMessageRead,
} from '@/api/systemMessage'
import {
  createGroupChat,
  dissolveGroupChat,
  getGroupChatMessages,
  getGroupChatMembers,
  getGroupChatSessions,
  getPublicGroupChats,
  inviteGroupChatMember,
  joinPublicGroupChat,
  leaveGroupChat,
  markGroupChatRead,
  muteGroupChatMember,
  removeGroupChatMember,
  reportGroupChatMessage,
  sendGroupChatMessage,
  updateGroupChat,
} from '@/api/groupChat'
import { useMessageStore } from '@/stores/message'
import { useChatEmojiStore } from '@/stores/chatEmoji'
import { useEmojiShopStore } from '@/stores/emojiShop'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { unwrapPageRecords } from '@/utils/apiData'
import {
  buildChatMessageTimeline,
  formatChatBubbleTimeShanghai,
  formatChatSessionTimeShanghai,
  parseForumDateTime,
} from '@/utils/datetime'
import { validateLocalImageFile, openImageUploadLoading } from '@/utils/imageUploadFeedback'
import {
  canFavoriteChatMediaMessage,
  validateChatImageMime,
  readImageNaturalSize,
  isUserUploadedChatEmoji,
} from '@/utils/chatMedia'
import { isVipActive } from '@/utils/vip'
import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'

const SYS_TYPE_ICON = {
  1: CircleCheck,
  2: Warning,
  3: Warning,
  99: Bell,
}

const SYS_GROUP_META = {
  audit: { key: 'sys-group-audit', name: '帖子审核', listIcon: Document },
  notice: { key: 'sys-group-notice', name: '系统公告', listIcon: Bell },
  other: { key: 'sys-group-other', name: '系统通知', listIcon: Bell },
}

function getSysGroupId(type) {
  const t = Number(type)
  if (t === 1 || t === 2 || t === 3) return 'audit'
  if (t === 99) return 'notice'
  return 'other'
}

function sortByTimeDesc(a, b) {
  const ta = parseForumDateTime(a?.createTime ?? a?.time)?.getTime() || 0
  const tb = parseForumDateTime(b?.createTime ?? b?.time)?.getTime() || 0
  return tb - ta
}

export function useMessageView() {
  const router = useRouter()
  const userStore = useUserStore()
  const { initWebSocket } = useWebSocket()
  const messageStore = useMessageStore()
  const messageCenterUi = useMessageCenterUiStore()
  const chatEmojiStore = useChatEmojiStore()
  const emojiShopStore = useEmojiShopStore()
  const defaultAvatar = DEFAULT_AVATAR

  const emojiPanelTab = ref('favorites')
  const emojiPopoverVisible = ref(false)
  const activeTab = ref('all')
  const focusedConvKey = ref(null)

  const msgScrollbar = ref()
  const msgContainer = ref()
  const chatImageInput = ref(null)
  const emojiStickerInput = ref(null)
  const inputBoxRef = ref(null)
  const searchQuery = ref('')

  const sessionList = ref([])
  const systemMessages = ref([])
  const systemUnread = ref(0)
  const currentSession = ref(null)
  const currentGroupSession = ref(null)
  /** @type {import('vue').Ref<{ groupId: string, messages: object[] } | null>} */
  const currentSystemGroup = ref(null)
  const messages = ref([])
  const sendContent = ref('')
  const sending = ref(false)
  const groupSessions = ref([])
  const groupCreateVisible = ref(false)
  const groupCreateForm = ref({ name: '', groupType: 0, intro: '' })
  const creatingGroup = ref(false)
  const groupListLoading = ref(false)
  const groupListError = ref('')
  const publicGroupVisible = ref(false)
  const publicGroups = ref([])
  const publicGroupsLoading = ref(false)
  const groupMembersVisible = ref(false)
  const groupMembers = ref([])
  const groupMembersLoading = ref(false)
  const groupEditVisible = ref(false)
  const groupEditForm = ref({ name: '', groupType: 0, intro: '' })
  const savingGroupEdit = ref(false)
  const inviteUserIdInput = ref('')
  const invitingMember = ref(false)
  const peerOnline = ref(false)
  const selfOnline = ref(false)
  let onlinePollTimer = null

  const dialogVisible = computed({
    get: () => messageCenterUi.visible,
    set: (v) => {
      if (!v) handleClose()
    },
  })

  const viewerIsVip = computed(() =>
    isVipActive(userStore.vipTier, userStore.vipExpireAt),
  )

  const peerIsVip = computed(() => {
    const u = currentSession.value?.user
    if (!u) return false
    return isVipActive(u.vipTier, u.vipExpireAt)
  })

  const listItems = computed(() => {
    const q = searchQuery.value.trim().toLowerCase()
    const pmItems = sessionList.value.map((s) => ({
      key: `pm-${s.user?.id}`,
      kind: 'pm',
      session: s,
      name: s.user?.nickname || '用户',
      time: s.lastMessageTime,
      preview: s.lastMessage || '暂无消息',
      unread: Number(s.unReadMessage) || 0,
      user: s.user,
    }))
    const groupMap = new Map()
    for (const m of systemMessages.value) {
      const gid = getSysGroupId(m.type)
      if (!groupMap.has(gid)) {
        groupMap.set(gid, { messages: [], unread: 0 })
      }
      const bucket = groupMap.get(gid)
      bucket.messages.push(m)
      if (Number(m.state) === 0) bucket.unread += 1
    }
    const sysItems = [...groupMap.entries()].map(([gid, bucket]) => {
      const sorted = [...bucket.messages].sort(sortByTimeDesc)
      const latest = sorted[0]
      const meta = SYS_GROUP_META[gid] || SYS_GROUP_META.other
      return {
        key: meta.key,
        kind: 'sys-group',
        groupId: gid,
        name: meta.name,
        messages: sorted,
        time: latest?.createTime,
        preview: previewForSysMessage(latest),
        unread: bucket.unread,
        listIcon: meta.listIcon,
      }
    }).sort((a, b) => {
      const ta = parseForumDateTime(a.time)?.getTime() || 0
      const tb = parseForumDateTime(b.time)?.getTime() || 0
      return tb - ta
    })
    const groupItems = groupSessions.value.map((s) => ({
      key: `group-${s.groupId}`,
      kind: 'group',
      group: s,
      name: s.name || '群聊',
      time: s.lastMessageTime,
      preview: s.lastMessage || '暂无消息',
      unread: Number(s.unreadCount) || 0,
      listIcon: UserFilled,
    }))
    let merged = []
    if (activeTab.value === 'pm') merged = pmItems
    else if (activeTab.value === 'group') merged = groupItems
    else if (activeTab.value === 'notif') merged = sysItems
    else merged = [...pmItems, ...groupItems, ...sysItems].sort((a, b) => {
      const ta = parseForumDateTime(a.time)?.getTime() || 0
      const tb = parseForumDateTime(b.time)?.getTime() || 0
      return tb - ta
    })
    if (!q) return merged
    return merged.filter((item) => item.name?.toLowerCase().includes(q)
      || item.preview?.toLowerCase().includes(q))
  })

  const tabBadges = computed(() => ({
    all: (messageStore.unreadCount || 0) + groupUnreadCount.value + (systemUnread.value || 0),
    pm: messageStore.unreadCount || 0,
    group: groupUnreadCount.value,
    notif: systemUnread.value || 0,
  }))

  const groupUnreadCount = computed(() =>
    groupSessions.value.reduce((sum, item) => sum + (Number(item.unreadCount) || 0), 0),
  )

  const isPrivateChat = computed(() => !!currentSession.value && !currentGroupSession.value)

  const activeChatTitle = computed(() => {
    if (currentSession.value) return currentSession.value.user?.nickname || '私信'
    if (currentGroupSession.value) return currentGroupSession.value.name || '群聊'
    return ''
  })

  const activeChatSubtitle = computed(() => {
    if (!currentGroupSession.value) return ''
    const count = Number(currentGroupSession.value.memberCount) || 0
    const limit = Number(currentGroupSession.value.memberLimit) || 0
    return limit > 0 ? `${count}/${limit} 人` : `${count} 人`
  })

  const isCurrentGroupOwner = computed(() =>
    currentGroupSession.value?.ownerUserId != null
      && Number(currentGroupSession.value.ownerUserId) === Number(userStore.id),
  )

  const visiblePacks = computed(() => {
    let hidden = []
    try {
      const raw = localStorage.getItem('hiddenEmojiPacks')
      hidden = raw ? JSON.parse(raw) : []
    } catch {
      hidden = []
    }
    const set = new Set(hidden.map((x) => Number(x)).filter(Number.isFinite))
    return emojiShopStore.myPacks.filter((p) => !set.has(Number(p.userEmojiId)))
  })

  const FAVORITES_PAGE_SIZE = 8
  const favoritePage = ref(1)
  const favoritePageInput = ref('1')
  const uploadedPage = ref(1)
  const uploadedPageInput = ref('1')
  const selectedPurchasedPackId = ref(null)
  const packBarRef = ref(null)
  const packBarCanScrollLeft = ref(false)
  const packBarCanScrollRight = ref(false)

  const favoriteEmojis = computed(() =>
    chatEmojiStore.list.filter((e) => !isUserUploadedChatEmoji(e)),
  )

  const uploadedEmojis = computed(() =>
    chatEmojiStore.list.filter((e) => isUserUploadedChatEmoji(e)),
  )

  const selectedPurchasedPack = computed(() => {
    const packs = visiblePacks.value
    if (!packs.length) return null
    const id = selectedPurchasedPackId.value
    if (id != null) {
      const hit = packs.find((p) => Number(p.userEmojiId) === Number(id))
      if (hit) return hit
    }
    return packs[0]
  })

  const favoriteTotalPages = computed(() => {
    const total = favoriteEmojis.value.length
    return Math.max(1, Math.ceil(total / FAVORITES_PAGE_SIZE))
  })

  const uploadedTotalPages = computed(() => {
    const total = uploadedEmojis.value.length + 1
    return Math.max(1, Math.ceil(total / FAVORITES_PAGE_SIZE))
  })

  const paginatedFavorites = computed(() => {
    const start = (favoritePage.value - 1) * FAVORITES_PAGE_SIZE
    return favoriteEmojis.value.slice(start, start + FAVORITES_PAGE_SIZE)
  })

  const paginatedUploaded = computed(() => {
    const start = (uploadedPage.value - 1) * FAVORITES_PAGE_SIZE
    return uploadedEmojis.value.slice(start, start + FAVORITES_PAGE_SIZE)
  })

  const showUploadOnCurrentPage = computed(() => {
    const uploadIndex = uploadedEmojis.value.length
    const pageStart = (uploadedPage.value - 1) * FAVORITES_PAGE_SIZE
    const pageEnd = pageStart + FAVORITES_PAGE_SIZE
    return uploadIndex >= pageStart && uploadIndex < pageEnd
  })

  async function removeEmojiKeepPopover(emojiId) {
    await chatEmojiStore.remove(emojiId)
    await nextTick()
    emojiPopoverVisible.value = true
  }

  function openPeerProfile(item) {
    if (item?.kind !== 'pm') return
    const uid = item?.user?.id ?? item?.session?.user?.id
    if (!uid) return
    handleClose()
    router.push(`/profile/${uid}`)
  }

  function syncFavoritePageInput() {
    favoritePageInput.value = String(favoritePage.value)
  }

  function syncUploadedPageInput() {
    uploadedPageInput.value = String(uploadedPage.value)
  }

  function goUploadedFirst() {
    uploadedPage.value = 1
    syncUploadedPageInput()
  }

  function goUploadedPrev() {
    if (uploadedPage.value > 1) {
      uploadedPage.value -= 1
      syncUploadedPageInput()
    }
  }

  function goUploadedNext() {
    if (uploadedPage.value < uploadedTotalPages.value) {
      uploadedPage.value += 1
      syncUploadedPageInput()
    }
  }

  function jumpUploadedPage() {
    const n = Number(uploadedPageInput.value)
    if (!Number.isFinite(n)) return
    uploadedPage.value = Math.min(uploadedTotalPages.value, Math.max(1, Math.floor(n)))
    syncUploadedPageInput()
  }

  function goFavoriteFirst() {
    favoritePage.value = 1
    syncFavoritePageInput()
  }

  function goFavoritePrev() {
    if (favoritePage.value > 1) {
      favoritePage.value -= 1
      syncFavoritePageInput()
    }
  }

  function goFavoriteNext() {
    if (favoritePage.value < favoriteTotalPages.value) {
      favoritePage.value += 1
      syncFavoritePageInput()
    }
  }

  function jumpFavoritePage() {
    const n = Number(favoritePageInput.value)
    if (!Number.isFinite(n)) return
    favoritePage.value = Math.min(favoriteTotalPages.value, Math.max(1, Math.floor(n)))
    syncFavoritePageInput()
  }

  function selectPurchasedPack(pack) {
    selectedPurchasedPackId.value = pack?.userEmojiId ?? null
    nextTick(updatePackBarScrollState)
  }

  function onPackBarScroll() {
    updatePackBarScrollState()
  }

  function scrollPackBarLeft() {
    packBarRef.value?.scrollBy({ left: -120, behavior: 'smooth' })
  }

  function scrollPackBarRight() {
    packBarRef.value?.scrollBy({ left: 120, behavior: 'smooth' })
  }

  function updatePackBarScrollState() {
    const el = packBarRef.value
    if (!el) {
      packBarCanScrollLeft.value = false
      packBarCanScrollRight.value = false
      return
    }
    const maxScroll = el.scrollWidth - el.clientWidth
    packBarCanScrollLeft.value = el.scrollLeft > 4
    packBarCanScrollRight.value = maxScroll > 4 && el.scrollLeft < maxScroll - 4
  }

  watch(visiblePacks, (packs) => {
    if (!packs.length) {
      selectedPurchasedPackId.value = null
      packBarCanScrollLeft.value = false
      packBarCanScrollRight.value = false
      return
    }
    const cur = selectedPurchasedPackId.value
    if (cur == null || !packs.some((p) => Number(p.userEmojiId) === Number(cur))) {
      selectedPurchasedPackId.value = packs[0].userEmojiId
    }
    nextTick(updatePackBarScrollState)
  }, { immediate: true })

  watch(() => favoriteEmojis.value.length, () => {
    if (favoritePage.value > favoriteTotalPages.value) {
      favoritePage.value = favoriteTotalPages.value
    }
    syncFavoritePageInput()
  })

  watch(() => uploadedEmojis.value.length, () => {
    if (uploadedPage.value > uploadedTotalPages.value) {
      uploadedPage.value = uploadedTotalPages.value
    }
    syncUploadedPageInput()
  })

  function coerceMessageId(raw) {
    if (raw == null) return NaN
    const n = typeof raw === 'string' ? Number(raw.trim()) : Number(raw)
    return Number.isFinite(n) ? n : NaN
  }

  const activeSystemMessages = computed(() => {
    const list = currentSystemGroup.value?.messages
    return Array.isArray(list) ? list : []
  })

  const messageTimeline = computed(() => buildChatMessageTimeline(messages.value))

  function previewForSysMessage(m) {
    if (!m) return ''
    const text = (m.content || '').replace(/\s+/g, ' ').trim()
    if (text.length <= 48) return text
    return `${text.slice(0, 48)}…`
  }

  function isActiveItem(item) {
    if (item.kind === 'pm') {
      return currentSession.value?.user?.id != null
        && String(currentSession.value.user.id) === String(item.session?.user?.id)
        && !currentSystemGroup.value
        && !currentGroupSession.value
    }
    if (item.kind === 'group') {
      return currentGroupSession.value?.groupId != null
        && String(currentGroupSession.value.groupId) === String(item.group?.groupId)
        && !currentSession.value
        && !currentSystemGroup.value
    }
    if (item.kind === 'sys-group') {
      return currentSystemGroup.value?.groupId === item.groupId
        && !currentSession.value
        && !currentGroupSession.value
    }
    return false
  }

  function sysIcon(type) {
    return SYS_TYPE_ICON[Number(type)] || Bell
  }

  function sysTagLabel(type) {
    const t = Number(type)
    if (t === 1) return '审核通过'
    if (t === 2) return '审核驳回'
    if (t === 3) return '审核异常'
    return '系统'
  }

  function sysTagClass(type) {
    const t = Number(type)
    if (t === 1) return 'mc-tag mc-tag--pass'
    if (t === 2) return 'mc-tag mc-tag--reject'
    if (t === 3) return 'mc-tag mc-tag--error'
    return 'mc-tag mc-tag--sys'
  }

  function parseSystemMessageContent(msg) {
    const content = msg?.content || ''
    const relatedId = msg?.relatedId
    const match = content.match(/《([^》]+)》/)
    if (match && relatedId) {
      const start = match.index ?? 0
      return {
        before: content.slice(0, start),
        articleTitle: match[1],
        after: content.slice(start + match[0].length),
        relatedId,
      }
    }
    return { plain: content, relatedId: relatedId || null }
  }

  async function loadSystemMessages() {
    try {
      const [listRes, countRes] = await Promise.all([
        getSystemMessageList({ pageNum: 1, pageSize: 50 }),
        getSystemMessageUnreadCount(),
      ])
      if (listRes.code === 0) {
        systemMessages.value = unwrapPageRecords(listRes.data)
      }
      if (countRes.code === 0) {
        systemUnread.value = Number(countRes.data) || 0
        messageStore.setSystemUnreadCount(systemUnread.value)
      }
    } catch {
      /* 已提示 */
    }
  }

  async function loadGroupSessions() {
    groupListLoading.value = true
    groupListError.value = ''
    try {
      const res = await getGroupChatSessions({ pageNum: 1, pageSize: 50 })
      if (res.code === 0) {
        groupSessions.value = unwrapPageRecords(res.data)
      } else {
        groupListError.value = res.message || '群聊加载失败'
      }
    } catch {
      groupListError.value = '群聊加载失败'
    } finally {
      groupListLoading.value = false
    }
  }

  async function applyOpenTarget() {
    const t = messageCenterUi.openTarget
    if (!t?.userId) return
    const rawId = t.userId
    const existing = sessionList.value.find((s) => String(s.user?.id) === String(rawId))
    if (existing) {
      await selectPmSession(existing)
      return
    }
    const virtualSession = {
      user: {
        id: Number(rawId),
        nickname: (t.nickname || '').trim() || `用户${rawId}`,
        avatarUrl: t.avatarUrl || '',
      },
      unReadMessage: 0,
      lastMessage: '',
      lastMessageTime: null,
      _virtual: true,
    }
    if (!sessionList.value.some((s) => String(s.user?.id) === String(rawId))) {
      sessionList.value.unshift(virtualSession)
    }
    await selectPmSession(virtualSession)
  }

  async function bootstrap() {
    if (!userStore.isLoggedIn) {
      router.push('/sign-in')
      return
    }
    if (userStore.isLoggedIn) {
      initWebSocket()
      await userStore.fetchUserInfo()
    }
    await Promise.all([loadSessions(), loadGroupSessions(), loadSystemMessages()])
    await applyOpenTarget()
  }

  onMounted(() => {
    if (messageCenterUi.visible) {
      bootstrap()
      startOnlinePolling()
    }
  })

  onUnmounted(() => {
    stopOnlinePolling()
  })

  watch(() => messageCenterUi.visible, (v) => {
    if (v) {
      bootstrap()
      startOnlinePolling()
    } else {
      stopOnlinePolling()
      currentSession.value = null
      currentGroupSession.value = null
      currentSystemGroup.value = null
      messages.value = []
      focusedConvKey.value = null
      peerOnline.value = false
      selfOnline.value = false
    }
  })

  watch(() => messageStore.incomingMessage, async (newMsg) => {
    if (!newMsg || newMsg.type !== 'message') return
    const onMessageRoute = router.currentRoute.value.name === 'messages'
    if (!messageCenterUi.visible && !onMessageRoute) return
    const fromId = Number(newMsg.fromUserId)
    const dbMessageId = coerceMessageId(newMsg.dbMessageId)
    const currentId = currentSession.value?.user?.id ? Number(currentSession.value.user.id) : null

    if (currentId && fromId === currentId && !currentSession.value?._virtual) {
      if (Number.isFinite(dbMessageId) && dbMessageId > 0) {
        try {
          const res = await getMessageDetailById(dbMessageId)
          if (res.code === 0 && res.data?.message) {
            const mid = coerceMessageId(res.data.message.id)
            const dup =
              Number.isFinite(mid)
              && messages.value.some((m) => coerceMessageId(m.message?.id) === mid)
            if (!dup) messages.value.push(res.data)
          } else {
            await loadMessagesForPeer(currentId)
          }
        } catch {
          await loadMessagesForPeer(currentId)
        }
      } else {
        await loadMessagesForPeer(currentId)
      }
      await nextTick()
      scrollToBottom()
      await markRead(fromId)
      await syncPmUnreadFromServer()
    } else {
      loadSessions()
    }
  })

  watch(() => messageStore.groupMessageSignal, async (newMsg) => {
    if (!newMsg || newMsg.type !== 'group_message') return
    if (!messageCenterUi.visible && router.currentRoute.value.name !== 'messages') return
    const groupId = Number(newMsg.groupId)
    const currentGroupId = currentGroupSession.value?.groupId
      ? Number(currentGroupSession.value.groupId)
      : null
    if (currentGroupId && groupId === currentGroupId) {
      await loadMessagesForGroup(groupId)
      await markCurrentGroupRead()
      await loadGroupSessions()
      await nextTick()
      scrollToBottom()
    } else {
      await loadGroupSessions()
    }
  })

  watch(() => messageStore.systemMessageSignal, () => {
    if (messageCenterUi.visible) loadSystemMessages()
  })

  watch(() => messageStore.readReceiptSignal, async (sig) => {
    if (!sig) return
    const peerId = Number(sig.readerUserId)
    const cur =
      currentSession.value?.user?.id != null ? Number(currentSession.value.user.id) : null
    if (cur != null && peerId === cur && !currentSession.value?._virtual) {
      const mid = sig.messageId != null ? coerceMessageId(sig.messageId) : NaN
      if (Number.isFinite(mid) && mid > 0) {
        const row = messages.value.find((m) => coerceMessageId(m.message?.id) === mid)
        if (row?.message && Number(row.message.state) !== 2) row.message.state = 1
      } else {
        messages.value.forEach((item) => {
          if (item.isOwner && Number(item.message?.state) === 0) item.message.state = 1
        })
      }
      await nextTick()
      scrollToBottom()
    }
    messageStore.clearReadReceiptSignal()
  })

  async function onDialogOpened() {
    if (currentSession.value && !currentSession.value._virtual && messages.value.length) {
      await scrollToBottom()
    }
  }

  function handleClose() {
    messageCenterUi.close()
    if (router.currentRoute.value.name === 'messages') {
      router.replace('/')
    }
  }

  async function loadSessions() {
    const res = await getSessionList({ pageNum: 1, pageSize: 50 })
    if (res.code === 0) {
      sessionList.value = unwrapPageRecords(res.data).filter((s) => s?.user?.id != null)
    }
  }

  function mapGroupMessage(row) {
    const sender = row.sender || {}
    return {
      isOwner: row.isOwner,
      user: sender,
      message: {
        id: row.id,
        groupId: row.groupId,
        messageType: row.messageType,
        content: row.content,
        mediaUrl: Number(row.messageType) === 1 ? row.content : undefined,
        createTime: row.createTime,
        updateTime: row.updateTime,
        state: row.status,
      },
      rawGroupMessage: row,
    }
  }

  async function loadMessagesForGroup(groupId) {
    const res = await getGroupChatMessages(groupId, {
      pageNum: 1,
      pageSize: 100,
    })
    if (res.code === 0) {
      messages.value = unwrapPageRecords(res.data).map(mapGroupMessage)
      await scrollToBottom()
    }
  }

  async function loadMessagesForPeer(peerUserId) {
    const res = await getMessageList({
      receiveId: peerUserId,
      pageNum: 1,
      pageSize: 100,
    })
    if (res.code === 0) {
      messages.value = unwrapPageRecords(res.data)
      await scrollToBottom()
    }
  }

  async function refreshOnlineStatus() {
    const selfId = userStore.id
    if (selfId) {
      try {
        const res = await getUserIsOnline(selfId)
        selfOnline.value = res?.code === 0 && res.data === true
      } catch {
        selfOnline.value = false
      }
    }
    const peerId = currentSession.value?.user?.id
    if (peerId && !currentSession.value?._virtual) {
      try {
        const res = await getUserIsOnline(peerId)
        peerOnline.value = res?.code === 0 && res.data === true
      } catch {
        peerOnline.value = false
      }
    } else {
      peerOnline.value = false
    }
  }

  function startOnlinePolling() {
    stopOnlinePolling()
    refreshOnlineStatus()
    onlinePollTimer = setInterval(refreshOnlineStatus, 20_000)
  }

  function stopOnlinePolling() {
    if (onlinePollTimer) {
      clearInterval(onlinePollTimer)
      onlinePollTimer = null
    }
  }

  async function syncPmUnreadFromServer() {
    try {
      const res = await getUnReadCount()
      if (res?.code === 0) {
        messageStore.setUnreadCount(Number(res.data) || 0, { keepTip: messageStore.showTip })
      }
    } catch {
      /* ignore */
    }
    await loadSessions()
  }

  async function selectPmSession(session) {
    currentSystemGroup.value = null
    currentGroupSession.value = null
    currentSession.value = session
    focusedConvKey.value = `pm-${session.user?.id}`
    await refreshOnlineStatus()
    if (session._virtual) {
      messages.value = []
      await nextTick()
      return
    }
    await loadMessagesForPeer(session.user?.id)
    await scrollToBottom()
    if ((session.unReadMessage || 0) > 0) {
      await markRead(session.user?.id)
      session.unReadMessage = 0
      const listItem = sessionList.value.find((s) => s.user?.id === session.user?.id)
      if (listItem) listItem.unReadMessage = 0
      await syncPmUnreadFromServer()
    }
  }

  async function selectSysGroup(item) {
    currentSession.value = null
    currentGroupSession.value = null
    currentSystemGroup.value = {
      groupId: item.groupId,
      name: item.name,
      messages: item.messages,
    }
    focusedConvKey.value = item.key
    let marked = 0
    for (const m of item.messages) {
      if (Number(m.state) !== 0) continue
      try {
        await markSystemMessageRead(m.id)
        m.state = 1
        marked += 1
      } catch {
        /* ignore */
      }
    }
    if (marked > 0) {
      systemUnread.value = Math.max(0, systemUnread.value - marked)
      messageStore.setSystemUnreadCount(systemUnread.value)
    }
  }

  async function selectGroupSession(group) {
    currentSession.value = null
    currentSystemGroup.value = null
    currentGroupSession.value = group
    focusedConvKey.value = `group-${group.groupId}`
    peerOnline.value = false
    await loadMessagesForGroup(group.groupId)
    await markCurrentGroupRead()
    await loadGroupSessions()
  }

  async function selectListItem(item) {
    focusedConvKey.value = item.key
    if (item.kind === 'pm') await selectPmSession(item.session)
    else if (item.kind === 'group') await selectGroupSession(item.group)
    else if (item.kind === 'sys-group') await selectSysGroup(item)
  }

  function onConvFocus(item) {
    focusedConvKey.value = item.key
  }

  function onConvBlur(item) {
    if (focusedConvKey.value === item.key) focusedConvKey.value = null
  }

  function onDialogBlurRoot(e) {
    if (!e.currentTarget.contains(e.relatedTarget)) {
      focusedConvKey.value = null
    }
  }

  async function handleRecall(msg) {
    const mid = coerceMessageId(msg?.message?.id)
    if (!Number.isFinite(mid) || mid <= 0) {
      ElMessage.warning('消息未同步完成，请稍后再试或刷新聊天记录')
      return
    }
    try {
      const res = await recallMessage(mid)
      if (res.code === 0) {
        msg.message.state = 2
        ElMessage.success('消息已撤回')
      } else {
        ElMessage.error(res.message || '撤回失败')
      }
    } catch {
      ElMessage.error('撤回操作异常')
    }
  }

  async function sendMsg() {
    const text = sendContent.value.trim()
    if (!text || (!currentSession.value && !currentGroupSession.value)) return
    sending.value = true
    try {
      if (currentGroupSession.value) {
        const res = await sendGroupChatMessage({
          groupId: currentGroupSession.value.groupId,
          messageType: 0,
          content: text,
        })
        if (res.code === 0 && res.data) {
          messages.value.push(mapGroupMessage(res.data))
          sendContent.value = ''
          await nextTick()
          scrollToBottom()
          await loadGroupSessions()
        }
        return
      }
      const res = await sendMessage({
        receiveUserId: currentSession.value.user?.id,
        content: text,
      })
      if (res.code === 0) {
        const serverMsg = res.data
        const realId = coerceMessageId(serverMsg?.id)
        messages.value.push({
          isOwner: true,
          message: {
            id: Number.isFinite(realId) && realId > 0 ? realId : Date.now(),
            messageType: 0,
            content: serverMsg?.content ?? text,
            createTime: serverMsg?.createTime ?? new Date().toISOString(),
            state: serverMsg?.state != null ? Number(serverMsg.state) : 0,
          },
        })
        sendContent.value = ''
        await nextTick()
        scrollToBottom()
        await loadSessions()
        if (currentSession.value?._virtual) {
          const uid = currentSession.value.user?.id
          const match = sessionList.value.find((s) => String(s.user?.id) === String(uid))
          if (match) currentSession.value = match
        }
      }
    } finally {
      sending.value = false
    }
  }

  async function markCurrentGroupRead() {
    const groupId = currentGroupSession.value?.groupId
    if (!groupId) return
    const latest = messages.value[messages.value.length - 1]?.message
    try {
      await markGroupChatRead(groupId, latest?.id)
    } catch {
      /* ignore */
    }
  }

  function openCreateGroup() {
    groupCreateForm.value = { name: '', groupType: 0, intro: '' }
    groupCreateVisible.value = true
  }

  async function submitCreateGroup() {
    const name = groupCreateForm.value.name.trim()
    if (!name) {
      ElMessage.warning('请输入群名称')
      return
    }
    creatingGroup.value = true
    try {
      const res = await createGroupChat({
        name,
        groupType: Number(groupCreateForm.value.groupType),
        intro: groupCreateForm.value.intro?.trim() || undefined,
      })
      if (res.code === 0) {
        ElMessage.success('群聊已创建')
        groupCreateVisible.value = false
        await loadGroupSessions()
        const created = groupSessions.value.find((item) => String(item.groupId) === String(res.data?.id))
        if (created) await selectGroupSession(created)
      }
    } finally {
      creatingGroup.value = false
    }
  }

  async function refreshCurrentGroupSession() {
    await loadGroupSessions()
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    const next = groupSessions.value.find((item) => String(item.groupId) === String(gid))
    if (next) currentGroupSession.value = next
  }

  async function openPublicGroups() {
    publicGroupVisible.value = true
    await loadPublicGroups()
  }

  async function loadPublicGroups() {
    publicGroupsLoading.value = true
    try {
      const res = await getPublicGroupChats({ pageNum: 1, pageSize: 50 })
      if (res.code === 0) {
        publicGroups.value = unwrapPageRecords(res.data)
      }
    } finally {
      publicGroupsLoading.value = false
    }
  }

  function isJoinedPublicGroup(group) {
    return groupSessions.value.some((item) => String(item.groupId) === String(group.id))
  }

  async function joinPublicGroup(group) {
    if (!group?.id || isJoinedPublicGroup(group)) return
    try {
      const res = await joinPublicGroupChat(group.id)
      if (res.code === 0) {
        ElMessage.success('已加入群聊')
        await loadGroupSessions()
        const joined = groupSessions.value.find((item) => String(item.groupId) === String(group.id))
        if (joined) await selectGroupSession(joined)
        await loadPublicGroups()
      }
    } catch {
      /* 拦截器已提示 */
    }
  }

  async function openGroupMembers() {
    if (!currentGroupSession.value?.groupId) return
    groupMembersVisible.value = true
    await loadGroupMembers()
  }

  async function loadGroupMembers() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    groupMembersLoading.value = true
    try {
      const res = await getGroupChatMembers(gid)
      if (res.code === 0) groupMembers.value = Array.isArray(res.data) ? res.data : []
    } finally {
      groupMembersLoading.value = false
    }
  }

  function openGroupEdit() {
    if (!currentGroupSession.value) return
    groupEditForm.value = {
      name: currentGroupSession.value.name || '',
      groupType: Number(currentGroupSession.value.groupType) || 0,
      intro: currentGroupSession.value.intro || '',
    }
    groupEditVisible.value = true
  }

  async function submitGroupEdit() {
    const gid = currentGroupSession.value?.groupId
    const name = groupEditForm.value.name.trim()
    if (!gid || !name) {
      ElMessage.warning('请输入群名称')
      return
    }
    savingGroupEdit.value = true
    try {
      const res = await updateGroupChat(gid, {
        name,
        groupType: Number(groupEditForm.value.groupType),
        intro: groupEditForm.value.intro?.trim() || undefined,
      })
      if (res.code === 0) {
        ElMessage.success('群资料已更新')
        groupEditVisible.value = false
        await refreshCurrentGroupSession()
      }
    } finally {
      savingGroupEdit.value = false
    }
  }

  async function inviteMemberById() {
    const gid = currentGroupSession.value?.groupId
    const inviteeUserId = Number(inviteUserIdInput.value)
    if (!gid || !Number.isFinite(inviteeUserId) || inviteeUserId <= 0) {
      ElMessage.warning('请输入正确的用户 ID')
      return
    }
    invitingMember.value = true
    try {
      const res = await inviteGroupChatMember(gid, inviteeUserId)
      if (res.code === 0) {
        ElMessage.success('已邀请成员加入')
        inviteUserIdInput.value = ''
        await refreshCurrentGroupSession()
        await loadGroupMembers()
      }
    } finally {
      invitingMember.value = false
    }
  }

  async function leaveCurrentGroup() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    await ElMessageBox.confirm('确认退出当前群聊吗？退出后不会再接收新消息。', '退出群聊', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await leaveGroupChat(gid)
    ElMessage.success('已退出群聊')
    currentGroupSession.value = null
    messages.value = []
    await loadGroupSessions()
  }

  async function dissolveCurrentGroup() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    await ElMessageBox.confirm('确认解散当前群聊吗？解散后成员将无法继续聊天。', '解散群聊', {
      confirmButtonText: '解散',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await dissolveGroupChat(gid)
    ElMessage.success('群聊已解散')
    currentGroupSession.value = null
    messages.value = []
    await loadGroupSessions()
  }

  async function removeMember(member) {
    const gid = currentGroupSession.value?.groupId
    const targetUserId = member?.user?.id
    if (!gid || !targetUserId) return
    await ElMessageBox.confirm(`确认移除「${member.user?.nickname || targetUserId}」吗？`, '移除成员', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await removeGroupChatMember(gid, targetUserId)
    ElMessage.success('已移除成员')
    await refreshCurrentGroupSession()
    await loadGroupMembers()
  }

  async function muteMember(member, minutes) {
    const gid = currentGroupSession.value?.groupId
    const targetUserId = member?.user?.id
    if (!gid || !targetUserId) return
    const title = minutes > 0 ? '禁言成员' : '解除禁言'
    const text = minutes > 0 ? '确认禁言该成员 30 分钟吗？' : '确认解除该成员禁言吗？'
    await ElMessageBox.confirm(text, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await muteGroupChatMember(gid, targetUserId, minutes)
    ElMessage.success(minutes > 0 ? '已禁言成员' : '已解除禁言')
    await loadGroupMembers()
  }

  async function reportGroupMessage(msgRow) {
    const gid = currentGroupSession.value?.groupId
    const mid = coerceMessageId(msgRow?.message?.id)
    if (!gid || !Number.isFinite(mid) || mid <= 0 || msgRow?.isOwner) return
    const { value } = await ElMessageBox.prompt('请填写举报原因', '举报群消息', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPattern: /\S+/,
      inputErrorMessage: '举报原因不能为空',
    })
    await reportGroupChatMessage(gid, {
      messageId: mid,
      reason: String(value || '').trim(),
    })
    ElMessage.success('举报已提交')
  }

  function memberRoleLabel(role) {
    return Number(role) === 0 ? '群主' : '成员'
  }

  function memberMuteLabel(member) {
    const until = parseForumDateTime(member?.muteUntil)
    if (!until || until.getTime() <= Date.now()) return ''
    return `禁言至 ${formatSessionTime(member.muteUntil)}`
  }

  function autoResizeInput() {
    const el = inputBoxRef.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 80)}px`
  }

  function triggerChatImagePick() {
    chatImageInput.value?.click()
  }

  function onChatImageFileChange(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (file) void sendChatImageMessage(file)
  }

  async function sendChatImageMessage(file) {
    const mimeOk = validateChatImageMime(file)
    if (!mimeOk.ok) {
      ElMessage.warning(mimeOk.message)
      return
    }
    const sizeOk = validateLocalImageFile(file)
    if (!sizeOk.ok) {
      ElMessage.warning(sizeOk.message)
      return
    }
    const recvId = currentSession.value?.user?.id
    if (recvId == null) return
    if (Number(recvId) === Number(userStore.id)) {
      ElMessage.warning('不能给自己发私信')
      return
    }

    const loading = openImageUploadLoading(file, '正在上传聊天图片…')
    try {
      const up = await uploadChatImage(file)
      const mediaUrl = up.data
      const dims = await readImageNaturalSize(mediaUrl)
      const isGif = file.type === 'image/gif'
      const sendRes = await sendImageMessage({
        receiveUserId: recvId,
        messageType: isGif ? 2 : 1,
        mediaUrl,
        mediaMime: isGif ? 'image/gif' : undefined,
        mediaSize: file.size,
        mediaWidth: dims.width > 0 ? dims.width : undefined,
        mediaHeight: dims.height > 0 ? dims.height : undefined,
      })
      if (sendRes.code === 0 && sendRes.data) {
        messages.value.push({ isOwner: true, message: sendRes.data })
        await nextTick()
        scrollToBottom()
        await loadSessions()
        if (currentSession.value?._virtual) {
          const uid = currentSession.value.user?.id
          const match = sessionList.value.find((s) => String(s.user?.id) === String(uid))
          if (match) currentSession.value = match
        }
      }
    } catch {
      /* 拦截器已提示 */
    } finally {
      loading.close()
    }
  }

  function triggerEmojiStickerPick() {
    emojiStickerInput.value?.click()
  }

  async function onEmojiStickerFileChange(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    const mimeOk = validateChatImageMime(file)
    if (!mimeOk.ok) {
      ElMessage.warning(mimeOk.message)
      return
    }
    const sizeOk = validateLocalImageFile(file)
    if (!sizeOk.ok) {
      ElMessage.warning(sizeOk.message)
      return
    }
    const loading = openImageUploadLoading(file, '正在上传表情…')
    try {
      await chatEmojiStore.uploadAndFavorite(file)
      ElMessage.success('已添加到我的上传')
      emojiPanelTab.value = 'uploads'
      uploadedPage.value = 1
      syncUploadedPageInput()
    } catch {
      /* 已提示 */
    } finally {
      loading.close()
    }
  }

  async function onEmojiPopoverShow() {
    try {
      await chatEmojiStore.fetchList(true)
      if (userStore.isLoggedIn && emojiPanelTab.value === 'purchased') {
        await emojiShopStore.fetchMyPacks()
      }
      syncFavoritePageInput()
      syncUploadedPageInput()
      nextTick(updatePackBarScrollState)
    } catch {
      /* store / 拦截器已提示 */
    }
  }

  async function onEmojiTabChange(name) {
    if (name === 'purchased' && userStore.isLoggedIn) {
      try {
        await emojiShopStore.fetchMyPacks()
      } catch {
        /* 已提示 */
      }
    }
    if (name === 'favorites') {
      syncFavoritePageInput()
    }
    if (name === 'uploads') {
      syncUploadedPageInput()
    }
  }

  async function sendMessageFromShopUrl(mediaUrl) {
    if (currentGroupSession.value) {
      await sendGroupEmojiMessage(mediaUrl)
      return
    }
    const recvId = currentSession.value?.user?.id
    if (recvId == null || !mediaUrl) return
    if (Number(recvId) === Number(userStore.id)) {
      ElMessage.warning('不能给自己发私信')
      return
    }
    const isGif = /\.gif(\?|#|$)/i.test(String(mediaUrl))
    try {
      const dims = await readImageNaturalSize(mediaUrl)
      const sendRes = await sendImageMessage({
        receiveUserId: recvId,
        messageType: isGif ? 2 : 1,
        mediaUrl,
        mediaMime: isGif ? 'image/gif' : undefined,
        mediaWidth: dims.width > 0 ? dims.width : undefined,
        mediaHeight: dims.height > 0 ? dims.height : undefined,
      })
      if (sendRes.code === 0 && sendRes.data) {
        messages.value.push({ isOwner: true, message: sendRes.data })
        await nextTick()
        scrollToBottom()
        await loadSessions()
      }
    } catch {
      /* 已提示 */
    }
  }

  async function sendMessageFromEmoji(emoji) {
    if (currentGroupSession.value) {
      await sendGroupEmojiMessage(emoji?.mediaUrl)
      return
    }
    const recvId = currentSession.value?.user?.id
    if (recvId == null) return
    if (Number(recvId) === Number(userStore.id)) {
      ElMessage.warning('不能给自己发私信')
      return
    }
    try {
      const sendRes = await sendImageMessage({
        receiveUserId: recvId,
        messageType: Number(emoji.mediaType) === 1 ? 2 : 1,
        mediaUrl: emoji.mediaUrl,
        mediaMime: emoji.mediaMime ?? undefined,
        mediaSize: emoji.mediaSize ?? undefined,
      })
      if (sendRes.code === 0 && sendRes.data) {
        messages.value.push({ isOwner: true, message: sendRes.data })
        await nextTick()
        scrollToBottom()
        await loadSessions()
      }
    } catch {
      /* 已提示 */
    }
  }

  async function sendGroupEmojiMessage(mediaUrl) {
    const gid = currentGroupSession.value?.groupId
    if (!gid || !mediaUrl) return
    try {
      const sendRes = await sendGroupChatMessage({
        groupId: gid,
        messageType: 1,
        content: mediaUrl,
      })
      if (sendRes.code === 0 && sendRes.data) {
        messages.value.push(mapGroupMessage(sendRes.data))
        await nextTick()
        scrollToBottom()
        await loadGroupSessions()
      }
    } catch {
      /* 已提示 */
    }
  }

  function canFavoriteChatImage(msgRow) {
    return canFavoriteChatMediaMessage(msgRow?.message)
  }

  async function favoriteChatImage(msgRow) {
    const m = msgRow?.message
    if (!canFavoriteChatMediaMessage(m)) return
    try {
      await chatEmojiStore.favoriteFromChatMessage(m)
    } catch {
      /* 已提示 */
    }
  }

  function isMediaMessage(msg) {
    const t = Number(msg?.message?.messageType)
    return t === 1 || t === 2
  }

  function bubbleAvatar(msg) {
    if (msg?.isOwner) return userStore.avatarUrl || defaultAvatar
    if (currentGroupSession.value) return msg?.user?.avatarUrl || defaultAvatar
    return currentSession.value?.user?.avatarUrl || defaultAvatar
  }

  function bubbleVipTier(msg) {
    if (msg?.isOwner) return Number(userStore.vipTier) || 0
    if (currentGroupSession.value) return Number(msg?.user?.vipTier) || 0
    return Number(currentSession.value?.user?.vipTier) || 0
  }

  function bubbleVipExpireAt(msg) {
    if (msg?.isOwner) return userStore.vipExpireAt
    if (currentGroupSession.value) return msg?.user?.vipExpireAt
    return currentSession.value?.user?.vipExpireAt
  }

  function bubbleImageStyle(message) {
    const w = message?.mediaWidth
    const h = message?.mediaHeight
    if (w != null && h != null && Number(w) > 0 && Number(h) > 0) {
      return { aspectRatio: `${w} / ${h}` }
    }
    return {}
  }

  async function scrollToBottom() {
    await nextTick()
    await new Promise((resolve) => requestAnimationFrame(resolve))
    await new Promise((resolve) => requestAnimationFrame(resolve))
    const apply = () => {
      if (msgScrollbar.value && msgContainer.value) {
        msgScrollbar.value.setScrollTop(msgContainer.value.scrollHeight + 200)
      }
    }
    apply()
    setTimeout(apply, 0)
    setTimeout(apply, 80)
  }

  function formatTime(time) {
    return formatChatBubbleTimeShanghai(time)
  }

  function formatSessionTime(time) {
    return formatChatSessionTimeShanghai(time)
  }

  function openArticleFromSystem(msg) {
    if (msg?.relatedId) router.push(`/article/${msg.relatedId}`)
  }

  return {
    ArrowLeft,
    ArrowRight,
    Bell,
    ChatLineRound,
    ChatLineSquare,
    CircleCheck,
    Close,
    Document,
    Picture,
    Plus,
    Search,
    Warning,
    UserFilled,
    activeSystemMessages,
    activeTab,
    activeChatSubtitle,
    activeChatTitle,
    emojiPackIconUrl,
    autoResizeInput,
    bubbleAvatar,
    bubbleImageStyle,
    bubbleVipExpireAt,
    bubbleVipTier,
    canFavoriteChatImage,
    chatEmojiStore,
    chatImageInput,
    currentSession,
    currentGroupSession,
    currentSystemGroup,
    defaultAvatar,
    dialogVisible,
    dissolveCurrentGroup,
    emojiPanelTab,
    emojiPopoverVisible,
    emojiShopStore,
    emojiStickerInput,
    favoriteChatImage,
    focusedConvKey,
    formatSessionTime,
    formatTime,
    handleClose,
    handleRecall,
    inputBoxRef,
    isActiveItem,
    isCurrentGroupOwner,
    isJoinedPublicGroup,
    isPrivateChat,
    isMediaMessage,
    inviteMemberById,
    inviteUserIdInput,
    invitingMember,
    joinPublicGroup,
    leaveCurrentGroup,
    listItems,
    memberMuteLabel,
    memberRoleLabel,
    messageTimeline,
    messages,
    msgContainer,
    msgScrollbar,
    onChatImageFileChange,
    onConvBlur,
    onConvFocus,
    onDialogBlurRoot,
    onDialogOpened,
    onEmojiPopoverShow,
    onEmojiTabChange,
    onEmojiStickerFileChange,
    openArticleFromSystem,
    openGroupEdit,
    openGroupMembers,
    openPublicGroups,
    openPeerProfile,
    parseSystemMessageContent,
    peerOnline,
    groupCreateForm,
    groupCreateVisible,
    groupEditForm,
    groupEditVisible,
    groupListError,
    groupListLoading,
    groupMembers,
    groupMembersLoading,
    groupMembersVisible,
    publicGroups,
    publicGroupsLoading,
    publicGroupVisible,
    removeMember,
    reportGroupMessage,
    savingGroupEdit,
    scrollToBottom,
    searchQuery,
    selfOnline,
    selectListItem,
    openCreateGroup,
    submitCreateGroup,
    submitGroupEdit,
    sendContent,
    sendMessageFromEmoji,
    sendMessageFromShopUrl,
    sendMsg,
    sending,
    creatingGroup,
    muteMember,
    sysIcon,
    sysTagClass,
    sysTagLabel,
    tabBadges,
    triggerChatImagePick,
    triggerEmojiStickerPick,
    userStore,
    viewerIsVip,
    favoriteEmojis,
    favoritePage,
    favoritePageInput,
    favoriteTotalPages,
    goFavoriteFirst,
    goFavoriteNext,
    goFavoritePrev,
    jumpFavoritePage,
    jumpUploadedPage,
    onPackBarScroll,
    paginatedFavorites,
    paginatedUploaded,
    removeEmojiKeepPopover,
    showUploadOnCurrentPage,
    packBarCanScrollLeft,
    packBarCanScrollRight,
    packBarRef,
    scrollPackBarLeft,
    scrollPackBarRight,
    selectPurchasedPack,
    selectedPurchasedPack,
    uploadedEmojis,
    uploadedPage,
    uploadedPageInput,
    uploadedTotalPages,
    goUploadedFirst,
    goUploadedNext,
    goUploadedPrev,
    visiblePacks,
    router,
  }
}

/** 从路由 query 打开消息中心（兼容 /messages?targetUserId=） */
export function openMessageCenterFromRoute(router, query) {
  const ui = useMessageCenterUiStore()
  const rawId = query?.targetUserId
  if (rawId != null && rawId !== '') {
    ui.open({
      userId: Number(rawId),
      nickname: typeof query.nickname === 'string' ? query.nickname : '',
      avatarUrl: typeof query.avatarUrl === 'string' ? query.avatarUrl : '',
    })
  } else {
    ui.open()
  }
  if (router.currentRoute.value.name === 'messages') {
    router.replace({ path: router.currentRoute.value.path, query: {} })
  }
}
