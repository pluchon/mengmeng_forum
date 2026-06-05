import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Close,
  ChatDotRound,
  Setting,
  ChatLineRound,
  Search,
  Picture,
  Plus,
  Bell,
  CircleCheck,
  Star,
  ChatLineSquare,
  Warning,
  Document,
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
import { useMessageStore } from '@/stores/message'
import { useChatEmojiStore } from '@/stores/chatEmoji'
import { useEmojiShopStore } from '@/stores/emojiShop'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'
import { validateLocalImageFile, openImageUploadLoading } from '@/utils/imageUploadFeedback'
import {
  canFavoriteChatMediaMessage,
  validateChatImageMime,
  readImageNaturalSize,
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
  /** @type {import('vue').Ref<{ groupId: string, messages: object[] } | null>} */
  const currentSystemGroup = ref(null)
  const messages = ref([])
  const sendContent = ref('')
  const sending = ref(false)
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
    let merged = []
    if (activeTab.value === 'pm') merged = pmItems
    else if (activeTab.value === 'notif') merged = sysItems
    else merged = [...pmItems, ...sysItems].sort((a, b) => {
      const ta = parseForumDateTime(a.time)?.getTime() || 0
      const tb = parseForumDateTime(b.time)?.getTime() || 0
      return tb - ta
    })
    if (!q) return merged
    return merged.filter((item) => item.name?.toLowerCase().includes(q)
      || item.preview?.toLowerCase().includes(q))
  })

  const tabBadges = computed(() => ({
    all: (messageStore.unreadCount || 0) + (systemUnread.value || 0),
    pm: messageStore.unreadCount || 0,
    notif: systemUnread.value || 0,
  }))

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

  function coerceMessageId(raw) {
    if (raw == null) return NaN
    const n = typeof raw === 'string' ? Number(raw.trim()) : Number(raw)
    return Number.isFinite(n) ? n : NaN
  }

  const activeSystemMessages = computed(() => {
    const list = currentSystemGroup.value?.messages
    return Array.isArray(list) ? list : []
  })

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
    }
    if (item.kind === 'sys-group') {
      return currentSystemGroup.value?.groupId === item.groupId && !currentSession.value
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
    await Promise.all([loadSessions(), loadSystemMessages()])
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

  async function selectListItem(item) {
    focusedConvKey.value = item.key
    if (item.kind === 'pm') await selectPmSession(item.session)
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
    if (!text || !currentSession.value) return
    sending.value = true
    try {
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
      ElMessage.success('已添加到表情')
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
  }

  async function sendMessageFromShopUrl(mediaUrl) {
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
    const d = parseForumDateTime(time)
    if (!d) return ''
    return d.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
      timeZone: 'Asia/Shanghai',
    })
  }

  function formatSessionTime(time) {
    const d = parseForumDateTime(time)
    if (!d) return ''
    const now = new Date()
    const opts = { timeZone: 'Asia/Shanghai' }
    const sameDay =
      d.toLocaleDateString('zh-CN', opts) === now.toLocaleDateString('zh-CN', opts)
    if (sameDay) {
      return d.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        timeZone: 'Asia/Shanghai',
      })
    }
    return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric', timeZone: 'Asia/Shanghai' })
  }

  function openArticleFromSystem(msg) {
    if (msg?.relatedId) router.push(`/article/${msg.relatedId}`)
  }

  return {
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
    activeSystemMessages,
    activeTab,
    emojiPackIconUrl,
    autoResizeInput,
    bubbleImageStyle,
    canFavoriteChatImage,
    chatEmojiStore,
    chatImageInput,
    currentSession,
    currentSystemGroup,
    defaultAvatar,
    dialogVisible,
    emojiPanelTab,
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
    isMediaMessage,
    listItems,
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
    parseSystemMessageContent,
    peerOnline,
    scrollToBottom,
    searchQuery,
    selfOnline,
    selectListItem,
    sendContent,
    sendMessageFromEmoji,
    sendMessageFromShopUrl,
    sendMsg,
    sending,
    sysIcon,
    sysTagClass,
    sysTagLabel,
    tabBadges,
    triggerChatImagePick,
    triggerEmojiStickerPick,
    userStore,
    viewerIsVip,
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
