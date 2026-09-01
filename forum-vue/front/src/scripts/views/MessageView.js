import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import {
  Close,
  Setting,
  ChatLineRound,
  Search,
  Plus,
  ArrowRight,
  ArrowLeft,
  ArrowDown,
  Top,
  Bell,
  Download,
  MuteNotification,
  CircleCheck,
  Star,
  ChatLineSquare,
  Warning,
  Document,
  Promotion,
  UserFilled,
  Headset,
  Delete,
  RefreshLeft,
  Back,
} from '@element-plus/icons-vue'
import {
  Image as ImageIcon,
  AtSign,
  Smile,
  Trash2,
  ShieldCheck,
  LoaderCircle,
  RotateCcw,
} from '@lucide/vue'
import { useUserStore } from '@/stores/user'
import { downloadImageByUrl, guessImageFileName } from '@/utils/imageDownload'
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
  uploadChatImages,
  sendAlbumMessage,
  sendImageMessage,
  searchMessageSessions,
  hideMessageSession,
  restoreMessageSession,
  getHiddenMessageSessions,
  reportChatMessage,
  muteMessageSession,
  pinMessageSession,
} from '@/api/message'
import { getShopEmojiAvailability } from '@/api/shop'
import { getUserIsOnline } from '@/api/user'
import {
  getSystemMessageList,
  getSystemMessageUnreadCount,
  markSystemMessageRead,
} from '@/api/systemMessage'
import {
  createGroupChat,
  acceptGroupInvite,
  approveGroupJoinRequest,
  declineGroupInvite,
  dissolveGroupChat,
  getGroupChatMessages,
  getGroupChatMembers,
  getGroupJoinRequest,
  getGroupChatSessions,
  getOwnedGroupChats,
  getAppliedGroupJoinRequests,
  getReceivedGroupJoinRequests,
  inviteGroupChatMember,
  leaveGroupChat,
  markGroupChatRead,
  markReceivedGroupJoinRequestsRead,
  markAppliedGroupJoinRequestsRead,
  muteGroupChatMember,
  rejectGroupJoinRequest,
  removeGroupChatMember,
  sendGroupChatMessage,
  sendGroupChatAlbum,
  recallGroupChatMessage,
  searchGroupChatSessions,
  updateGroupChat,
  updateGroupMemberRole,
  updateMyGroupRemark,
  uploadGroupAvatar,
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
  isEmojiShopMediaUrl,
} from '@/utils/chatMedia'
import { isVipActive } from '@/utils/vip'
import { getEnterToSendEnabled, onEnterToSendChanged } from '@/utils/chatSendPreference'
import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'
import emojiPersonEmptyUrl from '@/assets/images/emjio_person_empty.png'
import chatUnselectUrl from '@/assets/images/chat_un_select.png'
import searchChatEmptyUrl from '@/assets/images/search_chat_empty.png'

const SYS_TYPE_ICON = {
  1: CircleCheck,
  2: Warning,
  3: Warning,
  4: Document,
  99: Bell,
}

const SYS_GROUP_META = {
  joinRequest: { key: 'sys-group-join-request', name: '进群申请', listIcon: UserFilled },
  audit: { key: 'sys-group-audit', name: '帖子审核', listIcon: Document },
  musicAudit: { key: 'sys-group-music-audit', name: '歌曲审核', listIcon: Headset },
  tag: { key: 'sys-group-tag', name: '标签通知', listIcon: Document },
  report: { key: 'sys-group-report', name: '举报结果', listIcon: Warning },
  notice: { key: 'sys-group-notice', name: '系统公告', listIcon: Bell },
  other: { key: 'sys-group-other', name: '系统通知', listIcon: Bell },
}

const GROUP_INVITE_CARD_PREFIX = '[[GROUP_INVITE:'
const JOIN_REQUEST_PAGE_SIZE = 7
const SYSTEM_NOTIFY_PAGE_SIZE = 6

function systemGroupCategory(groupId) {
  if (groupId === 'audit') return 'AUDIT'
  if (groupId === 'tag') return 'TAG'
  if (groupId === 'musicAudit') return 'MUSIC'
  if (groupId === 'report') return 'REPORT'
  return null
}

function resolvePrivateAuditFailed(row) {
  if (!row) return false
  if (row.auditFailed === true || row.message?.auditFailed === true) return true
  return Number(row.message?.state) === 3
}

function resolveGroupAuditFailed(row) {
  if (!row) return false
  if (row.auditFailed === true) return true
  if (row.message?.auditFailed === true) return true
  if (Number(row.status) === 4) return true
  return Number(row.message?.state) === 4
}

function getSysGroupId(type) {
  const t = Number(type)
  if (t === 1 || t === 2 || t === 3) return 'audit'
  if (t === 4) return 'tag'
  if (t === 5 || t === 6 || t === 7) return 'report'
  if (t === 99) return 'notice'
  return 'other'
}

function getSysGroupIdForMessage(msg) {
  if (isMusicAuditMessageStatic(msg)) return 'musicAudit'
  return getSysGroupId(msg?.type)
}

