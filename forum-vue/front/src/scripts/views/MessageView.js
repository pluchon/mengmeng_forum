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
  Phone,
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
  acceptGroupInvite,
  approveGroupJoinRequest,
  declineGroupInvite,
  dissolveGroupChat,
  getGroupChatMessages,
  getGroupChatMembers,
  getGroupJoinRequest,
  getGroupChatSessions,
  getOwnedGroupChats,
  getReceivedGroupJoinRequests,
  inviteGroupChatMember,
  leaveGroupChat,
  markGroupChatRead,
  markReceivedGroupJoinRequestsRead,
  muteGroupChatMember,
  rejectGroupJoinRequest,
  removeGroupChatMember,
  reportGroupChatMessage,
  sendGroupChatMessage,
  updateGroupChat,
  updateGroupMemberRole,
  updateMyGroupRemark,
  uploadGroupAvatar,
} from '@/api/groupChat'
import { useMessageStore } from '@/stores/message'
import { useChatEmojiStore } from '@/stores/chatEmoji'
import { useEmojiShopStore } from '@/stores/emojiShop'
import { useGroupVoiceStore } from '@/stores/groupVoice'
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
  joinRequest: { key: 'sys-group-join-request', name: '进群申请', listIcon: UserFilled },
  audit: { key: 'sys-group-audit', name: '帖子审核', listIcon: Document },
  notice: { key: 'sys-group-notice', name: '系统公告', listIcon: Bell },
  other: { key: 'sys-group-other', name: '系统通知', listIcon: Bell },
}

const GROUP_INVITE_CARD_PREFIX = '[[GROUP_INVITE:'

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
  const groupVoiceStore = useGroupVoiceStore()
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
  const receivedJoinRequests = ref([])
  const joinRequestsLoading = ref(false)
  const groupSettingsVisible = ref(false)
  const groupMembers = ref([])
  const groupMembersLoading = ref(false)
  const groupMembersPage = ref(1)
  const groupAdminManageMode = ref(false)
  const mentionMembersPage = ref(1)
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
  const selfOnline = ref(false)
  let onlinePollTimer = null
  let groupTypeSwitchTimer = null

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
    const pendingJoinCount = receivedJoinRequests.value.filter((item) => Number(item.status) === 0).length
    const joinRequestItem = {
      key: SYS_GROUP_META.joinRequest.key,
      kind: 'join-request-group',
      groupId: 'joinRequest',
      name: SYS_GROUP_META.joinRequest.name,
      requests: receivedJoinRequests.value,
      time: receivedJoinRequests.value[0]?.createTime,
      preview: pendingJoinCount > 0 ? `${pendingJoinCount} 条待处理申请` : '暂无待处理申请',
      unread: pendingJoinCount,
      listIcon: SYS_GROUP_META.joinRequest.listIcon,
    }
    const notifItems = [joinRequestItem, ...sysItems]
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
    else if (activeTab.value === 'notif') merged = notifItems
    else merged = pmItems
    if (!q) return merged
    return merged.filter((item) => item.name?.toLowerCase().includes(q)
      || item.preview?.toLowerCase().includes(q))
  })

  const tabBadges = computed(() => ({
    pm: messageStore.unreadCount || 0,
    group: groupUnreadCount.value,
    notif: (systemUnread.value || 0) + joinRequestUnreadCount.value,
  }))

  const groupUnreadCount = computed(() =>
    groupSessions.value.reduce((sum, item) => sum + (Number(item.unreadCount) || 0), 0),
  )

  const joinRequestUnreadCount = computed(() =>
    receivedJoinRequests.value.filter((item) => Number(item.ownerReadState) === 0).length,
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

  const isCurrentGroupManager = computed(() =>
    isCurrentGroupOwner.value || Number(myGroupMember.value?.role) === 2,
  )

  const currentGroupVoiceSession = computed(() => {
    const groupId = currentGroupSession.value?.groupId
    if (!groupId) return null
    return groupVoiceStore.sessionFor(groupId)
  })

  const currentGroupVoiceActive = computed(() =>
    currentGroupVoiceSession.value?.active === true,
  )

  const currentGroupVoiceManager = computed(() =>
    isCurrentGroupManager.value || currentGroupVoiceSession.value?.currentUserManager === true,
  )

  const showGroupVoiceEntry = computed(() =>
    !!currentGroupSession.value && (currentGroupVoiceActive.value || currentGroupVoiceManager.value),
  )

  const groupVoiceEntryText = computed(() => {
    if (currentGroupVoiceActive.value) {
      const count = Number(currentGroupVoiceSession.value?.memberCount) || 0
      return `${count}人正在聊天`
    }
    return '发起语音'
  })

  const currentPrivateVoiceSession = computed(() => {
    const peerUserId = currentSession.value?.user?.id
    if (!peerUserId) return null
    return groupVoiceStore.privateSessionFor(peerUserId)
  })

  const currentPrivateVoiceActive = computed(() =>
    currentPrivateVoiceSession.value?.active === true,
  )

  const showPrivateVoiceEntry = computed(() =>
    !!currentSession.value?.user?.id && !currentSession.value?._virtual,
  )

  const privateVoiceEntryText = computed(() => {
    if (!currentPrivateVoiceActive.value) return '发起语音'
    if (privateVoiceWaiting.value) return '等待对方回应...'
    if (currentPrivateVoiceSession.value?.currentUserJoined) return '语音中'
    return '接听语音'
  })

  const privateVoiceWaiting = computed(() =>
    currentPrivateVoiceActive.value
    && currentPrivateVoiceSession.value?.currentUserJoined === true
    && currentPrivateVoiceSession.value?.currentUserInitiator === true
    && (Number(currentPrivateVoiceSession.value?.memberCount) || 0) < 2,
  )

  const showPrivateVoiceAnswerActions = computed(() =>
    !!currentSession.value?.user?.id
    && currentPrivateVoiceActive.value
    && currentPrivateVoiceSession.value?.currentUserJoined !== true,
  )

  const showVoiceEntry = computed(() =>
    currentSession.value
      ? showPrivateVoiceEntry.value && !showPrivateVoiceAnswerActions.value
      : showGroupVoiceEntry.value,
  )

  const voiceEntryText = computed(() =>
    currentSession.value ? privateVoiceEntryText.value : groupVoiceEntryText.value,
  )

  const voiceEntryActive = computed(() =>
    currentSession.value
      ? currentPrivateVoiceActive.value && !privateVoiceWaiting.value
      : currentGroupVoiceActive.value,
  )

  const groupMembersTotalPages = computed(() =>
    Math.max(1, Math.ceil(groupMembers.value.length / 50)),
  )

  const myGroupMember = computed(() =>
    groupMembers.value.find((member) => Number(member?.user?.id) === Number(userStore.id)) || null,
  )

  const groupMemberSettingsDirty = computed(() =>
    groupMemberSettingsSnapshot.value !== serializeGroupMemberSettings(),
  )

  const groupEditDirty = computed(() =>
    isCurrentGroupOwner.value
      && (groupEditSnapshot.value !== serializeGroupEditForm() || groupMemberSettingsDirty.value),
  )

  const filteredMentionMembers = computed(() => {
    const keyword = mentionSearch.value.trim().toLowerCase()
    return groupMembers.value
      .slice()
      .sort(sortGroupMember)
      .filter((member) => {
        const name = memberDisplayName(member).toLowerCase()
        return !keyword || name.includes(keyword) || String(member?.user?.id || '').includes(keyword)
      })
  })

  const mentionMembersTotalPages = computed(() =>
    Math.max(1, Math.ceil(filteredMentionMembers.value.length / MENTION_PAGE_SIZE)),
  )

  const paginatedMentionMembers = computed(() => {
    const start = (mentionMembersPage.value - 1) * MENTION_PAGE_SIZE
    return filteredMentionMembers.value.slice(start, start + MENTION_PAGE_SIZE)
  })

  const paginatedGroupMembers = computed(() => {
    const start = (groupMembersPage.value - 1) * 50
    return groupMembers.value
      .slice()
      .sort(sortGroupMember)
      .slice(start, start + 50)
  })

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
const MENTION_PAGE_SIZE = 5
const GROUP_NOTIFY_OPTIONS = [
  { value: 0, label: '关闭' },
  { value: 1, label: '仅@消息提醒' },
  { value: 2, label: '完全不提醒' },
]
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

  const activeJoinRequests = computed(() => {
    if (currentSystemGroup.value?.groupId !== 'joinRequest') return []
    return receivedJoinRequests.value
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

  async function loadReceivedJoinRequests() {
    joinRequestsLoading.value = true
    try {
      const res = await getReceivedGroupJoinRequests({ pageNum: 1, pageSize: 50 })
      if (res.code === 0) {
        receivedJoinRequests.value = unwrapPageRecords(res.data)
      }
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
    if (messageCenterUi.visible) {
      bootstrap()
      startOnlinePolling()
    }
  })

  onUnmounted(() => {
    stopOnlinePolling()
    if (groupTypeSwitchTimer) {
      clearTimeout(groupTypeSwitchTimer)
      groupTypeSwitchTimer = null
    }
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
    if (messageCenterUi.visible) {
      loadSystemMessages()
      loadReceivedJoinRequests()
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
        mediaUrl: [1, 2].includes(Number(row.messageType)) ? row.content : undefined,
        replyMessageId: row.replyMessageId,
        replySenderName: row.replySenderName,
        replyContent: row.replyContent,
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
      await loadGroupInviteCards()
      await scrollToBottom()
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
        /* ignore */
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
    await refreshCurrentPrivateVoice()
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

  async function selectJoinRequestGroup(item) {
    currentSession.value = null
    currentGroupSession.value = null
    currentSystemGroup.value = {
      groupId: item.groupId,
      name: item.name,
      messages: [],
    }
    focusedConvKey.value = item.key
    const hasUnread = receivedJoinRequests.value.some((request) => Number(request.ownerReadState) === 0)
    if (hasUnread) {
      receivedJoinRequests.value.forEach((request) => {
        request.ownerReadState = 1
      })
      try {
        await markReceivedGroupJoinRequestsRead()
      } catch {
        /* ignore */
      }
    }
    await loadReceivedJoinRequests()
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
    await Promise.all([loadReceivedJoinRequests(), loadGroupSessions()])
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
    await loadReceivedJoinRequests()
  }

  async function refreshCurrentGroupVoice() {
    const groupId = currentGroupSession.value?.groupId
    if (!groupId) return
    try {
      await groupVoiceStore.fetchSession(groupId)
    } catch {
      /* ignore */
    }
  }

  async function refreshCurrentPrivateVoice() {
    const peerUserId = currentSession.value?.user?.id
    if (!peerUserId || currentSession.value?._virtual) return
    try {
      await groupVoiceStore.fetchPrivateSession(peerUserId)
    } catch {
      /* ignore */
    }
  }

  async function handleVoiceEntry() {
    if (currentSession.value) {
      await handlePrivateVoiceEntry()
      return
    }
    await handleGroupVoiceEntry()
  }

  async function handlePrivateVoiceEntry() {
    const peerUserId = currentSession.value?.user?.id
    if (!peerUserId || currentSession.value?._virtual) return
    if (currentPrivateVoiceActive.value) {
      if (privateVoiceWaiting.value) {
        ElMessage.info('等待对方接听中')
        return
      }
      if (currentPrivateVoiceSession.value?.currentUserJoined) {
        try {
          await groupVoiceStore.openVoiceDialog()
        } catch {
          /* 已提示 */
        }
        return
      }
      await ElMessageBox.confirm('接听当前私聊语音吗？', '接听语音', {
        confirmButtonText: '接听',
        cancelButtonText: '取消',
        type: 'warning',
      })
      try {
        await groupVoiceStore.acceptPrivate(peerUserId)
      } catch {
        /* 已提示 */
      }
      return
    }
    await ElMessageBox.confirm('确认发起私聊语音吗？', '发起语音', {
      confirmButtonText: '发起',
      cancelButtonText: '取消',
      type: 'warning',
    })
    try {
      await groupVoiceStore.startPrivate(peerUserId)
    } catch {
      /* 已提示 */
    }
  }

  async function handleAcceptPrivateVoice() {
    const peerUserId = currentSession.value?.user?.id
    if (!peerUserId || currentSession.value?._virtual) return
    try {
      await groupVoiceStore.acceptPrivate(peerUserId)
    } catch {
      /* 已提示 */
    }
  }

  async function handleDeclinePrivateVoice() {
    const peerUserId = currentSession.value?.user?.id
    if (!peerUserId || currentSession.value?._virtual) return
    try {
      await groupVoiceStore.declinePrivate(peerUserId)
      ElMessage.success('已拒绝语音邀请')
    } catch {
      /* 已提示 */
    }
  }

  async function handleGroupVoiceEntry() {
    const groupId = currentGroupSession.value?.groupId
    if (!groupId) return
    if (currentGroupVoiceActive.value) {
      if (currentGroupVoiceSession.value?.currentUserJoined) {
        try {
          await groupVoiceStore.openVoiceDialog()
        } catch {
          /* 已提示 */
        }
        return
      }
      await ElMessageBox.confirm('加入当前群语音聊天吗？', '加入语音', {
        confirmButtonText: '加入',
        cancelButtonText: '取消',
        type: 'warning',
      })
      try {
        await groupVoiceStore.join(groupId)
      } catch {
        /* 已提示 */
      }
      return
    }
    if (!currentGroupVoiceManager.value) {
      ElMessage.warning('只有群主或管理员可以发起语音聊天')
      return
    }
    await ElMessageBox.confirm('确认发起群语音聊天吗？', '发起语音', {
      confirmButtonText: '发起',
      cancelButtonText: '取消',
      type: 'warning',
    })
    try {
      await groupVoiceStore.start(groupId)
    } catch {
      /* 已提示 */
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
    await refreshCurrentGroupVoice()
  }

  async function selectListItem(item) {
    focusedConvKey.value = item.key
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
    } catch {
      /* 拦截器已提示 */
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
    groupSettingsVisible.value = true
    groupAdminManageMode.value = false
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
      const res = await getGroupChatMembers(gid)
      if (res.code === 0) {
        groupMembers.value = Array.isArray(res.data) ? res.data : []
        groupMembersPage.value = 1
        mentionMembersPage.value = 1
        groupRemarkForm.value = {
          remarkName: myGroupMember.value?.remarkName || '',
          notifyMode: Number(myGroupMember.value?.notifyMode) || 0,
        }
        setGroupMemberSettingsSnapshot()
      }
    } finally {
      groupMembersLoading.value = false
    }
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
    ElMessageBox.confirm('群资料修改后需要点击保存才会生效，确认先关闭吗？', '群资料未保存', {
      confirmButtonText: '关闭',
      cancelButtonText: '继续编辑',
      type: 'warning',
    }).then(() => done()).catch(() => {})
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
        const me = myGroupMember.value
        if (me) me.remarkName = res.data?.remarkName || ''
        if (me) me.notifyMode = Number(res.data?.notifyMode) || 0
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
    await ElMessageBox.confirm('确认退出当前群聊吗？退出后不会再接收新消息。', '退出群聊', {
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
    await ElMessageBox.confirm('确认解散当前群聊吗？解散后成员将无法继续聊天。', '解散群聊', {
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

  function toggleMuteMember(member) {
    return muteMember(member, isMemberMuted(member) ? 0 : 30)
  }

  async function reportGroupMessage(msgRow) {
    const gid = currentGroupSession.value?.groupId
    const mid = coerceMessageId(msgRow?.message?.id)
    if (!gid || !Number.isFinite(mid) || mid <= 0 || msgRow?.isOwner) return
    let value
    try {
      const result = await ElMessageBox.prompt('', '举报群消息', {
        confirmButtonText: '提交',
        cancelButtonText: '取消',
        customClass: 'group-message-report-box',
        inputType: 'textarea',
        inputPlaceholder: '举报原因',
        inputValidator: (input) => {
          const reason = String(input || '').trim()
          if (!reason) return '举报原因不能为空'
          if (reason.length > 200) return '举报原因不能超过 200 个字'
          return true
        },
      })
      value = result.value
    } catch {
      return
    }
    await reportGroupChatMessage(gid, {
      messageId: mid,
      reason: String(value || '').trim(),
    })
    ElMessage.success('举报已提交')
  }

  function openMentionPicker() {
    if (!currentGroupSession.value) return
    mentionSearch.value = ''
    mentionMembersPage.value = 1
    mentionPopoverVisible.value = true
    if (!groupMembers.value.length) {
      loadGroupMembers()
    }
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

  async function goOwnedGroupPrev() {
    if (ownedGroupPage.value <= 1) return
    ownedGroupPage.value -= 1
    await loadOwnedGroupsForInvite()
  }

  async function goOwnedGroupNext() {
    if (ownedGroupPage.value >= ownedGroupTotalPages.value) return
    ownedGroupPage.value += 1
    await loadOwnedGroupsForInvite()
  }

  function ownedGroupMemberText(group) {
    return `${Number(group?.memberCount) || 0}/${Number(group?.memberLimit) || 0} 人`
  }

  async function sendGroupInviteFromPm(group) {
    const gid = group?.id
    const inviteeUserId = currentSession.value?.user?.id
    if (!gid || !inviteeUserId) return
    await ElMessageBox.confirm(`确认邀请对方加入「${group.name || '群聊'}」吗？`, '发送入群邀请', {
      confirmButtonText: '发送',
      cancelButtonText: '取消',
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

  function goMentionMembersPrev() {
    if (mentionMembersPage.value > 1) mentionMembersPage.value -= 1
  }

  function goMentionMembersNext() {
    if (mentionMembersPage.value < mentionMembersTotalPages.value) mentionMembersPage.value += 1
  }

  function setGroupNotifyMode(mode) {
    groupRemarkForm.value.notifyMode = Number(mode)
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
    if (type === 3) return '[语音聊天]'
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

  function toggleGroupAdminManageMode() {
    groupAdminManageMode.value = !groupAdminManageMode.value
  }

  async function toggleGroupAdminRole(member) {
    const gid = currentGroupSession.value?.groupId
    const targetUserId = member?.user?.id
    if (!gid || !targetUserId || Number(member?.role) === 0) return
    const nextRole = Number(member.role) === 2 ? 1 : 2
    const actionText = nextRole === 2 ? '设置为管理员' : '取消管理员'
    await ElMessageBox.confirm(`确认${actionText}「${member.user?.nickname || targetUserId}」吗？`, actionText, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await updateGroupMemberRole(gid, targetUserId, nextRole)
    ElMessage.success(nextRole === 2 ? '已设置管理员' : '已取消管理员')
    await loadGroupMembers()
  }

  function memberMuteLabel(member) {
    if (!isMemberMuted(member)) return ''
    return `禁言至 ${formatSessionTime(member.muteUntil)}`
  }

  function isMemberMuted(member) {
    const until = parseForumDateTime(member?.muteUntil)
    return !!until && until.getTime() > Date.now()
  }

  function goGroupMembersPrev() {
    if (groupMembersPage.value > 1) groupMembersPage.value -= 1
  }

  function goGroupMembersNext() {
    if (groupMembersPage.value < groupMembersTotalPages.value) groupMembersPage.value += 1
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
        /* 拦截器已提示 */
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

  function isVoiceCallMessage(msg) {
    return Number(msg?.message?.messageType) === 3
  }

  function voiceCallDurationText(msg) {
    return String(msg?.message?.content || '00:00').trim() || '00:00'
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
    Phone,
    Plus,
    Search,
    Setting,
    Warning,
    UserFilled,
    activeJoinRequests,
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
    formatJoinRequestTime,
    formatTime,
    goMentionMembersNext,
    goMentionMembersPrev,
    goOwnedGroupNext,
    goOwnedGroupPrev,
    groupAdminManageMode,
    groupAvatarText,
    groupAvatarUrl,
    groupVoiceEntryText,
    groupNotifyOptions: GROUP_NOTIFY_OPTIONS,
    handleClose,
    handleGroupVoiceEntry,
    handleAcceptPrivateVoice,
    handleDeclinePrivateVoice,
    handleVoiceEntry,
    handleRecall,
    inputBoxRef,
    isActiveItem,
    isCurrentGroupOwner,
    isCurrentGroupManager,
    isMemberMuted,
    isPrivateChat,
    isMediaMessage,
    isVoiceCallMessage,
    isGroupInviteCard,
    leaveCurrentGroup,
    listItems,
    memberDisplayName,
    memberMuteLabel,
    memberRoleLabel,
    ownedGroupInviteVisible,
    ownedGroupSearch,
    ownedGroupPage,
    ownedGroupTotalPages,
    ownedGroups,
    ownedGroupsLoading,
    ownedGroupMemberText,
    invitingGroupId,
    groupInviteInfo,
    groupInviteStatusText,
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
    openMentionPicker,
    openOwnedGroupInvitePicker,
    toggleMentionPicker,
    toggleGroupAdminManageMode,
    toggleGroupAdminRole,
    openArticleFromSystem,
    openGroupSettings,
    openGroupMemberProfile,
    openMessageSenderProfile,
    openPeerProfile,
    parseSystemMessageContent,
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
    groupMembersPage,
    groupMembersLoading,
    groupMembersTotalPages,
    groupMemberSettingsDirty,
    groupSettingsVisible,
    groupTypeSwitchLocked,
    groupRemarkForm,
    removeMember,
    approveJoinRequestItem,
    rejectJoinRequestItem,
    acceptInviteCard,
    declineInviteCard,
    reportGroupMessage,
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
    clearReplyTarget,
    mentionMembersPage,
    mentionMembersTotalPages,
    mentionPopoverVisible,
    mentionSearch,
    userStore,
    uploadingGroupAvatar,
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
    goGroupMembersNext,
    goGroupMembersPrev,
    onPackBarScroll,
    paginatedFavorites,
    paginatedGroupMembers,
    paginatedMentionMembers,
    paginatedUploaded,
    removeEmojiKeepPopover,
    showUploadOnCurrentPage,
    showGroupVoiceEntry,
    showPrivateVoiceAnswerActions,
    showVoiceEntry,
    voiceEntryActive,
    voiceEntryText,
    voiceCallDurationText,
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