function parseSystemMessagePayloadStatic(msg) {
  const raw = msg?.payload
  if (raw == null || raw === '') return null
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function isMusicAuditMessageStatic(msg) {
  const title = String(msg?.title || '')
  if (title.includes('歌曲审核')) return true
  const payload = parseSystemMessagePayloadStatic(msg)
  if (!payload) return false
  const kind = String(payload.kind || payload.targetType || '').toLowerCase()
  return kind === 'music' || payload.musicId != null
}

function sortByTimeDesc(a, b) {
  const ta = parseForumDateTime(a?.createTime ?? a?.time)?.getTime() || 0
  const tb = parseForumDateTime(b?.createTime ?? b?.time)?.getTime() || 0
  return tb - ta
}

export function useMessageView() {
  const router = useRouter()
  const userStore = useUserStore()
  const { initWebSocket, notifyConnected, notifyReconnectedSignal } = useWebSocket()
  const messageStore = useMessageStore()
  const messageCenterUi = useMessageCenterUiStore()
  const chatEmojiStore = useChatEmojiStore()
  const emojiShopStore = useEmojiShopStore()
  const defaultAvatar = DEFAULT_AVATAR

  const emojiPanelTab = ref('favorites')
  const emojiPopoverVisible = ref(false)
  const activeTab = ref('pm')
  const focusedConvKey = ref(null)

  const msgScrollbar = ref()
  const msgContainer = ref()
  const chatImageInput = ref(null)
  const emojiStickerInput = ref(null)
  const inputBoxRef = ref(null)
  const searchQuery = ref('')
  const notificationSearch = ref('')
  const notificationPage = ref(1)
  const notificationTotal = ref(0)
  const enterToSendEnabled = ref(getEnterToSendEnabled())
  const chatReportDialogVisible = ref(false)
  const chatReportSubmitting = ref(false)
  const pendingChatReport = ref(null)
  const hiddenManagementMode = ref(false)
  const hiddenSessionList = ref([])
  const textSearchMatches = ref(new Map())
  const textSearchLoading = ref(false)
  const groupTextSearchMatches = ref(new Map())
  const groupTextSearchLoading = ref(false)

  const sessionList = ref([])
  const systemMessages = ref([])
  const systemUnread = ref(0)
  const currentSession = ref(null)
  const currentGroupSession = ref(null)
  const currentSystemGroup = ref(null)
  const messages = ref([])
  const sendContent = ref('')
  const sending = ref(false)
  const pendingAlbumFiles = ref([])
  const albumPreviewVisible = ref(false)
  const albumPreviewImages = ref([])
  const albumPreviewIndex = ref(0)
  const groupSessions = ref([])
  const groupCreateVisible = ref(false)
  const groupCreateForm = ref({ name: '', groupType: 0, intro: '' })
  const creatingGroup = ref(false)
  const groupListLoading = ref(false)
  const groupListError = ref('')
  const receivedJoinRequests = ref([])
  const appliedJoinRequests = ref([])
  const joinRequestsLoading = ref(false)
  const groupSettingsVisible = ref(false)
  const groupSettingsPortalReady = ref(false)
  const groupMembers = ref([])
  const groupMembersLoading = ref(false)
  const groupMembersPage = ref(1)
  const groupMembersTotal = ref(0)
  const groupMemberSearch = ref('')
  const mentionMembers = ref([])
  const mentionMembersTotal = ref(0)
  const mentionMembersPage = ref(1)
  const groupAdminVisible = ref(false)
  const groupAdminMembers = ref([])
  const groupAdminSearch = ref('')
  const groupAdminPage = ref(1)
  const groupAdminTotal = ref(0)
  const groupAdminLoading = ref(false)
  const groupAdminUpdatingId = ref(null)
  const groupEditForm = ref({ name: '', groupType: 0, intro: '', avatarUrl: '' })
  const groupEditSnapshot = ref('')
  const groupMemberSettingsSnapshot = ref('')
  const groupRemarkForm = ref({ remarkName: '', notifyMode: 0 })
  const groupAvatarInputRef = ref(null)
  const groupIntroEditorRef = ref(null)
  const uploadingGroupAvatar = ref(false)
  const savingGroupEdit = ref(false)
  const savingGroupRemark = ref(false)
  const groupTypeSwitchLocked = ref(false)
  const mentionPopoverVisible = ref(false)
  const mentionSearch = ref('')
  const ownedGroupInviteVisible = ref(false)
  const ownedGroupSearch = ref('')
  const ownedGroupPage = ref(1)
  const ownedGroupTotal = ref(0)
  const ownedGroups = ref([])
  const ownedGroupsLoading = ref(false)
  const invitingGroupId = ref(null)
  const groupInviteCards = ref({})
  const replyTarget = ref(null)
  const peerOnline = ref(false)
  const selfOnline = notifyConnected
  // 是否还跟着最新消息；离底时新消息只攒角标不强行拉回
  const following = ref(true)
  const pendingNewCount = ref(0)
  // 正在设置免打扰，防连点
  const mutingSession = ref(false)
  // 正在置顶哪个会话，防连点
  const pinningPeerId = ref(null)
  // 会话列表分页游标
  const sessionPage = ref(1)
  const sessionHasMore = ref(false)
  const sessionLoading = ref(false)
  const hiddenSessionPage = ref(1)
  const hiddenSessionHasMore = ref(false)
  const hiddenSessionLoading = ref(false)
  // 会话内消息的向上翻页游标
  const messagePage = ref(1)
  const messageHasMore = ref(false)
  const loadingOlderMessages = ref(false)
  const recallClock = ref(Date.now())
  const expiredRecallIds = ref(new Set())
  const mediaNaturalSizes = ref({})
  let onlinePollTimer = null
  let recallClockTimer = null
  let groupTypeSwitchTimer = null
  let searchTimer = null
  let searchRequestSequence = 0
  let groupSearchRequestSequence = 0
  let groupMemberSearchTimer = null
  let mentionSearchTimer = null
  let groupAdminSearchTimer = null
  let notificationSearchTimer = null
  let stopEnterToSendListener = null

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
    const privateSessions = hiddenManagementMode.value ? hiddenSessionList.value : sessionList.value
    const pmItems = privateSessions.map((s) => {
      const peerUserId = Number(s.user?.id)
      const name = s.user?.nickname || '用户'
      const matched = textSearchMatches.value.get(peerUserId)
      const nameMatched = !!q && name.toLowerCase().includes(q)
      const preview = matched?.matchedContent || previewForPrivateMessage(s.lastMessage)
      return {
        key: `pm-${s.user?.id}`,
        kind: 'pm',
        session: s,
        pinned: !!s.pinnedAt,
        name,
        time: matched?.matchedMessageTime || s.lastMessageTime,
        preview,
        unread: Number(s.unReadMessage) || 0,
        user: s.user,
        nameMatched,
        previewMatched: !!matched,
        hidden: hiddenManagementMode.value,
      }
    }).filter((item) => !q || item.nameMatched || item.previewMatched)
    const groupMap = new Map()
    for (const m of systemMessages.value) {
      const gid = getSysGroupIdForMessage(m)
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
    const joinNotificationItems = [
      ...receivedJoinRequests.value.map((item) => ({ ...item, viewerSide: 'owner' })),
      ...appliedJoinRequests.value.map((item) => ({ ...item, viewerSide: 'applicant' })),
    ].sort(sortByTimeDesc)
    const pendingJoinCount = receivedJoinRequests.value.filter((item) => Number(item.status) === 0).length
    const joinRequestItem = {
      key: SYS_GROUP_META.joinRequest.key,
      kind: 'join-request-group',
      groupId: 'joinRequest',
      name: SYS_GROUP_META.joinRequest.name,
      requests: joinNotificationItems,
      time: joinNotificationItems[0]?.createTime,
      preview: pendingJoinCount > 0 ? `${pendingJoinCount} 条待处理申请` : '暂无待处理申请',
      unread: pendingJoinCount,
      listIcon: SYS_GROUP_META.joinRequest.listIcon,
    }
    const notifItems = [joinRequestItem, ...sysItems]
    const groupItems = groupSessions.value.map((s) => {
      const groupId = Number(s.groupId)
      const name = s.name || '群聊'
      const matched = groupTextSearchMatches.value.get(groupId)
      return {
        key: `group-${s.groupId}`,
        kind: 'group',
        group: s,
        name,
        time: matched?.matchedMessageTime || s.lastMessageTime,
        preview: matched?.matchedContent || previewForPrivateMessage(s.lastMessage),
        unread: Number(s.unreadCount) || 0,
        listIcon: UserFilled,
        nameMatched: !!q && name.toLowerCase().includes(q),
        previewMatched: !!matched,
      }
    })
    let merged = []
    if (activeTab.value === 'pm') merged = pmItems
    else if (activeTab.value === 'group') merged = groupItems
    else if (activeTab.value === 'notif') merged = notifItems
    else merged = pmItems
    if (activeTab.value === 'pm') return merged
    if (!q) return merged
    if (activeTab.value === 'group') {
      return merged.filter((item) => item.nameMatched || item.previewMatched)
    }
    return merged.filter((item) => item.name?.toLowerCase().includes(q)
      || item.preview?.toLowerCase().includes(q))
  })

  const privateSearchEmpty = computed(() =>
    (activeTab.value === 'pm' && !!searchQuery.value.trim() && !textSearchLoading.value
      && listItems.value.length === 0)
    || (activeTab.value === 'group' && !!searchQuery.value.trim() && !groupTextSearchLoading.value
      && listItems.value.length === 0),
  )

  const tabBadges = computed(() => ({
    pm: messageStore.unreadCount || 0,
    group: groupUnreadCount.value,
    notif: (systemUnread.value || 0) + joinRequestUnreadCount.value,
  }))

  const groupUnreadCount = computed(() =>
    groupSessions.value.reduce((sum, item) => sum + (Number(item.unreadCount) || 0), 0),
  )

  const joinRequestUnreadCount = computed(() =>
    receivedJoinRequests.value.filter((item) => Number(item.ownerReadState) === 0).length
      + appliedJoinRequests.value.filter((item) => Number(item.applicantReadState) === 0).length,
  )

  // 归档列表里的会话是只读的，恢复后才能发言
  const sessionReadonly = computed(() => hiddenManagementMode.value)

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

  const isCurrentGroupManager = computed(() =>
    isCurrentGroupOwner.value || Number(currentGroupSession.value?.myRole) === 2,
  )

  const groupMembersTotalPages = computed(() =>
    Math.max(1, Math.ceil(groupMembersTotal.value / 4)),
  )

  const myGroupMember = computed(() => ({
    role: currentGroupSession.value?.myRole,
    remarkName: currentGroupSession.value?.remarkName || '',
    notifyMode: Number(currentGroupSession.value?.notifyMode) || 0,
  }))

  const groupMemberSettingsDirty = computed(() =>
    groupMemberSettingsSnapshot.value !== serializeGroupMemberSettings(),
  )

  const groupEditDirty = computed(() =>
    isCurrentGroupOwner.value
      && (groupEditSnapshot.value !== serializeGroupEditForm() || groupMemberSettingsDirty.value),
  )

  const filteredMentionMembers = computed(() => {
    return mentionMembers.value
  })

  const mentionMembersTotalPages = computed(() =>
    Math.max(1, Math.ceil(mentionMembersTotal.value / MENTION_PAGE_SIZE)),
  )

  const paginatedMentionMembers = computed(() => mentionMembers.value)

  const paginatedGroupMembers = computed(() => groupMembers.value)

  const groupAdminTotalPages = computed(() =>
    Math.max(1, Math.ceil(groupAdminTotal.value / 5)),
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

// 一次最多上传多少张表情。图集是十张，表情商城是六十张，这里原来没有上限
// 会话列表每页条数；往下滚再续下一页
const SESSION_PAGE_SIZE = 20
// 会话内消息每页条数；向上翻更早的记录
const MESSAGE_PAGE_SIZE = 30
// 搜索只取第一页
const SEARCH_PAGE_SIZE = 50
// 入群申请每页条数
const JOIN_REQUEST_PAGE_SIZE = 50
// loadAllPages 的页数上限，防止 hasNextPage 异常时无限翻页
const MAX_AUTO_PAGES = 10

const EMOJI_UPLOAD_BATCH_MAX = 20

const FAVORITES_PAGE_SIZE = 8
const MENTION_PAGE_SIZE = 5
const GROUP_NOTIFY_OPTIONS = [
  { value: 0, label: '关闭' },
  { value: 1, label: '仅@提醒' },
  { value: 2, label: '完全不提醒' },
]
  const favoritePage = ref(1)
  const uploadedPage = ref(1)
  const uploadedPendingSlots = ref([])
  const selectedPurchasedPackId = ref(null)
  const packBarRef = ref(null)
  const packBarCanScrollLeft = ref(false)
  const packBarCanScrollRight = ref(false)

  const favoriteEmojis = computed(() => chatEmojiStore.favoriteItems)

  const uploadedEmojis = computed(() => chatEmojiStore.uploadedItems)

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
    return Math.max(1, Number(chatEmojiStore.pagination.favorite.pages) || 1)
  })

  const uploadedTotalPages = computed(() => {
    const totalImages = Math.max(0, Number(chatEmojiStore.pagination.uploaded.total) || 0)
    return Math.max(1, Math.ceil((totalImages + 1) / FAVORITES_PAGE_SIZE))
  })

  const favoritePagerTotal = computed(() => Number(chatEmojiStore.pagination.favorite.total) || 0)

  const uploadedPagerTotal = computed(() => {
    const totalImages = Math.max(0, Number(chatEmojiStore.pagination.uploaded.total) || 0)
    return totalImages + 1
  })

  const paginatedFavorites = computed(() => favoriteEmojis.value)

  const paginatedUploaded = computed(() => uploadedEmojis.value)

  const showUploadOnCurrentPage = computed(() => {
    return uploadedPage.value === uploadedTotalPages.value
  })

  async function removeEmojiKeepPopover(emojiId, source) {
    const removed = await chatEmojiStore.remove(emojiId)
    if (!removed) return
    const currentPage = source === 'uploaded' ? uploadedPage.value : favoritePage.value
    await chatEmojiStore.fetchPage(source, currentPage, FAVORITES_PAGE_SIZE)
    const totalPages = source === 'uploaded' ? uploadedTotalPages.value : favoriteTotalPages.value
    if (currentPage > totalPages) {
      await chatEmojiStore.fetchPage(source, totalPages, FAVORITES_PAGE_SIZE)
      if (source === 'uploaded') uploadedPage.value = totalPages
      else favoritePage.value = totalPages
    }
    await nextTick()
    emojiPopoverVisible.value = true
  }

  // 右侧顶部的「查看对方主页」。会话列表不再承担跳转，避免翻列表时误点
  function openCurrentPeerProfile() {
    const uid = currentSession.value?.user?.id
    if (!uid || currentSession.value?._virtual) return
    handleClose()
    router.push(`/profile/${uid}`)
  }


  async function onFavoritePageChange(page) {
    favoritePage.value = page
    await chatEmojiStore.fetchPage('favorite', page, FAVORITES_PAGE_SIZE)
  }

  async function onUploadedPageChange(page) {
    uploadedPage.value = page
    await chatEmojiStore.fetchPage('uploaded', page, FAVORITES_PAGE_SIZE)
  }

  async function onMentionMembersPageChange(page) {
    mentionMembersPage.value = page
    await loadMentionMembers()
  }

  async function onGroupAdminPageChange(page) {
    groupAdminPage.value = page
    await loadGroupAdminMembers()
  }

  async function onOwnedGroupPageChange(page) {
    ownedGroupPage.value = page
    await loadOwnedGroupsForInvite()
  }

  async function onGroupMembersPageChange(page) {
    groupMembersPage.value = page
    await loadGroupMembers()
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
  })

  watch(() => uploadedEmojis.value.length, () => {
    if (uploadedPage.value > uploadedTotalPages.value) {
      uploadedPage.value = uploadedTotalPages.value
    }
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

  const activeJoinRequests = computed(() => {
    if (currentSystemGroup.value?.groupId !== 'joinRequest') return []
    return [
      ...receivedJoinRequests.value.map((item) => ({ ...item, viewerSide: 'owner' })),
      ...appliedJoinRequests.value.map((item) => ({ ...item, viewerSide: 'applicant' })),
    ].sort(sortByTimeDesc)
  })

  const messageTimeline = computed(() => buildChatMessageTimeline(messages.value))

  function serializeGroupEditForm() {
    return JSON.stringify({
      name: groupEditForm.value.name || '',
      groupType: Number(groupEditForm.value.groupType) || 0,
      intro: groupEditForm.value.intro || '',
      avatarUrl: groupEditForm.value.avatarUrl || '',
    })
  }

  function serializeGroupMemberSettings() {
    return JSON.stringify({
      remarkName: groupRemarkForm.value.remarkName || '',
      notifyMode: Number(groupRemarkForm.value.notifyMode) || 0,
    })
  }

  function setGroupEditSnapshot() {
    groupEditSnapshot.value = serializeGroupEditForm()
  }

  function setGroupMemberSettingsSnapshot() {
    groupMemberSettingsSnapshot.value = serializeGroupMemberSettings()
  }

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
    if (item.kind === 'join-request-group') {
      return currentSystemGroup.value?.groupId === item.groupId
        && !currentSession.value
        && !currentGroupSession.value
    }
    return false
  }

  function previewForPrivateMessage(content) {
    const text = String(content || '').replace(/\s+/g, ' ').trim()
    if (text.startsWith(GROUP_INVITE_CARD_PREFIX) && text.endsWith(']]')) {
      return '进群邀请'
    }
    return text || '暂无消息'
  }

  function openGroupMemberProfile(member) {
    const uid = member?.user?.id
    if (!uid) return
    groupSettingsVisible.value = false
    handleClose()
    router.push(`/profile/${uid}`)
  }

  function openMessageSenderProfile(msgRow) {
    if (!currentGroupSession.value) return
    const uid = msgRow?.user?.id
    if (!uid) return
    handleClose()
    router.push(`/profile/${uid}`)
  }

  function memberDisplayName(member) {
    return member?.user?.nickname || `用户${member?.user?.id || ''}`
  }

  function groupTypeLabel(type) {
    return Number(type) === 1 ? '私密群' : '公开群'
  }

  function sysIcon(type) {
    return SYS_TYPE_ICON[Number(type)] || Bell
  }

  function sysTagLabel(type) {
    const t = Number(type)
    if (t === 1) return '审核通过'
    if (t === 2) return '审核驳回'
    if (t === 3) return '审核异常'
    if (t === 4) return '标签'
    if (t === 5) return '举报成功'
    if (t === 6) return '举报不通过'
    if (t === 7) return '举报异常'
    return '通知'
  }

  function sysTagClass(type) {
    const t = Number(type)
    if (t === 1) return 'mc-tag mc-tag--pass'
    if (t === 2) return 'mc-tag mc-tag--reject'
    if (t === 3) return 'mc-tag mc-tag--error'
    if (t === 5) return 'mc-tag mc-tag--pass'
    if (t === 6) return 'mc-tag mc-tag--reject'
    if (t === 7) return 'mc-tag mc-tag--error'
    return 'mc-tag mc-tag--sys'
  }

  function parseSystemMessagePayload(msg) {
    const raw = msg?.payload
    if (raw == null || raw === '') return null
    if (typeof raw === 'object') return raw
    try {
      return JSON.parse(raw)
    } catch {
      return null
    }
  }

  function isMusicAuditMessage(msg) {
    const title = String(msg?.title || '')
    if (title.includes('歌曲审核')) return true
    const payload = parseSystemMessagePayload(msg)
    if (!payload) return false
    const kind = String(payload.kind || payload.targetType || '').toLowerCase()
    return kind === 'music' || payload.musicId != null
  }

  function systemNotifyCardTitle(msg) {
    if (isMusicAuditMessage(msg)) {
      return msg?.title || '歌曲审核'
    }
    return msg?.title || currentSystemGroup.value?.name || '通知'
  }

  function parseSystemMessageContent(msg) {
    const content = msg?.content || ''
    const relatedId = msg?.relatedId
    const isMusic = isMusicAuditMessage(msg)
    const match = content.match(/《([^》]+)》/)
    if (match && relatedId) {
      const start = match.index ?? 0
      return {
        before: content.slice(0, start),
        articleTitle: match[1],
        after: content.slice(start + match[0].length),
        relatedId,
        isMusic,
      }
    }
    return { plain: content, relatedId: relatedId || null, isMusic }
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
      // 已提示
    }
  }

  async function loadGroupSessions() {
    groupListLoading.value = true
    groupListError.value = ''
    try {
      groupSessions.value = await loadAllPages((pageNum) => getGroupChatSessions({ pageNum, pageSize: 50 }))
    } catch {
      groupListError.value = '群聊加载失败'
    } finally {
      groupListLoading.value = false
    }
  }

  async function loadReceivedJoinRequests() {
    joinRequestsLoading.value = true
    try {
      // 服务端已改成数据库分页；这里取第一页即可，不再把所有页拉完。
      // 注意直调接口拿到的是响应体，要 unwrap 成数组——上游是按数组用的
      const [receivedRes, appliedRes] = await Promise.all([
        getReceivedGroupJoinRequests({ pageNum: 1, pageSize: JOIN_REQUEST_PAGE_SIZE }),
        getAppliedGroupJoinRequests({ pageNum: 1, pageSize: JOIN_REQUEST_PAGE_SIZE }),
      ])
      receivedJoinRequests.value = receivedRes.code === 0 ? unwrapPageRecords(receivedRes.data) : []
      appliedJoinRequests.value = appliedRes.code === 0 ? unwrapPageRecords(appliedRes.data) : []
    } finally {
      joinRequestsLoading.value = false
    }
  }

  async function applyOpenTarget() {
    const t = messageCenterUi.openTarget
    if (t?.groupId) {
      const rawGroupId = t.groupId
      const group = groupSessions.value.find((s) => String(s.groupId) === String(rawGroupId))
      if (group) {
        await selectGroupSession(group)
      }
      return
    }
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
    await Promise.all([loadSessions(), loadGroupSessions(), loadSystemMessages(), loadReceivedJoinRequests()])
    await applyOpenTarget()
  }

  onMounted(() => {
    enterToSendEnabled.value = getEnterToSendEnabled()
    stopEnterToSendListener = onEnterToSendChanged((enabled) => {
      enterToSendEnabled.value = enabled
    })
    recallClockTimer = setInterval(() => {
      recallClock.value = Date.now()
    }, 1000)
    if (messageCenterUi.visible) {
      bootstrap()
      startOnlinePolling()
    }
  })

  onUnmounted(() => {
    stopEnterToSendListener?.()
    stopEnterToSendListener = null
    clearPendingAlbum()
    stopOnlinePolling()
    if (recallClockTimer) {
      clearInterval(recallClockTimer)
      recallClockTimer = null
    }
    if (groupTypeSwitchTimer) {
      clearTimeout(groupTypeSwitchTimer)
      groupTypeSwitchTimer = null
    }
    if (searchTimer) {
      clearTimeout(searchTimer)
      searchTimer = null
    }
    if (groupMemberSearchTimer) clearTimeout(groupMemberSearchTimer)
    if (mentionSearchTimer) clearTimeout(mentionSearchTimer)
    if (groupAdminSearchTimer) clearTimeout(groupAdminSearchTimer)
    if (notificationSearchTimer) {
      clearTimeout(notificationSearchTimer)
      notificationSearchTimer = null
    }
  })

  watch(() => messageCenterUi.visible, (v) => {
    if (v) {
      bootstrap()
      startOnlinePolling()
    } else {
      stopOnlinePolling()
      groupSettingsVisible.value = false
      groupSettingsPortalReady.value = false
      groupAdminVisible.value = false
      albumPreviewVisible.value = false
      albumPreviewImages.value = []
      clearPendingAlbum()
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
      // 在列表变长之前记下「原本是否贴着底」
      const wasFollowing = captureFollowing()
      if (Number.isFinite(dbMessageId) && dbMessageId > 0) {
        try {
          const res = await getMessageDetailById(dbMessageId)
          if (res.code === 0 && res.data?.message) {
            const mid = coerceMessageId(res.data.message.id)
            const dup =
              Number.isFinite(mid)
              && messages.value.some((m) => coerceMessageId(m.message?.id) === mid)
            if (!dup) messages.value.push(mapPrivateMessageRow(res.data))
            if (!dup && isGroupInviteCard(res.data)) await loadGroupInviteCards()
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
      await settleIncomingScroll(wasFollowing)
      await markRead(fromId)
      await syncPmUnreadFromServer()
    } else {
      loadSessions()
    }
  })

  watch(() => messageStore.privateMessageMutationSignal, async (event) => {
    if (!event || !['private_message_recalled', 'private_message_audit_failed'].includes(event.type)) return
    const onMessageRoute = router.currentRoute.value.name === 'messages'
    if (!messageCenterUi.visible && !onMessageRoute) return
    const senderId = Number(event.fromUserId)
    const receiveId = Number(event.receiveUserId)
    const loginId = Number(userStore.id)
    const currentId = currentSession.value?.user?.id ? Number(currentSession.value.user.id) : null

    if (event.type === 'private_message_audit_failed') {
      const inCurrentSession = currentId
        && (
          (senderId === loginId && currentId === receiveId)
          || (receiveId === loginId && currentId === senderId)
        )
      if (inCurrentSession) {
        // 双方都移除：发送方那侧原来会留一条「未通过审核」的气泡，
        // 现在聊天记录里就像这条消息没发生过
        messages.value = messages.value.filter((item) => String(item.message?.id) !== String(event.messageId))
        if (senderId === loginId) {
          ElMessage.warning('你的一条消息未通过审核，已被移除')
        }
        await nextTick()
        scrollToBottom()
      }
      await loadSessions()
      return
    }

    if (currentId && senderId === currentId) {
      const recalled = messages.value.find((item) => String(item.message?.id) === String(event.messageId))
      if (recalled?.message) recalled.message.state = 2
      else await loadMessagesForPeer(currentId)
      await nextTick()
      scrollToBottom()
    }
    await loadSessions()
  })

  watch(() => messageStore.groupMessageSignal, async (newMsg) => {
    if (!newMsg || !['group_message', 'group_message_recalled', 'group_message_deleted', 'group_message_audit_failed', 'private_message_deleted'].includes(newMsg.type)) return
    if (!messageCenterUi.visible && router.currentRoute.value.name !== 'messages') return
    if (newMsg.type === 'private_message_deleted') {
      messages.value = messages.value.filter((item) => String(item.message?.id) !== String(newMsg.messageId))
      await loadSessions()
      return
    }
    const groupId = Number(newMsg.groupId)
    const currentGroupId = currentGroupSession.value?.groupId
      ? Number(currentGroupSession.value.groupId)
      : null
    if (currentGroupId && groupId === currentGroupId) {
      if (newMsg.type === 'group_message_deleted') {
        messages.value = messages.value.filter((item) => String(item.message?.id) !== String(newMsg.messageId))
      } else if (newMsg.type === 'group_message_audit_failed') {
        const loginId = Number(userStore.id)
        const senderId = Number(newMsg.fromUserId)
        if (senderId === loginId) {
          const row = messages.value.find((item) => String(item.message?.id) === String(newMsg.messageId))
          if (row?.message) {
            row.auditFailed = true
            row.message.auditFailed = true
            row.message.state = 4
            row.message.content = ''
          } else {
            await loadMessagesForGroup(groupId)
          }
        } else {
          messages.value = messages.value.filter((item) => String(item.message?.id) !== String(newMsg.messageId))
        }
      } else if (newMsg.type === 'group_message_recalled') {
        const recalled = messages.value.find((item) => String(item.message?.id) === String(newMsg.messageId))
        if (recalled?.message) recalled.message.state = 3
        else await loadMessagesForGroup(groupId)
      } else {
        await loadMessagesForGroup(groupId)
        await markCurrentGroupRead()
      }
      await loadGroupSessions()
      await nextTick()
      scrollToBottom()
    } else {
      await loadGroupSessions()
    }
  })

  watch(() => messageStore.systemMessageSignal, () => {
    if (!messageCenterUi.visible) return
    loadSystemMessages()
    if (currentSystemGroup.value?.groupId === 'joinRequest') {
      loadJoinRequestNotificationPage()
    } else {
      loadReceivedJoinRequests()
    }
    if (['audit', 'tag', 'musicAudit'].includes(currentSystemGroup.value?.groupId)) {
      loadSystemGroupMessages()
    }
  })

  watch(mentionSearch, () => {
    mentionMembersPage.value = 1
  })

  watch(ownedGroupSearch, () => {
    ownedGroupPage.value = 1
    if (ownedGroupInviteVisible.value) {
      loadOwnedGroupsForInvite()
    }
  })

  watch(searchQuery, () => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      if (activeTab.value === 'pm') runPrivateTextSearch()
      if (activeTab.value === 'group') runGroupTextSearch()
    }, 300)
  })

  watch(notificationSearch, () => {
    if (notificationSearchTimer) clearTimeout(notificationSearchTimer)
    notificationSearchTimer = setTimeout(() => {
      void runNotificationSearch()
    }, 320)
  })

  watch(activeTab, (tab) => {
    clearPendingAlbum()
    if (tab !== 'pm') {
      hiddenManagementMode.value = false
      textSearchMatches.value = new Map()
    } else if (searchQuery.value.trim()) {
      runPrivateTextSearch()
    }
    if (tab !== 'group') {
      groupTextSearchMatches.value = new Map()
    } else if (searchQuery.value.trim()) {
      runGroupTextSearch()
    }
  })

  watch(() => currentSession.value?.user?.id, (nextId, previousId) => {
    if (previousId != null && String(nextId || '') !== String(previousId)) {
      clearPendingAlbum()
    }
  })

  watch(() => currentGroupSession.value?.groupId, (nextId, previousId) => {
    if (previousId != null && String(nextId || '') !== String(previousId)) {
      clearPendingAlbum()
    }
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
    groupSettingsVisible.value = false
    groupSettingsPortalReady.value = false
    albumPreviewVisible.value = false
    albumPreviewImages.value = []
    clearPendingAlbum()
    messageCenterUi.close()
    if (router.currentRoute.value.name === 'messages') {
      router.replace('/')
    }
  }

  /**
   * 把若干页一次取完。仅用于数量天然有限的列表（群聊会话、入群申请）。
   *
   * <p>原来是 while(true)，翻到没有下一页为止，既没有上限，hasNextPage 一旦有问题
   * 就会无限循环。这里封了页数上限当安全网。
   */
  async function loadAllPages(fetchPage) {
    const records = []
    for (let pageNum = 1; pageNum <= MAX_AUTO_PAGES; pageNum += 1) {
      const res = await fetchPage(pageNum)
      if (res.code !== 0) break
      records.push(...unwrapPageRecords(res.data))
      if (!res.data?.hasNextPage) break
    }
    return records
  }

  /**
   * 会话列表按页取。
   *
   * <p>原来是一个 while(true) 把所有页拉完塞进内存，既没有页数上限，也让「搜索」
   * 看起来像纯前端搜索——因为整份列表本来就躺在本地。现在首屏只要一页，往下滚再续。
   */
  async function loadSessions({ append = false } = {}) {
    if (sessionLoading.value) return
    const targetPage = append ? sessionPage.value + 1 : 1
    sessionLoading.value = true
    try {
      const res = await getSessionList({ pageNum: targetPage, pageSize: SESSION_PAGE_SIZE })
      if (res.code !== 0) return
      const rows = unwrapPageRecords(res.data).filter((item) => item?.user?.id != null)
      sessionList.value = append ? [...sessionList.value, ...rows] : rows
      sessionPage.value = targetPage
      sessionHasMore.value = !!res.data?.hasNextPage
    } finally {
      sessionLoading.value = false
    }
  }

  async function loadHiddenSessions({ append = false } = {}) {
    if (hiddenSessionLoading.value) return
    const targetPage = append ? hiddenSessionPage.value + 1 : 1
    hiddenSessionLoading.value = true
    try {
      const res = await getHiddenMessageSessions({ pageNum: targetPage, pageSize: SESSION_PAGE_SIZE })
      if (res.code !== 0) return
      const rows = unwrapPageRecords(res.data).filter((item) => item?.user?.id != null)
      hiddenSessionList.value = append ? [...hiddenSessionList.value, ...rows] : rows
      hiddenSessionPage.value = targetPage
      hiddenSessionHasMore.value = !!res.data?.hasNextPage
    } finally {
      hiddenSessionLoading.value = false
    }
  }

  // 左侧列表滚到底就续下一页
  function onSessionListScroll(event) {
    const el = event?.target
    if (!el || el.scrollHeight - el.scrollTop - el.clientHeight > 80) return
    if (hiddenManagementMode.value) {
      if (hiddenSessionHasMore.value) void loadHiddenSessions({ append: true })
      return
    }
    if (activeTab.value === 'pm' && sessionHasMore.value) void loadSessions({ append: true })
  }

  async function runPrivateTextSearch() {
    const keyword = searchQuery.value.trim()
    const requestSequence = ++searchRequestSequence
    if (!keyword || activeTab.value !== 'pm' || hiddenManagementMode.value) {
      textSearchMatches.value = new Map()
      textSearchLoading.value = false
      return
    }
    textSearchLoading.value = true
    try {
      // 只取第一页：命中的会话按相关度/时间排在前面，翻到几百条之后已无意义
      const res = await searchMessageSessions({ keyword, pageNum: 1, pageSize: SEARCH_PAGE_SIZE })
      if (requestSequence !== searchRequestSequence) return
      const records = res.code === 0 ? unwrapPageRecords(res.data) : []
      textSearchMatches.value = new Map(records.map((item) => [Number(item.peerUserId), item]))
    } finally {
      if (requestSequence === searchRequestSequence) textSearchLoading.value = false
    }
  }

  async function runGroupTextSearch() {
    const keyword = searchQuery.value.trim()
    const requestSequence = ++groupSearchRequestSequence
    if (!keyword || activeTab.value !== 'group') {
      groupTextSearchMatches.value = new Map()
      groupTextSearchLoading.value = false
      return
    }
    groupTextSearchLoading.value = true
    try {
      // 与私信搜索一致：只取第一页
      const searchRes = await searchGroupChatSessions({ keyword, pageNum: 1, pageSize: SEARCH_PAGE_SIZE })
      const records = searchRes.code === 0 ? unwrapPageRecords(searchRes.data) : []
      if (requestSequence !== groupSearchRequestSequence) return
      groupTextSearchMatches.value = new Map(records.map((item) => [Number(item.groupId), item]))
    } finally {
      if (requestSequence === groupSearchRequestSequence) groupTextSearchLoading.value = false
    }
  }

  async function toggleHiddenManagement() {
    hiddenManagementMode.value = !hiddenManagementMode.value
    textSearchMatches.value = new Map()
    currentSession.value = null
    currentGroupSession.value = null
    currentSystemGroup.value = null
    messages.value = []
    if (hiddenManagementMode.value) {
      await loadHiddenSessions()
    } else if (searchQuery.value.trim()) {
      await runPrivateTextSearch()
    }
  }

  // 下载原图。图片消息与图集里的每一张都用它
  const downloadingMediaUrl = ref('')

  async function downloadChatImage(url, index = 1) {
    const target = String(url || '').trim()
    if (!target || downloadingMediaUrl.value) return
    downloadingMediaUrl.value = target
    try {
      const ok = await downloadImageByUrl(target, guessImageFileName(target, 'chat', index))
      if (ok) ElMessage.success('已开始下载原图')
      else ElMessage.error('下载失败：图片跨域受限，请稍后重试')
    } finally {
      downloadingMediaUrl.value = ''
    }
  }

  // 当前会话是否免打扰。铃铛就画在对方昵称右边
  const currentSessionMuted = computed(() => !!currentSession.value?.muted)

  async function toggleMuteCurrentSession() {
    const peerUserId = currentSession.value?.user?.id
    if (!peerUserId || currentSession.value?._virtual || mutingSession.value) return
    const nextMuted = !currentSessionMuted.value
    mutingSession.value = true
    try {
      const res = await muteMessageSession({ peerUserId, muted: nextMuted })
      if (res.code !== 0) {
        ElMessage.warning(res.message || '设置失败')
        return
      }
      // 就地更新，避免整份列表重拉导致滚动位置跳走
      currentSession.value.muted = nextMuted
      const row = sessionList.value.find((item) => Number(item?.user?.id) === Number(peerUserId))
      if (row) row.muted = nextMuted
      ElMessage.success(nextMuted ? '已开启免打扰' : '已恢复消息提醒')
    } catch {
      ElMessage.error('设置失败，请稍后重试')
    } finally {
      mutingSession.value = false
    }
  }

  // 置顶顺序由服务端按置顶时刻倒序给出，这里只负责发起并重拉列表
  async function togglePinPrivateSession(item) {
    const peerUserId = item?.session?.user?.id ?? item?.user?.id
    if (!peerUserId || pinningPeerId.value) return
    const nextPinned = !item.pinned
    pinningPeerId.value = Number(peerUserId)
    try {
      const res = await pinMessageSession({ peerUserId, pinned: nextPinned })
      if (res.code !== 0) {
        ElMessage.warning(res.message || (nextPinned ? '置顶失败' : '取消置顶失败'))
        return
      }
      await loadSessions()
      ElMessage.success(nextPinned ? '已置顶' : '已取消置顶')
    } catch {
      ElMessage.error(nextPinned ? '置顶失败，请稍后重试' : '取消置顶失败，请稍后重试')
    } finally {
      pinningPeerId.value = null
    }
  }

  async function hidePrivateSession(item) {
    const peerUserId = Number(item?.user?.id)
    if (!peerUserId) return
    try {
      await confirmDialog('隐藏后可恢复，消息本身不会被删除。', '隐藏聊天', {
        confirmButtonText: '确认',
        showCancelButton: false,
        customClass: 'mc-hide-session-confirm',
        confirmButtonClass: 'mc-hide-session-confirm__button',
        type: 'warning',
      })
      const res = await hideMessageSession(peerUserId)
      if (res.code !== 0) return
      if (Number(currentSession.value?.user?.id) === peerUserId) {
        currentSession.value = null
        messages.value = []
      }
      await Promise.all([loadSessions(), syncPmUnreadFromServer()])
      ElMessage.success('聊天已隐藏')
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') throw error
    }
  }

  async function restorePrivateSession(item) {
    const peerUserId = Number(item?.user?.id)
    if (!peerUserId) return
    const res = await restoreMessageSession(peerUserId)
    if (res.code !== 0) return
    await Promise.all([loadHiddenSessions(), loadSessions(), syncPmUnreadFromServer()])
    ElMessage.success('聊天已恢复')
  }

  function highlightSegments(text) {
    const source = String(text || '')
    const keyword = searchQuery.value.trim()
    if (!keyword) return [{ text: source, matched: false }]
    const sourceLower = source.toLowerCase()
    const keywordLower = keyword.toLowerCase()
    const parts = []
    let cursor = 0
    while (cursor < source.length) {
      const index = sourceLower.indexOf(keywordLower, cursor)
      if (index < 0) {
        parts.push({ text: source.slice(cursor), matched: false })
        break
      }
      if (index > cursor) parts.push({ text: source.slice(cursor, index), matched: false })
      parts.push({ text: source.slice(index, index + keyword.length), matched: true })
      cursor = index + keyword.length
    }
    return parts.length ? parts : [{ text: source, matched: false }]
  }

  function mapGroupMessage(row) {
    const sender = row.sender || {}
    const auditFailed = resolveGroupAuditFailed(row)
    return {
      isOwner: row.isOwner,
      auditFailed,
      user: sender,
      message: {
        id: row.id,
        groupId: row.groupId,
        messageType: row.messageType,
        content: auditFailed ? '' : row.content,
        mediaUrl: [1, 2].includes(Number(row.messageType)) ? row.content : undefined,
        albumImages: Array.isArray(row.albumImages) ? row.albumImages : [],
        replyMessageId: row.replyMessageId,
        replySenderName: row.replySenderName,
        replyContent: row.replyContent,
        createTime: row.createTime,
        updateTime: row.updateTime,
        state: row.status,
        auditFailed,
        recallDeadline: row.recallDeadline,
      },
      rawGroupMessage: row,
    }
  }

  function mapPrivateMessageRow(row) {
    const auditFailed = resolvePrivateAuditFailed(row)
    if (!row?.message) {
      return { ...row, auditFailed }
    }
    return {
      ...row,
      auditFailed,
      message: {
        ...row.message,
        auditFailed,
        content: auditFailed ? '' : row.message.content,
      },
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

  // 首屏取「最近的一页」。服务端已改成倒序分页，pageNum 越大越往前翻
  async function loadMessagesForPeer(peerUserId) {
    const res = await getMessageList({
      receiveId: peerUserId,
      pageNum: 1,
      pageSize: MESSAGE_PAGE_SIZE,
    })
    if (res.code === 0) {
      messages.value = unwrapPageRecords(res.data).map(mapPrivateMessageRow)
      messagePage.value = 1
      messageHasMore.value = !!res.data?.hasNextPage
      await loadGroupInviteCards()
      // 进入会话总是从最新一条看起
      following.value = true
      pendingNewCount.value = 0
      await scrollToBottom()
    }
  }

  /**
   * 往上翻更早的消息。
   *
   * <p>新内容插在最前面会把已有内容整体推下去，视觉上等于跳走了。
   * 所以记下插入前的滚动高度，插完再把差值补回去，让手指底下的那条消息不动。
   */
  async function loadOlderMessages() {
    const peerId = currentSession.value?.user?.id
    if (!peerId || currentSession.value?._virtual) return
    if (!messageHasMore.value || loadingOlderMessages.value) return
    const wrap = msgScrollbar.value?.wrapRef
    const beforeHeight = wrap ? wrap.scrollHeight : 0
    const beforeTop = wrap ? wrap.scrollTop : 0
    loadingOlderMessages.value = true
    try {
      const res = await getMessageList({
        receiveId: peerId,
        pageNum: messagePage.value + 1,
        pageSize: MESSAGE_PAGE_SIZE,
      })
      if (res.code !== 0) return
      const older = unwrapPageRecords(res.data).map(mapPrivateMessageRow)
      if (older.length) {
        messages.value = [...older, ...messages.value]
        messagePage.value += 1
      }
      messageHasMore.value = !!res.data?.hasNextPage
      await nextTick()
      if (wrap) {
        wrap.scrollTop = beforeTop + (wrap.scrollHeight - beforeHeight)
      }
    } finally {
      loadingOlderMessages.value = false
    }
  }

  function groupInviteRequestId(msgRow) {
    const content = String(msgRow?.message?.content || '')
    if (!content.startsWith(GROUP_INVITE_CARD_PREFIX) || !content.endsWith(']]')) {
      return null
    }
    const raw = content.slice(GROUP_INVITE_CARD_PREFIX.length, -2)
    const id = Number(raw)
    return Number.isFinite(id) && id > 0 ? id : null
  }

  function isGroupInviteCard(msgRow) {
    return groupInviteRequestId(msgRow) != null
  }

  function groupInviteInfo(msgRow) {
    const id = groupInviteRequestId(msgRow)
    return id == null ? null : groupInviteCards.value[String(id)] || null
  }

  async function loadGroupInviteCards() {
    const ids = [...new Set(messages.value
      .map(groupInviteRequestId)
      .filter((id) => id != null)
      .map((id) => String(id)))]
    if (!ids.length) {
      groupInviteCards.value = {}
      return
    }
    const next = { ...groupInviteCards.value }
    await Promise.all(ids.map(async (id) => {
      try {
        const res = await getGroupJoinRequest(id)
        if (res.code === 0 && res.data) {
          next[id] = res.data
        }
      } catch {
        // 忽略
      }
    }))
    groupInviteCards.value = next
  }

  function groupInviteStatusText(info) {
    if (!info) return '加载中'
    if (Number(info.status) === 3) return '已作废'
    if (Number(info.status) === 2) return '已拒绝'
    if (Number(info.status) === 1 || info.targetJoined) return '已进群'
    return '等待处理'
  }

  function canRespondGroupInvite(msgRow) {
    const info = groupInviteInfo(msgRow)
    return !!info
      && Number(info.status) === 0
      && Number(info.targetUser?.id) === Number(userStore.id)
      && !info.targetJoined
  }

  async function acceptInviteCard(msgRow) {
    const requestId = groupInviteRequestId(msgRow)
    if (!requestId) return
    await acceptGroupInvite(requestId)
    ElMessage.success('已加入群聊')
    await Promise.all([loadGroupInviteCards(), loadGroupSessions()])
  }

  async function declineInviteCard(msgRow) {
    const requestId = groupInviteRequestId(msgRow)
    if (!requestId) return
    await declineGroupInvite(requestId)
    ElMessage.success('已拒绝邀请')
    await loadGroupInviteCards()
  }

  // 自己是否在线不再问服务端：页面开着、通知长连接连着，就是在线。
  // 原来每 20 秒为此多发一个请求，而答案本地就有
  // 断线期间对方发来的消息不会补推，重连后按服务端状态重新对齐一次
  watch(() => notifyReconnectedSignal.value, async () => {
    if (!messageCenterUi.visible && router.currentRoute.value.name !== 'messages') return
    await syncPmUnreadFromServer()
    await loadSessions()
    const peerId = currentSession.value?.user?.id
    if (peerId && !currentSession.value?._virtual) {
      await loadMessagesForPeer(peerId)
    }
    void refreshOnlineStatus()
  })

  async function refreshOnlineStatus() {
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
      // 忽略
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

  async function loadJoinRequestNotificationPage() {
    joinRequestsLoading.value = true
    try {
      const keyword = notificationSearch.value.trim() || undefined
      const [received, applied] = await Promise.all([
        getReceivedGroupJoinRequests({
          pageNum: notificationPage.value,
          pageSize: JOIN_REQUEST_PAGE_SIZE,
          keyword,
        }),
        getAppliedGroupJoinRequests({
          pageNum: notificationPage.value,
          pageSize: JOIN_REQUEST_PAGE_SIZE,
          keyword,
        }),
      ])
      if (received.code === 0) {
        receivedJoinRequests.value = unwrapPageRecords(received.data)
      }
      if (applied.code === 0) {
        appliedJoinRequests.value = unwrapPageRecords(applied.data)
      }
      const receivedTotal = Number(received.data?.total) || 0
      const appliedTotal = Number(applied.data?.total) || 0
      notificationTotal.value = receivedTotal + appliedTotal
    } finally {
      joinRequestsLoading.value = false
    }
  }

  async function loadSystemGroupMessages({ markRead = false } = {}) {
    const groupId = currentSystemGroup.value?.groupId
    const category = systemGroupCategory(groupId)
    if (!category) return
    const response = await getSystemMessageList({
      category,
      keyword: notificationSearch.value.trim() || undefined,
      pageNum: notificationPage.value,
      pageSize: SYSTEM_NOTIFY_PAGE_SIZE,
    })
    if (response.code !== 0 || currentSystemGroup.value?.groupId !== groupId) return
    const records = unwrapPageRecords(response.data)
    currentSystemGroup.value.messages = records
    notificationTotal.value = Number(response.data?.total) || 0
    if (!markRead) return
    let marked = 0
    for (const m of records) {
      if (Number(m.state) !== 0) continue
      try {
        await markSystemMessageRead(m.id)
        m.state = 1
        marked += 1
      } catch {
        // 忽略
      }
    }
    if (marked > 0) {
      systemUnread.value = Math.max(0, systemUnread.value - marked)
      messageStore.setSystemUnreadCount(systemUnread.value)
      await loadSystemMessages()
    }
  }

  async function onNotificationPageChange(page) {
    notificationPage.value = page
    const groupId = currentSystemGroup.value?.groupId
    if (groupId === 'joinRequest') {
      await loadJoinRequestNotificationPage()
      return
    }
    if (['audit', 'tag', 'musicAudit'].includes(groupId)) {
      await loadSystemGroupMessages()
    }
  }

  function onComposerKeydown(event) {
    if (event.key !== 'Enter' || event.shiftKey) return
    if (!enterToSendEnabled.value) return
    event.preventDefault()
    void sendMsg()
  }

  async function selectSysGroup(item) {
    notificationSearch.value = ''
    notificationPage.value = 1
    notificationTotal.value = 0
    currentSession.value = null
    currentGroupSession.value = null
    currentSystemGroup.value = {
      groupId: item.groupId,
      name: item.name,
      messages: [],
    }
    focusedConvKey.value = item.key
    if (['audit', 'tag', 'musicAudit'].includes(item.groupId)) {
      await loadSystemGroupMessages({ markRead: true })
      return
    }
    currentSystemGroup.value.messages = Array.isArray(item.messages) ? [...item.messages] : []
    let marked = 0
    for (const m of currentSystemGroup.value.messages) {
      if (Number(m.state) !== 0) continue
      try {
        await markSystemMessageRead(m.id)
        m.state = 1
        marked += 1
      } catch {
        // 忽略
      }
    }
    if (marked > 0) {
      systemUnread.value = Math.max(0, systemUnread.value - marked)
      messageStore.setSystemUnreadCount(systemUnread.value)
    }
  }

  async function selectJoinRequestGroup(item) {
    notificationSearch.value = ''
    notificationPage.value = 1
    notificationTotal.value = 0
    currentSession.value = null
    currentGroupSession.value = null
    currentSystemGroup.value = {
      groupId: item.groupId,
      name: item.name,
      messages: [],
    }
    focusedConvKey.value = item.key
    const ownerHasUnread = receivedJoinRequests.value.some((request) => Number(request.ownerReadState) === 0)
    const applicantHasUnread = appliedJoinRequests.value.some((request) => Number(request.applicantReadState) === 0)
    if (ownerHasUnread) {
      receivedJoinRequests.value.forEach((request) => {
        request.ownerReadState = 1
      })
      try {
        await markReceivedGroupJoinRequestsRead()
      } catch {
        // 忽略
      }
    }
    if (applicantHasUnread) {
      appliedJoinRequests.value.forEach((request) => {
        request.applicantReadState = 1
      })
      try {
        await markAppliedGroupJoinRequestsRead()
      } catch {
        // 忽略
      }
    }
    await loadJoinRequestNotificationPage()
  }

  async function approveJoinRequestItem(item) {
    const requestId = item?.id
    if (!requestId) return
    const res = await approveGroupJoinRequest(requestId)
    if (Number(res.data?.status) === 3) {
      ElMessage.warning('对方已经进群，具体请在群内操作')
    } else {
      ElMessage.success('已批准进群')
    }
    if (currentSystemGroup.value?.groupId === 'joinRequest') {
      await Promise.all([loadJoinRequestNotificationPage(), loadGroupSessions()])
    } else {
      await Promise.all([loadReceivedJoinRequests(), loadGroupSessions()])
    }
  }

  async function rejectJoinRequestItem(item) {
    const requestId = item?.id
    if (!requestId) return
    const res = await rejectGroupJoinRequest(requestId)
    if (Number(res.data?.status) === 3) {
      ElMessage.warning('对方已经进群，具体请在群内操作')
    } else {
      ElMessage.success('已拒绝申请')
    }
    if (currentSystemGroup.value?.groupId === 'joinRequest') {
      await loadJoinRequestNotificationPage()
    } else {
      await loadReceivedJoinRequests()
    }
  }

  async function selectGroupSession(group) {
    currentSession.value = null
    currentSystemGroup.value = null
    currentGroupSession.value = group
    clearReplyTarget()
    focusedConvKey.value = `group-${group.groupId}`
    peerOnline.value = false
    await loadMessagesForGroup(group.groupId)
    await markCurrentGroupRead()
    await loadGroupSessions()
  }

  async function selectListItem(item) {
    focusedConvKey.value = item.key
    // 归档的会话也要能点开：能翻记录，只是不能发言（发送区已禁用）
    if (item.kind === 'pm') await selectPmSession(item.session)
    else if (item.kind === 'group') await selectGroupSession(item.group)
    else if (item.kind === 'join-request-group') await selectJoinRequestGroup(item)
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
      const res = currentGroupSession.value
        ? await recallGroupChatMessage(mid)
        : await recallMessage(mid)
      if (res.code === 0) {
        msg.message.state = currentGroupSession.value ? 3 : 2
        ElMessage.success('消息已撤回')
      } else {
        ElMessage.error(res.message || '撤回失败')
      }
    } catch (error) {
      const message = String(error?.message || error?.msg || '')
      if (msg?.isOwner && (message.includes('撤回时间') || !canRecallMessage(msg))) {
        const next = new Set(expiredRecallIds.value)
        next.add(mid)
        expiredRecallIds.value = next
        if (!message) ElMessage.warning('超过撤回时间，撤回失败')
      }
    }
  }

  function canRecallMessage(msgRow) {
    if (!msgRow?.isOwner || isGroupInviteCard(msgRow) || isRecalledMessage(msgRow)) return false
    if (!currentSession.value && !currentGroupSession.value) return false
    if (currentGroupSession.value && Number(msgRow?.message?.messageType) === 9) return false
    const messageId = coerceMessageId(msgRow?.message?.id)
    if (Number.isFinite(messageId) && expiredRecallIds.value.has(messageId)) return false
    const deadline = parseForumDateTime(msgRow?.message?.recallDeadline)
    if (deadline) return recallClock.value < deadline.getTime()
    const createTime = parseForumDateTime(msgRow?.message?.createTime)
    return !!createTime && recallClock.value < createTime.getTime() + 120_000
  }

  async function sendMsg() {
    // 归档（已删除）的会话是只读的：要发言得先恢复。
    // 服务端在发消息时会自动把归档恢复出来，那是给「对方来消息」用的兜底，
    // 不该让人在归档列表里直接开聊，否则「删除聊天」就形同虚设
    if (hiddenManagementMode.value) {
      ElMessage.warning('这是已删除的聊天，恢复后才能发消息')
      return
    }
    const text = sendContent.value.trim()
    const hasPendingAlbum = !!(currentSession.value || currentGroupSession.value)
      && pendingAlbumFiles.value.length > 0
    if ((!text && !hasPendingAlbum) || (!currentSession.value && !currentGroupSession.value)) return
    sending.value = true
    try {
      if (hasPendingAlbum) {
        await sendPendingAlbum(text)
        return
      }
      if (currentGroupSession.value) {
        const res = await sendGroupChatMessage({
          groupId: currentGroupSession.value.groupId,
          messageType: 0,
          content: text,
          replyMessageId: replyTarget.value?.id,
        })
        if (res.code === 0 && res.data) {
          messages.value.push(mapGroupMessage(res.data))
          sendContent.value = ''
          clearReplyTarget()
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
    } catch {
      // 请求拦截器已提示
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
      // 忽略
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
    if (name.length > 10) {
      ElMessage.warning('群名称不能超过10个字')
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
    } catch {
      // 拦截器已提示
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

  async function openGroupSettings() {
    if (!currentGroupSession.value?.groupId) return
    groupSettingsPortalReady.value = true
    await nextTick()
    groupSettingsVisible.value = true
    groupMemberSearch.value = ''
    groupMembersPage.value = 1
    openGroupEdit()
    await nextTick()
    syncGroupIntroEditor()
    await loadGroupMembers()
  }

  async function loadGroupMembers() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    groupMembersLoading.value = true
    try {
      const res = await getGroupChatMembers(gid, {
        keyword: groupMemberSearch.value.trim() || undefined,
        pageNum: groupMembersPage.value,
        pageSize: 4,
        sortMode: 'default',
      })
      if (res.code === 0) {
        groupMembers.value = unwrapPageRecords(res.data)
        groupMembersTotal.value = Number(res.data?.total) || 0
        groupRemarkForm.value = {
          remarkName: currentGroupSession.value?.remarkName || '',
          notifyMode: Number(currentGroupSession.value?.notifyMode) || 0,
        }
        setGroupMemberSettingsSnapshot()
      }
    } finally {
      groupMembersLoading.value = false
    }
  }

  function canShowGroupMessageActions(msgRow) {
    return !!currentGroupSession.value
      && !isRecalledMessage(msgRow)
      && Number(msgRow?.message?.messageType) !== 9
      && !msgRow?.pendingAlbumState
  }

  function canRecallGroupMessage(msgRow) {
    if (!canShowGroupMessageActions(msgRow)) return false
    if (msgRow?.isOwner) return canRecallMessage(msgRow)
    return isCurrentGroupOwner.value
  }

  function onGroupMemberSearchInput() {
    if (groupMemberSearchTimer) clearTimeout(groupMemberSearchTimer)
    groupMemberSearchTimer = setTimeout(async () => {
      groupMembersPage.value = 1
      await loadGroupMembers()
    }, 300)
  }

  async function loadMentionMembers() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    const res = await getGroupChatMembers(gid, {
      keyword: mentionSearch.value.trim() || undefined,
      pageNum: mentionMembersPage.value,
      pageSize: MENTION_PAGE_SIZE,
      sortMode: 'nicknameInitial',
    })
    if (res.code === 0) {
      mentionMembers.value = unwrapPageRecords(res.data)
      mentionMembersTotal.value = Number(res.data?.total) || 0
    }
  }

  async function runNotificationSearch() {
    const groupId = currentSystemGroup.value?.groupId
    if (!groupId) return
    notificationPage.value = 1
    if (groupId === 'joinRequest') {
      await loadJoinRequestNotificationPage()
      return
    }
    if (!['audit', 'tag', 'musicAudit', 'report'].includes(groupId)) return
    await loadSystemGroupMessages()
  }

  function onMentionSearchInput() {
    if (mentionSearchTimer) clearTimeout(mentionSearchTimer)
    mentionSearchTimer = setTimeout(async () => {
      mentionMembersPage.value = 1
      await loadMentionMembers()
    }, 300)
  }

  async function loadGroupAdminMembers() {
    const gid = currentGroupSession.value?.groupId
    if (!gid || !isCurrentGroupOwner.value) return
    groupAdminLoading.value = true
    try {
      const res = await getGroupChatMembers(gid, {
        keyword: groupAdminSearch.value.trim() || undefined,
        pageNum: groupAdminPage.value,
        pageSize: 5,
        sortMode: 'nicknameInitial',
      })
      if (res.code === 0) {
        groupAdminMembers.value = unwrapPageRecords(res.data)
        groupAdminTotal.value = Number(res.data?.total) || 0
      }
    } finally {
      groupAdminLoading.value = false
    }
  }

  async function openGroupAdminPicker() {
    if (!isCurrentGroupOwner.value) return
    groupAdminVisible.value = true
    groupAdminSearch.value = ''
    groupAdminPage.value = 1
    await loadGroupAdminMembers()
  }

  function toggleGroupAdminPicker() {
    if (groupAdminVisible.value) {
      groupAdminVisible.value = false
      return
    }
    void openGroupAdminPicker()
  }

  function onGroupAdminSearchInput() {
    if (groupAdminSearchTimer) clearTimeout(groupAdminSearchTimer)
    groupAdminSearchTimer = setTimeout(async () => {
      groupAdminPage.value = 1
      await loadGroupAdminMembers()
    }, 300)
  }

  function openGroupEdit() {
    if (!currentGroupSession.value) return
    groupEditForm.value = {
      name: currentGroupSession.value.groupName || currentGroupSession.value.name || '',
      groupType: Number(currentGroupSession.value.groupType) || 0,
      intro: currentGroupSession.value.intro || '',
      avatarUrl: currentGroupSession.value.avatarUrl || '',
    }
    setGroupEditSnapshot()
  }

  function groupAvatarText(groupLike) {
    const name = groupLike?.groupName || groupLike?.group?.groupName || groupLike?.name || groupLike?.group?.name || '群'
    return String(name).trim().charAt(0) || '群'
  }

  function groupAvatarUrl(groupLike) {
    return groupLike?.avatarUrl || groupLike?.group?.avatarUrl || ''
  }

  function triggerGroupAvatarUpload() {
    groupAvatarInputRef.value?.click()
  }

  function validateGroupAvatarFile(file) {
    if (!file) return { ok: false, message: '请选择头像图片' }
    const type = (file.type || '').toLowerCase()
    if (!['image/jpeg', 'image/jpg', 'image/png', 'image/gif'].includes(type)) {
      return { ok: false, message: '群头像仅支持 JPG / PNG / GIF' }
    }
    return validateLocalImageFile(file)
  }

  async function onGroupAvatarFileChange(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    const check = validateGroupAvatarFile(file)
    if (!check.ok) {
      ElMessage.warning(check.message)
      return
    }
    uploadingGroupAvatar.value = true
    const loading = openImageUploadLoading(file, '正在上传群头像…')
    try {
      const res = await uploadGroupAvatar(file)
      if (res.code === 0 && res.data) {
        groupEditForm.value.avatarUrl = res.data
        ElMessage.success('群头像已上传，保存后生效')
      }
    } finally {
      loading.close()
      uploadingGroupAvatar.value = false
    }
  }

  function syncGroupIntroEditor() {
    const el = groupIntroEditorRef.value
    if (!el) return
    const text = groupEditForm.value.intro || ''
    if (el.innerText !== text) el.innerText = text
  }

  function normalizeGroupIntro(value) {
    return String(value || '').replace(/\r\n?/g, '\n').slice(0, 120)
  }

  function placeCaretAtEnd(el) {
    const selection = window.getSelection()
    if (!selection) return
    const range = document.createRange()
    range.selectNodeContents(el)
    range.collapse(false)
    selection.removeAllRanges()
    selection.addRange(range)
  }

  function onGroupIntroInput(event) {
    const el = event?.currentTarget
    if (!el) return
    const text = normalizeGroupIntro(el.innerText)
    groupEditForm.value.intro = text
    if (el.innerText !== text) {
      el.innerText = text
      placeCaretAtEnd(el)
    }
  }

  function switchGroupType(type) {
    if (groupTypeSwitchLocked.value) {
      ElMessage.warning('操作太频繁，请稍后再试')
      return
    }
    groupEditForm.value.groupType = Number(type)
    groupTypeSwitchLocked.value = true
    if (groupTypeSwitchTimer) clearTimeout(groupTypeSwitchTimer)
    groupTypeSwitchTimer = setTimeout(() => {
      groupTypeSwitchLocked.value = false
      groupTypeSwitchTimer = null
    }, 1200)
  }

  function beforeCloseGroupSettings(done) {
    if (!groupEditDirty.value) {
      done()
      return
    }
    confirmDialog('群资料修改后需要点击保存才会生效，确认先关闭吗？', '群资料未保存', {
      confirmButtonText: '关闭',
      cancelButtonText: '继续编辑',
      type: 'warning',
    }).then(() => done()).catch(() => {})
  }

  function requestCloseGroupSettings() {
    beforeCloseGroupSettings(() => {
      groupSettingsVisible.value = false
    })
  }

  async function submitGroupEdit() {
    const gid = currentGroupSession.value?.groupId
    const name = groupEditForm.value.name.trim()
    if (!gid || !name) {
      ElMessage.warning('请输入群名称')
      return
    }
    if (name.length > 10) {
      ElMessage.warning('群名称不能超过10个字')
      return
    }
    savingGroupEdit.value = true
    try {
      const res = await updateGroupChat(gid, {
        name,
        groupType: Number(groupEditForm.value.groupType),
        intro: groupEditForm.value.intro?.trim() || undefined,
        avatarUrl: groupEditForm.value.avatarUrl?.trim() || undefined,
      })
      if (res.code === 0) {
        if (groupMemberSettingsDirty.value) {
          await updateMyGroupRemark(gid, {
            remarkName: groupRemarkForm.value.remarkName?.trim() || undefined,
            notifyMode: Number(groupRemarkForm.value.notifyMode) || 0,
          })
          setGroupMemberSettingsSnapshot()
        }
        ElMessage.success('群信息已更新')
        setGroupEditSnapshot()
        groupSettingsVisible.value = false
        await refreshCurrentGroupSession()
      }
    } finally {
      savingGroupEdit.value = false
    }
  }

  async function submitGroupRemark() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    savingGroupRemark.value = true
    try {
      const res = await updateMyGroupRemark(gid, {
        remarkName: groupRemarkForm.value.remarkName?.trim() || undefined,
        notifyMode: Number(groupRemarkForm.value.notifyMode) || 0,
      })
      if (res.code === 0) {
        if (currentGroupSession.value) {
          currentGroupSession.value.remarkName = res.data?.remarkName || ''
          currentGroupSession.value.notifyMode = Number(res.data?.notifyMode) || 0
        }
        groupRemarkForm.value.remarkName = res.data?.remarkName || ''
        groupRemarkForm.value.notifyMode = Number(res.data?.notifyMode) || 0
        setGroupMemberSettingsSnapshot()
        await refreshCurrentGroupSession()
        ElMessage.success('群聊设置已保存')
      }
    } finally {
      savingGroupRemark.value = false
    }
  }

  async function leaveCurrentGroup() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    await confirmDialog('确认退出当前群聊吗？退出后不会再接收新消息。', '退出群聊', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await leaveGroupChat(gid)
    ElMessage.success('已退出群聊')
    groupSettingsVisible.value = false
    currentGroupSession.value = null
    messages.value = []
    await loadGroupSessions()
  }

  async function dissolveCurrentGroup() {
    const gid = currentGroupSession.value?.groupId
    if (!gid) return
    await confirmDialog('确认解散当前群聊吗？解散后成员将无法继续聊天。', '解散群聊', {
      confirmButtonText: '解散',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await dissolveGroupChat(gid)
    ElMessage.success('群聊已解散')
    groupSettingsVisible.value = false
    currentGroupSession.value = null
    messages.value = []
    await loadGroupSessions()
  }

  async function removeMember(member) {
    const gid = currentGroupSession.value?.groupId
    const targetUserId = member?.user?.id
    if (!gid || !targetUserId) return
    await confirmDialog(`确认移除「${member.user?.nickname || targetUserId}」吗？`, '移除成员', {
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
    await confirmDialog(text, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await muteGroupChatMember(gid, targetUserId, minutes)
    ElMessage.success(minutes > 0 ? '已禁言成员' : '已解除禁言')
    await loadGroupMembers()
  }

  function toggleMuteMember(member) {
    return muteMember(member, isMemberMuted(member) ? 0 : 30)
  }

  function openMentionPicker() {
    if (!currentGroupSession.value) return
    mentionSearch.value = ''
    mentionMembersPage.value = 1
    mentionPopoverVisible.value = true
    void loadMentionMembers()
  }

  function toggleMentionPicker() {
    if (mentionPopoverVisible.value) {
      mentionPopoverVisible.value = false
      return
    }
    openMentionPicker()
  }

  async function openOwnedGroupInvitePicker() {
    if (!currentSession.value?.user?.id) return
    ownedGroupInviteVisible.value = true
    ownedGroupPage.value = 1
    await loadOwnedGroupsForInvite()
  }

  async function loadOwnedGroupsForInvite() {
    ownedGroupsLoading.value = true
    try {
      const res = await getOwnedGroupChats({
        keyword: ownedGroupSearch.value.trim() || undefined,
        pageNum: ownedGroupPage.value,
        pageSize: 5,
      })
      if (res.code === 0) {
        ownedGroups.value = unwrapPageRecords(res.data)
        ownedGroupTotal.value = Number(res.data?.total) || 0
      }
    } finally {
      ownedGroupsLoading.value = false
    }
  }

  const ownedGroupTotalPages = computed(() =>
    Math.max(1, Math.ceil((ownedGroupTotal.value || 0) / 5)),
  )


  function ownedGroupMemberText(group) {
    return `${Number(group?.memberCount) || 0}/${Number(group?.memberLimit) || 0} 人`
  }

  async function sendGroupInviteFromPm(group) {
    const gid = group?.id
    const inviteeUserId = currentSession.value?.user?.id
    if (!gid || !inviteeUserId) return
    await confirmDialog(`确认邀请对方加入「${group.name || '群聊'}」吗？`, '发送入群邀请', {
      confirmButtonText: '发送',
      showCancelButton: false,
      customClass: 'mc-group-invite-confirm',
      confirmButtonClass: 'mc-group-invite-confirm__button',
      type: 'warning',
    })
    invitingGroupId.value = gid
    try {
      const res = await inviteGroupChatMember(gid, inviteeUserId)
      if (res.code === 0) {
        ElMessage.success('邀请已发送')
        ownedGroupInviteVisible.value = false
        await loadMessagesForPeer(inviteeUserId)
        await loadSessions()
      }
    } finally {
      invitingGroupId.value = null
    }
  }

  function selectMentionMember(member) {
    const name = memberDisplayName(member).trim()
    if (!name) return
    insertMentionText(`@${name}`)
    mentionPopoverVisible.value = false
  }

  function selectMentionAll() {
    if (!isCurrentGroupManager.value) {
      ElMessage.warning('只有群主或管理员可以@所有人')
      return
    }
    insertMentionText('@所有人')
    mentionPopoverVisible.value = false
  }

  function insertMentionText(text) {
    const prefix = sendContent.value.trimEnd()
    sendContent.value = `${prefix}${prefix ? ' ' : ''}${text} `
    nextTick(() => {
      inputBoxRef.value?.focus()
      autoResizeInput()
    })
  }


  async function setGroupNotifyMode(mode) {
    const nextMode = Number(mode)
    const gid = Number(currentGroupSession.value?.groupId)
    if (!Number.isFinite(gid) || gid <= 0 || nextMode === Number(groupRemarkForm.value.notifyMode)) return
    groupRemarkForm.value.notifyMode = nextMode
    try {
      const res = await updateMyGroupRemark(gid, {
        // 切换提醒模式不应顺带提交尚未保存的群备注
        remarkName: undefined,
        notifyMode: nextMode,
      })
      if (res.code !== 0) throw new Error(res.message || '消息提醒模式更新失败')
      const actualMode = Number(res.data?.notifyMode) || 0
      groupRemarkForm.value.notifyMode = actualMode
      if (currentGroupSession.value) currentGroupSession.value.notifyMode = actualMode
      setGroupMemberSettingsSnapshot()
      await refreshCurrentGroupSession()
    } catch (error) {
      groupRemarkForm.value.notifyMode = Number(myGroupMember.value?.notifyMode) || 0
      ElMessage.error(error?.message || '消息提醒模式更新失败')
    }
  }

  function startReply(msgRow) {
    const message = msgRow?.message
    const id = coerceMessageId(message?.id)
    if (!currentGroupSession.value || !Number.isFinite(id) || id <= 0) return
    if (Number(message?.messageType) === 9) return
    replyTarget.value = {
      id,
      senderName: msgRow?.user?.nickname || (msgRow?.isOwner ? '我' : '用户'),
      content: replyContentText(msgRow),
    }
    nextTick(() => inputBoxRef.value?.focus())
  }

  function clearReplyTarget() {
    replyTarget.value = null
  }

  function replyContentText(msgRow) {
    const message = msgRow?.message || {}
    const type = Number(message.messageType)
    if (type === 1) return '[表情]'
    if (type === 2) return '[图片]'
    return String(message.content || '').replace(/\s+/g, ' ').trim().slice(0, 80)
  }

  function memberRoleLabel(role) {
    if (Number(role) === 0) return '群主'
    if (Number(role) === 2) return '管理员'
    return '成员'
  }

  function formatJoinRequestTime(time) {
    const date = parseForumDateTime(time)
    if (!date) return ''
    const now = new Date()
    const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const startTarget = new Date(date.getFullYear(), date.getMonth(), date.getDate())
    const dayDiff = Math.floor((startToday.getTime() - startTarget.getTime()) / 86400000)
    if (dayDiff === 0) {
      return `${padTime(date.getHours())}:${padTime(date.getMinutes())}`
    }
    if (dayDiff === 1) return '昨天'
    if (dayDiff === 2) return '前天'
    if (dayDiff < 30) return `${dayDiff}天前`
    const monthDiff = (now.getFullYear() - date.getFullYear()) * 12 + now.getMonth() - date.getMonth()
    if (monthDiff < 12) return `${Math.max(1, monthDiff)}月前`
    return `${date.getFullYear()}-${padTime(date.getMonth() + 1)}-${padTime(date.getDate())} ${padTime(date.getHours())}:${padTime(date.getMinutes())}`
  }

  function padTime(value) {
    return String(value).padStart(2, '0')
  }

  function sortGroupMember(a, b) {
    const roleA = groupRoleWeight(a?.role)
    const roleB = groupRoleWeight(b?.role)
    if (roleA !== roleB) return roleA - roleB
    const nameA = a?.user?.nickname || ''
    const nameB = b?.user?.nickname || ''
    return nameA.localeCompare(nameB, 'zh-Hans-CN')
  }

  function groupRoleWeight(role) {
    if (Number(role) === 0) return 0
    if (Number(role) === 2) return 1
    return 2
  }

  async function toggleGroupAdminRole(member) {
    const gid = currentGroupSession.value?.groupId
    const targetUserId = member?.user?.id
    if (!gid || !targetUserId || Number(member?.role) === 0) return
    const nextRole = Number(member.role) === 2 ? 1 : 2
    const actionText = nextRole === 2 ? '设置为管理员' : '撤销管理员'
    await confirmDialog(`确认${actionText}「${member.user?.nickname || targetUserId}」吗？`, actionText, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    groupAdminUpdatingId.value = targetUserId
    try {
      await updateGroupMemberRole(gid, targetUserId, nextRole)
      ElMessage.success(nextRole === 2 ? '已设置管理员' : '已撤销管理员')
      await Promise.all([loadGroupAdminMembers(), loadGroupMembers()])
    } finally {
      groupAdminUpdatingId.value = null
    }
  }

  function memberMuteLabel(member) {
    if (!isMemberMuted(member)) return ''
    return `禁言至 ${formatSessionTime(member.muteUntil)}`
  }

  function isMemberMuted(member) {
    const until = parseForumDateTime(member?.muteUntil)
    return !!until && until.getTime() > Date.now()
  }


  function canReportChatMessage(msgRow) {
    return Boolean(
      msgRow
      && !msgRow.isOwner
      && Number(msgRow.message?.messageType) === 0
      && !isRecalledMessage(msgRow)
      && !msgRow.pendingAlbumState
      && !isGroupInviteCard(msgRow)
    )
  }

  function submitChatMessageReport(msgRow) {
    if (!canReportChatMessage(msgRow)) return
    pendingChatReport.value = msgRow
    chatReportDialogVisible.value = true
  }

  async function confirmChatMessageReport(reason) {
    const msgRow = pendingChatReport.value
    if (!canReportChatMessage(msgRow) || chatReportSubmitting.value) return
    chatReportSubmitting.value = true
    try {
      const response = await reportChatMessage({
        conversationType: currentGroupSession.value ? 'GROUP' : 'PRIVATE',
        messageId: msgRow.message?.id,
        reason: String(reason || '').trim(),
      })
      if (response.code !== 0) {
        ElMessage.error(response.message || '举报提交失败')
        return
      }
      chatReportDialogVisible.value = false
      pendingChatReport.value = null
      ElMessage.success('已收到举报，结果会通过消息中心通知')
    } finally {
      chatReportSubmitting.value = false
    }
  }

  function formatGroupCreatedDate(time) {
    const date = parseForumDateTime(time)
    if (!date) return '未知日期'
    return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
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
    const files = Array.from(e.target.files || [])
    e.target.value = ''
    if (!files.length) return
    if (currentSession.value || currentGroupSession.value) {
      addPendingAlbumFiles(files)
      return
    }
    if (files[0]) void sendChatImageMessage(files[0])
  }

  function addPendingAlbumFiles(files) {
    const remaining = 10 - pendingAlbumFiles.value.length
    if (remaining <= 0) {
      ElMessage.warning('一次最多发送十张图片')
      return
    }
    if (files.length > remaining) {
      ElMessage.warning(`一次最多发送十张图片，已保留前 ${remaining} 张`)
    }
    const accepted = []
    for (const file of files.slice(0, remaining)) {
      const mimeOk = validateChatImageMime(file)
      if (!mimeOk.ok) {
        ElMessage.warning(`${file.name}：${mimeOk.message}`)
        continue
      }
      const sizeOk = validateLocalImageFile(file)
      if (!sizeOk.ok) {
        ElMessage.warning(`${file.name}：${sizeOk.message}`)
        continue
      }
      accepted.push({
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        file,
        previewUrl: URL.createObjectURL(file),
      })
    }
    pendingAlbumFiles.value.push(...accepted)
  }

  function removePendingAlbumFile(id) {
    const target = pendingAlbumFiles.value.find((item) => item.id === id)
    if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl)
    pendingAlbumFiles.value = pendingAlbumFiles.value.filter((item) => item.id !== id)
  }

  function clearPendingAlbum() {
    pendingAlbumFiles.value.forEach((item) => {
      if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
    })
    pendingAlbumFiles.value = []
  }

  async function uploadPendingAlbumFiles(task) {
    const source = task.items
    const BATCH_SIZE = 9
    const pending = source.filter((item) => !item.uploaded && !item.violation && !item.uploadError)
    for (let offset = 0; offset < pending.length; offset += BATCH_SIZE) {
      const chunk = pending.slice(offset, offset + BATCH_SIZE)
      try {
        const [uploadResponse, dimensionsList] = await Promise.all([
          uploadChatImages(
            chunk.map((item) => item.file),
            { silentBizCodes: [1128], silentHttpError: true },
          ),
          Promise.all(chunk.map((item) => readImageNaturalSize(item.previewUrl))),
        ])
        const successList = Array.isArray(uploadResponse?.data?.success) ? uploadResponse.data.success : []
        const failedList = Array.isArray(uploadResponse?.data?.failed) ? uploadResponse.data.failed : []
        const successByIndex = new Map(
          successList
            .filter((item) => item?.url != null)
            .map((item) => [Number(item.index), String(item.url)]),
        )
        const failedByIndex = new Map(
          failedList.map((item) => [Number(item.index), item]),
        )
        chunk.forEach((item, localIndex) => {
          const url = successByIndex.get(localIndex)
          if (url) {
            const dimensions = dimensionsList[localIndex] || { width: 0, height: 0 }
            item.uploaded = {
              mediaUrl: url,
              mediaMime: item.file.type || undefined,
              mediaSize: item.file.size,
              mediaWidth: dimensions.width > 0 ? dimensions.width : undefined,
              mediaHeight: dimensions.height > 0 ? dimensions.height : undefined,
            }
            return
          }
          const failed = failedByIndex.get(localIndex)
          const reason = String(failed?.reason || uploadResponse?.message || '')
          if (Number(uploadResponse?.code) === 1128 || reason.includes('违规') || reason.includes('审核')) {
            item.violation = true
          } else {
            item.uploadError = new Error(reason || '图片上传失败')
          }
        })
      } catch (error) {
        if (Number(error?.code) === 1128) {
          chunk.forEach((item) => {
            item.violation = true
          })
        } else {
          chunk.forEach((item) => {
            item.uploadError = error || new Error('图片上传失败')
          })
        }
      }
    }
    const violationCount = source.filter((item) => item.violation).length
    task.items = source.filter((item) => !item.violation)
    task.row.message.albumImages = task.items.map((item, index) => ({
      id: `pending-${index}`,
      mediaUrl: item.previewUrl,
    }))
    return {
      violationCount,
      hasUploadFailure: task.items.some((item) => item.uploadError),
      images: task.items.map((item) => item.uploaded).filter(Boolean),
    }
  }

  function releaseAlbumTaskFiles(task) {
    ;(task?.allItems || task?.items || []).forEach((item) => {
      if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
    })
  }

  function removePendingAlbumRow(row, { release = true } = {}) {
    const index = messages.value.indexOf(row)
    if (index >= 0) messages.value.splice(index, 1)
    if (release) releaseAlbumTaskFiles(row?.pendingAlbumTask)
  }

  function isAlbumTaskConversationActive(task) {
    if (task.context.kind === 'group') {
      return Number(currentGroupSession.value?.groupId) === Number(task.context.groupId)
    }
    return Number(currentSession.value?.user?.id) === Number(task.context.receiveUserId)
  }

  async function processPendingAlbumTask(task) {
    const row = task.row
    row.pendingAlbumState = 'auditing'
    task.items.forEach((item) => {
      item.uploadError = null
    })
    try {
      const result = await uploadPendingAlbumFiles(task)
      if (result.violationCount > 0) {
        ElMessage.warning('图集包含违规图片，已为您自动过滤删除！')
      }
      if (!task.items.length) {
        if (task.text && isAlbumTaskConversationActive(task) && !sendContent.value.trim()) {
          sendContent.value = task.text
        }
        removePendingAlbumRow(row)
        ElMessage.warning('图集内图片均未通过审核，消息未发送')
        return
      }
      if (result.hasUploadFailure || result.images.length !== task.items.length) {
        row.pendingAlbumState = 'failed'
        row.pendingAlbumError = '部分图片上传失败，请重试'
        return
      }
      // 一张图就是一张图，不该当成图集发出去。
      // 群聊这条路暂时不分流：群聊的单图消息用 content 存图片地址，带不了说明文字，
      // 分流反而会把文字弄丢，等群聊补上独立的媒体字段再说。
      const singleImage = task.context.kind !== 'group' && result.images.length === 1
      const only = singleImage ? result.images[0] : null
      const response = task.context.kind === 'group'
        ? await sendGroupChatAlbum({
          groupId: task.context.groupId,
          content: task.text || undefined,
          replyMessageId: task.context.replyMessageId,
          images: result.images,
        })
        : singleImage
          ? await sendImageMessage({
            receiveUserId: task.context.receiveUserId,
            messageType: String(only.mediaMime || '').toLowerCase() === 'image/gif' ? 2 : 1,
            mediaUrl: only.mediaUrl,
            mediaMime: only.mediaMime,
            mediaSize: only.mediaSize,
            mediaWidth: only.mediaWidth,
            mediaHeight: only.mediaHeight,
            content: task.text || undefined,
          })
          : await sendAlbumMessage({
            receiveUserId: task.context.receiveUserId,
            content: task.text || undefined,
            images: result.images,
          })
      if (response.code !== 0 || !response.data) {
        row.pendingAlbumState = 'failed'
        row.pendingAlbumError = response.message || '图集发送失败，请重试'
        return
      }
      const index = messages.value.indexOf(row)
      if (index >= 0) {
        messages.value.splice(index, 1, task.context.kind === 'group'
          ? mapGroupMessage(response.data)
          : { isOwner: true, message: response.data })
      }
      releaseAlbumTaskFiles(task)
      if (task.context.kind === 'group') await loadGroupSessions()
      else await loadSessions()
    } catch (error) {
      row.pendingAlbumState = 'failed'
      row.pendingAlbumError = error?.message || '图集发送失败，请重试'
    }
  }

  function retryPendingAlbum(row) {
    if (!row?.pendingAlbumTask || row.pendingAlbumState === 'auditing') return
    void processPendingAlbumTask(row.pendingAlbumTask)
  }

  function deletePendingAlbum(row) {
    removePendingAlbumRow(row)
  }

  async function sendPendingAlbum(text) {
    const source = pendingAlbumFiles.value.slice()
    const context = currentGroupSession.value
      ? {
        kind: 'group',
        groupId: Number(currentGroupSession.value.groupId),
        replyMessageId: replyTarget.value?.id,
      }
      : {
        kind: 'private',
        receiveUserId: Number(currentSession.value?.user?.id),
      }
    if (context.kind === 'private' && context.receiveUserId === Number(userStore.id)) {
      ElMessage.warning('不能给自己发私信')
      return
    }
    const localId = `pending-album-${Date.now()}-${Math.random().toString(16).slice(2)}`
    const row = {
      isOwner: true,
      pendingAlbumState: 'auditing',
      message: {
        id: localId,
        messageType: 4,
        content: text || '',
        createTime: new Date().toISOString(),
        albumImages: source.map((item, index) => ({ id: `pending-${index}`, mediaUrl: item.previewUrl })),
      },
    }
    const task = { row, items: source, allItems: source, context, text }
    row.pendingAlbumTask = task
    messages.value.push(row)
    sendContent.value = ''
    pendingAlbumFiles.value = []
    clearReplyTarget()
    await nextTick()
    scrollToBottom()
    void processPendingAlbumTask(task)
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
    if (currentGroupSession.value) {
      const gid = currentGroupSession.value.groupId
      if (!gid) return
      const loading = openImageUploadLoading(file, '正在上传群聊图片…')
      try {
        const up = await uploadChatImage(file)
        const mediaUrl = up.data
        const sendRes = await sendGroupChatMessage({
          groupId: gid,
          messageType: 2,
          content: mediaUrl,
          replyMessageId: replyTarget.value?.id,
        })
        if (sendRes.code === 0 && sendRes.data) {
          messages.value.push(mapGroupMessage(sendRes.data))
          clearReplyTarget()
          await nextTick()
          scrollToBottom()
          await loadGroupSessions()
        }
      } catch {
        // 拦截器已提示
      } finally {
        loading.close()
      }
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
      // 拦截器已提示
    } finally {
      loading.close()
    }
  }

  function triggerEmojiStickerPick() {
    emojiStickerInput.value?.click()
  }

  async function onEmojiStickerFileChange(e) {
    const files = Array.from(e.target.files || [])
    e.target.value = ''
    if (!files.length) return
    // 一次选多少传多少会把一整个相册灌进来。图集有十张的上限，
    // 表情商城有六十张，这里原来一个都没有
    let picked = files
    if (picked.length > EMOJI_UPLOAD_BATCH_MAX) {
      ElMessage.warning(`一次最多上传 ${EMOJI_UPLOAD_BATCH_MAX} 张，已保留前 ${EMOJI_UPLOAD_BATCH_MAX} 张`)
      picked = picked.slice(0, EMOJI_UPLOAD_BATCH_MAX)
    }
    const validFiles = []
    for (const file of picked) {
      const mimeOk = validateChatImageMime(file)
      if (!mimeOk.ok) {
        ElMessage.warning(`${file.name}：${mimeOk.message}`)
        continue
      }
      const sizeOk = validateLocalImageFile(file)
      if (!sizeOk.ok) {
        ElMessage.warning(`${file.name}：${sizeOk.message}`)
        continue
      }
      validFiles.push(file)
    }
    if (!validFiles.length) return

    emojiPanelTab.value = 'uploads'
    const slots = validFiles.map((file, index) => ({
      id: `up-${Date.now()}-${index}-${Math.random().toString(16).slice(2)}`,
      previewUrl: URL.createObjectURL(file),
      pending: true,
    }))
    uploadedPendingSlots.value = [...uploadedPendingSlots.value, ...slots]

    try {
      const result = await chatEmojiStore.uploadAndFavoriteMany(validFiles, {
        onItemDone: ({ index, ok }) => {
          const slot = slots[index]
          if (!slot) return
          if (slot.previewUrl) URL.revokeObjectURL(slot.previewUrl)
          uploadedPendingSlots.value = uploadedPendingSlots.value.filter((item) => item.id !== slot.id)
          if (!ok) {
            // 单张失败已由拦截器/收藏逻辑提示，这里只清槽
          }
        },
      })
      const okCount = Number(result?.okCount) || 0
      if (okCount > 0) {
        ElMessage.success(okCount === 1 ? '已添加到我的上传' : `已添加 ${okCount} 张到我的上传`)
        // 列表已经改成按时间降序，刚传的就在第一页，不用再跳到最后一页去找
        uploadedPage.value = 1
        await chatEmojiStore.fetchPage('uploaded', 1, FAVORITES_PAGE_SIZE)
          } else {
        ElMessage.warning('未能添加到我的上传，请稍后重试')
      }
    } catch {
      slots.forEach((slot) => {
        if (slot.previewUrl) URL.revokeObjectURL(slot.previewUrl)
      })
      const ids = new Set(slots.map((slot) => slot.id))
      uploadedPendingSlots.value = uploadedPendingSlots.value.filter((item) => !ids.has(item.id))
    }
  }

  async function onEmojiPopoverShow() {
    try {
      await chatEmojiStore.fetchBoth()
      if (userStore.isLoggedIn && emojiPanelTab.value === 'purchased') {
        await emojiShopStore.fetchMyPacks()
      }
          nextTick(updatePackBarScrollState)
    } catch {
      // store / 拦截器已提示
    }
  }

  async function onEmojiTabChange(name) {
    if (name === 'purchased' && userStore.isLoggedIn) {
      try {
        await emojiShopStore.fetchMyPacks()
      } catch {
        // 已提示
      }
    }
    if (name === 'favorites') {
      await chatEmojiStore.fetchPage('favorite', favoritePage.value, FAVORITES_PAGE_SIZE)
      }
    if (name === 'uploads') {
      await chatEmojiStore.fetchPage('uploaded', uploadedPage.value, FAVORITES_PAGE_SIZE)
      }
  }

  async function sendMessageFromShopUrl(mediaUrl) {
    const shopId = selectedPurchasedPack.value?.shopId
    if (currentGroupSession.value) {
      await sendGroupEmojiMessage(mediaUrl, shopId)
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
        emojiShopId: shopId,
      })
      if (sendRes.code === 0 && sendRes.data) {
        messages.value.push({ isOwner: true, message: sendRes.data })
        await nextTick()
        scrollToBottom()
        await loadSessions()
      }
    } catch {
      await emojiShopStore.fetchMyPacks().catch(() => {})
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
      // 已提示
    }
  }

  async function sendGroupEmojiMessage(mediaUrl, emojiShopId = null) {
    const gid = currentGroupSession.value?.groupId
    if (!gid || !mediaUrl) return
    try {
      const sendRes = await sendGroupChatMessage({
        groupId: gid,
        messageType: 1,
        content: mediaUrl,
        replyMessageId: replyTarget.value?.id,
        emojiShopId: emojiShopId || undefined,
      })
      if (sendRes.code === 0 && sendRes.data) {
        messages.value.push(mapGroupMessage(sendRes.data))
        clearReplyTarget()
        await nextTick()
        scrollToBottom()
        await loadGroupSessions()
      }
    } catch {
      if (emojiShopId) {
        await emojiShopStore.fetchMyPacks().catch(() => {})
      }
    }
  }

  function canFavoriteChatImage(msgRow) {
    return canFavoriteChatMediaMessage(msgRow?.message)
  }

  async function favoriteChatImage(msgRow) {
    const m = msgRow?.message
    if (!canFavoriteChatMediaMessage(m)) return
    try {
      const added = await chatEmojiStore.favoriteFromChatMessage(m)
      if (added) {
        await chatEmojiStore.fetchPage('favorite', 1, FAVORITES_PAGE_SIZE)
      }
    } catch {
      // 已提示
    }
  }

  function isMediaMessage(msg) {
    const t = Number(msg?.message?.messageType)
    return t === 1 || t === 2 || t === 4
  }

  function isAlbumMessage(msg) {
    return Number(msg?.message?.messageType) === 4
  }

  function isRecalledMessage(msg) {
    const state = Number(msg?.message?.state)
    return currentGroupSession.value ? state === 3 : state === 2
  }

  function albumGridColumns(msg) {
    const count = Array.isArray(msg?.message?.albumImages) ? msg.message.albumImages.length : 0
    return count <= 1 ? 1 : (count === 2 ? 2 : 3)
  }

  function openAlbumPreview(msgRow, index = 0) {
    const images = Array.isArray(msgRow?.message?.albumImages) ? msgRow.message.albumImages : []
    if (!images.length) return
    albumPreviewImages.value = images
    albumPreviewIndex.value = Math.min(Math.max(Number(index) || 0, 0), images.length - 1)
    albumPreviewVisible.value = true
  }

  function isEmojiShopGroupMedia(msgRow) {
    return isEmojiShopMediaUrl(msgRow?.message?.mediaUrl)
  }

  function isEmojiShopMessage(msgRow) {
    return isEmojiShopMediaUrl(msgRow?.message?.mediaUrl)
  }

  async function openEmojiShopFromMessage(msgRow) {
    const mediaUrl = msgRow?.message?.mediaUrl
    if (!mediaUrl) return
    try {
      const res = await getShopEmojiAvailability({ url: mediaUrl })
      const status = res.data?.status
      const shopId = res.data?.shopId
      // 已下架也跳过去：详情页会展示下架占位，和从评论点进来的表现一致。
      // 原来这里只弹一句提示不跳转，两个入口两种行为
      if (res.code === 0 && shopId && (status === 'AVAILABLE' || status === 'SERIES_OFFLINE')) {
        handleClose()
        await router.push({ path: '/emoji-shop', query: { detail: String(shopId) } })
        return
      }
      ElMessage.warning('该表情已被删除')
    } catch {
      // 请求拦截器已提示
    }
  }

  function bubbleAvatar(msg) {
    if (msg?.isOwner) return userStore.avatarUrl || defaultAvatar
    if (currentGroupSession.value) return msg?.user?.avatarUrl || defaultAvatar
    return currentSession.value?.user?.avatarUrl || defaultAvatar
  }

  function bubbleImageStyle(message) {
    const naturalSize = mediaNaturalSizes.value[mediaSizeKey(message)]
    const w = Number(message?.mediaWidth) > 0 ? message.mediaWidth : naturalSize?.width
    const h = Number(message?.mediaHeight) > 0 ? message.mediaHeight : naturalSize?.height
    if (w != null && h != null && Number(w) > 0 && Number(h) > 0) {
      const halfWidth = Math.max(1, Math.round(Number(w) / 2))
      return {
        width: `${Math.min(halfWidth, 120)}px`,
        aspectRatio: `${w} / ${h}`,
      }
    }
    return { width: '120px' }
  }

  function mediaSizeKey(message) {
    return String(message?.id || message?.mediaUrl || '')
  }

  function rememberBubbleNaturalSize(message, event) {
    // 尺寸定下来意味着气泡高度变了，滚动位置会偏；仍在跟随就重新贴底
    onBubbleMediaLoad()
    if (Number(message?.mediaWidth) > 0 && Number(message?.mediaHeight) > 0) return
    const image = event?.target || event
    const width = Number(image?.naturalWidth)
    const height = Number(image?.naturalHeight)
    const key = mediaSizeKey(message)
    if (!key || width <= 0 || height <= 0) return
    mediaNaturalSizes.value = {
      ...mediaNaturalSizes.value,
      [key]: { width, height },
    }
  }

  // 距底多少像素之内算「还在跟着最新消息」
  const NEAR_BOTTOM_PX = 120

  function isNearBottom() {
    const wrap = msgScrollbar.value?.wrapRef
    if (!wrap) return true
    return wrap.scrollHeight - wrap.scrollTop - wrap.clientHeight <= NEAR_BOTTOM_PX
  }

  // 滚动到底部附近就恢复跟随，并清掉未读浮标
  function onMessagesScroll() {
    following.value = isNearBottom()
    if (following.value) pendingNewCount.value = 0
    const wrap = msgScrollbar.value?.wrapRef
    if (wrap && wrap.scrollTop <= 40) void loadOlderMessages()
  }

  /**
   * 收到新消息后的定位。
   *
   * <p>务必在把消息塞进列表「之前」调用它取快照：一旦 push，scrollHeight 就变大了，
   * 再判断永远算不出「原本贴着底」。
   */
  function captureFollowing() {
    following.value = isNearBottom()
    return following.value
  }

  // 原本贴着底就跟到最新；正在翻历史就只攒一个角标，不打断阅读
  async function settleIncomingScroll(wasFollowing) {
    if (wasFollowing) {
      await scrollToBottom()
      pendingNewCount.value = 0
      return
    }
    pendingNewCount.value += 1
  }

  async function jumpToLatest() {
    pendingNewCount.value = 0
    following.value = true
    await scrollToBottom()
  }

  // 图片撑开气泡后原来的位置就偏了，若此刻仍在跟随则重新贴底
  function onBubbleMediaLoad() {
    if (following.value) void scrollToBottom()
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
    if (isMusicAuditMessage(msg)) {
      const payload = parseSystemMessagePayload(msg)
      const musicId = payload?.musicId || msg?.relatedId
      if (musicId) {
        router.push({ path: '/music-hall/mine', query: { musicId: String(musicId) } })
      } else {
        router.push('/music-hall/mine')
      }
      return
    }
    if (msg?.relatedId) router.push(`/article/${msg.relatedId}`)
  }

  return {
    AtSign,
    ArrowLeft,
    ArrowRight,
    Bell,
    ChatLineRound,
    ChatLineSquare,
    CircleCheck,
    Close,
    Document,
    Delete,
    RefreshLeft,
    Back,
    ImageIcon,
    LoaderCircle,
    RotateCcw,
    ShieldCheck,
    Smile,
    Trash2,
    Promotion,
    Plus,
    Search,
    Setting,
    Warning,
    UserFilled,
    activeJoinRequests,
    activeSystemMessages,
    activeTab,
    notificationSearch,
    notificationPage,
    notificationTotal,
    JOIN_REQUEST_PAGE_SIZE,
    SYSTEM_NOTIFY_PAGE_SIZE,
    onNotificationPageChange,
    onComposerKeydown,
    enterToSendEnabled,
    activeChatSubtitle,
    activeChatTitle,
    albumGridColumns,
    albumPreviewImages,
    albumPreviewIndex,
    albumPreviewVisible,
    emojiPackIconUrl,
    emojiPersonEmptyUrl,
    chatUnselectUrl,
    chatReportDialogVisible,
    chatReportSubmitting,
    searchChatEmptyUrl,
    autoResizeInput,
    bubbleAvatar,
    bubbleImageStyle,
    canFavoriteChatImage,
    canRecallMessage,
    canRecallGroupMessage,
    canShowGroupMessageActions,
    canRespondGroupInvite,
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
    filteredMentionMembers,
    focusedConvKey,
    formatSessionTime,
    formatGroupCreatedDate,
    formatJoinRequestTime,
    formatTime,
    onMentionMembersPageChange,
    onOwnedGroupPageChange,
    groupAdminVisible,
    groupAdminMembers,
    groupAdminSearch,
    groupAdminPage,
    groupAdminTotal,
    groupAdminLoading,
    groupAdminUpdatingId,
    groupAvatarText,
    groupAvatarUrl,
    groupNotifyOptions: GROUP_NOTIFY_OPTIONS,
    handleClose,
    handleRecall,
    inputBoxRef,
    isActiveItem,
    isCurrentGroupOwner,
    isCurrentGroupManager,
    isMemberMuted,
    isPrivateChat,
    isMediaMessage,
    isAlbumMessage,
    isEmojiShopGroupMedia,
    isEmojiShopMessage,
    isGroupInviteCard,
    isRecalledMessage,
    leaveCurrentGroup,
    listItems,
    privateSearchEmpty,
    hiddenManagementMode,
    textSearchLoading,
    memberDisplayName,
    memberMuteLabel,
    memberRoleLabel,
    ownedGroupInviteVisible,
    ownedGroupSearch,
    ownedGroupPage,
    ownedGroupTotal,
    ownedGroups,
    ownedGroupsLoading,
    ownedGroupMemberText,
    invitingGroupId,
    groupInviteInfo,
    groupInviteStatusText,
    following,
    isNearBottom,
    jumpToLatest,
    onBubbleMediaLoad,
    onMessagesScroll,
    pendingNewCount,
    loadingOlderMessages,
    messageHasMore,
    onSessionListScroll,
    sessionLoading,
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
    onGroupAvatarFileChange,
    onGroupIntroInput,
    onGroupAdminSearchInput,
    onGroupMemberSearchInput,
    onMentionSearchInput,
    openMentionPicker,
    openOwnedGroupInvitePicker,
    toggleMentionPicker,
    toggleGroupAdminPicker,
    toggleGroupAdminRole,
    openArticleFromSystem,
    openGroupSettings,
    openGroupMemberProfile,
    openMessageSenderProfile,
    openCurrentPeerProfile,
    openEmojiShopFromMessage,
    openAlbumPreview,
    downloadChatImage,
    downloadingMediaUrl,
    currentSessionMuted,
    mutingSession,
    toggleMuteCurrentSession,
    pinningPeerId,
    togglePinPrivateSession,
    sessionReadonly,
    hidePrivateSession,
    restorePrivateSession,
    toggleHiddenManagement,
    highlightSegments,
    parseSystemMessageContent,
    systemNotifyCardTitle,
    isMusicAuditMessage,
    peerOnline,
    groupAvatarInputRef,
    groupCreateForm,
    groupCreateVisible,
    groupEditDirty,
    groupEditForm,
    groupIntroEditorRef,
    groupListError,
    groupListLoading,
    groupMembers,
    groupMembersTotal,
    groupMemberSearch,
    groupMembersPage,
    groupMembersLoading,
    groupMemberSettingsDirty,
    groupSettingsVisible,
    groupSettingsPortalReady,
    groupTypeSwitchLocked,
    groupRemarkForm,
    removeMember,
    approveJoinRequestItem,
    rejectJoinRequestItem,
    acceptInviteCard,
    declineInviteCard,
    replyTarget,
    savingGroupEdit,
    savingGroupRemark,
    scrollToBottom,
    searchQuery,
    selfOnline,
    selectMentionMember,
    selectMentionAll,
    selectListItem,
    groupTypeLabel,
    openCreateGroup,
    submitCreateGroup,
    submitGroupEdit,
    submitGroupRemark,
    startReply,
    canReportChatMessage,
    submitChatMessageReport,
    confirmChatMessageReport,
    switchGroupType,
    setGroupNotifyMode,
    sendContent,
    sendGroupInviteFromPm,
    sendMessageFromEmoji,
    sendMessageFromShopUrl,
    sendMsg,
    sending,
    creatingGroup,
    muteMember,
    toggleMuteMember,
    sysIcon,
    sysTagClass,
    sysTagLabel,
    tabBadges,
    triggerGroupAvatarUpload,
    triggerChatImagePick,
    triggerEmojiStickerPick,
    beforeCloseGroupSettings,
    requestCloseGroupSettings,
    clearReplyTarget,
    mentionMembersPage,
    mentionMembersTotal,
    MENTION_PAGE_SIZE,
    mentionPopoverVisible,
    mentionSearch,
    userStore,
    uploadingGroupAvatar,
    viewerIsVip,
    favoriteEmojis,
    FAVORITES_PAGE_SIZE,
    favoritePage,
    favoritePagerTotal,
    onFavoritePageChange,
    onUploadedPageChange,
    onGroupMembersPageChange,
    onGroupAdminPageChange,
    onPackBarScroll,
    paginatedFavorites,
    paginatedGroupMembers,
    paginatedMentionMembers,
    paginatedUploaded,
    pendingAlbumFiles,
    retryPendingAlbum,
    deletePendingAlbum,
    removePendingAlbumFile,
    removeEmojiKeepPopover,
    rememberBubbleNaturalSize,
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
    uploadedPagerTotal,
    uploadedPendingSlots,
    visiblePacks,
    router,
  }
}

// 从路由 查询 打开消息中心 兼容 /messages?targetUserId
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
