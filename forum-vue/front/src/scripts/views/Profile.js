import { ref, onMounted, onActivated, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Star, Camera, Plus, Lock, Unlock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { getArticleListWithUser } from '@/api/article'
import { getMyLikeList } from '@/api/like'
import {
  getFavoriteFolderArticles,
  getMyFavoriteFolders,
  getUserFavoriteFolders,
  createFavoriteFolder,
  updateFavoriteFolder,
} from '@/api/favorite'
import { uploadProfileBackground, updateBackgroundUrl } from '@/api/settings'
import { followUser, unfollowUser, getFollowStats } from '@/api/userFollow'
import {
  getGroupChatSessions,
  getPublicGroupChats,
  getUserPublicGroupChats,
  joinPublicGroupChat,
} from '@/api/groupChat'
import { ElMessage } from 'element-plus'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'
import { clientOssUrl } from '@/utils/clientOss'
import { formatChatSessionTimeShanghai } from '@/utils/datetime'

const PROFILE_PAGE_SIZE = 12
const FAVORITE_DIALOG_PAGE_SIZE = 10
const PUBLIC_GROUP_PAGE_SIZE = 10
const PROFILE_RETURN_KEY = 'profile-return-state'

export function useProfile() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const messageCenterUi = useMessageCenterUiStore()
  const defaultAvatar = DEFAULT_AVATAR
  const defaultBg = clientOssUrl('profileb_back.webp')

  const userInfo = ref(null)
  const articles = ref([])
  const loading = ref(true)
  const activeTab = ref('notes')
  const total = ref(0)
  const notesPageNum = ref(1)
  const notesTotal = ref(0)
  const notesPageInput = ref('1')

  const bgFileInput = ref(null)
  const likedArticles = ref([])
  const likedPageNum = ref(1)
  const likedTotal = ref(0)
  const likedPageInput = ref('1')

  const favoriteFolders = ref([])
  const loadingFavorites = ref(false)
  const favoriteDialogVisible = ref(false)
  const favoriteDialogLoading = ref(false)
  const favoriteDialogTitle = ref('收藏')
  const favoriteDialogItems = ref([])
  const favoriteDialogPageNum = ref(1)
  const favoriteDialogTotal = ref(0)
  const favoriteDialogPageInput = ref('1')
  const favoriteFolderRenaming = ref(false)
  const favoriteFolderRenameValue = ref('')
  const activeFavoriteFolder = ref(null)
  const favoriteFolderPublic = ref(1)
  const favoriteVisibilitySaving = ref(false)

  const favoriteCreateVisible = ref(false)
  const favoriteCreateSaving = ref(false)
  const favoriteCreateForm = ref({ name: '', isPublic: 1 })

  const publicGroups = ref([])
  const publicGroupsLoading = ref(false)
  const publicGroupsPageNum = ref(1)
  const publicGroupsTotal = ref(0)
  const publicGroupsPageInput = ref('1')
  const myGroupSessions = ref([])
  const joiningGroupId = ref(null)

  const followingCount = ref(0)
  const followerCount = ref(0)
  const isFollowing = ref(false)
  const followSaving = ref(false)

  const profileIpRegion = computed(() => {
    if (userInfo.value?.ipRegion) return userInfo.value.ipRegion
    if (isMe.value && userStore.ipRegion) return userStore.ipRegion
    return ''
  })

  watch(activeTab, (tab) => {
    if (tab === 'liked' && likedArticles.value.length === 0) {
      loadLikedArticles(1)
    }
    if (tab === 'collect' && favoriteFolders.value.length === 0) {
      loadFavoriteFolders()
    }
    if (tab === 'groups' && publicGroups.value.length === 0) {
      loadPublicGroups(1)
    }
  })

  const notesTotalPages = computed(() =>
    Math.max(1, Math.ceil((notesTotal.value || 0) / PROFILE_PAGE_SIZE)),
  )

  const likedTotalPages = computed(() =>
    Math.max(1, Math.ceil((likedTotal.value || 0) / PROFILE_PAGE_SIZE)),
  )

  const favoriteDialogTotalPages = computed(() =>
    Math.max(1, Math.ceil((favoriteDialogTotal.value || 0) / FAVORITE_DIALOG_PAGE_SIZE)),
  )

  const publicGroupsTotalPages = computed(() =>
    Math.max(1, Math.ceil((publicGroupsTotal.value || 0) / PUBLIC_GROUP_PAGE_SIZE)),
  )

  const joinedGroupIds = computed(() =>
    new Set(myGroupSessions.value.map((item) => String(item.groupId))),
  )

  async function loadLikedArticles(page = 1) {
    if (!isMe.value) return
    likedPageNum.value = page
    likedPageInput.value = String(page)
    try {
      const res = await getMyLikeList({
        pageNum: likedPageNum.value,
        pageSize: PROFILE_PAGE_SIZE,
      })
      if (res.code === 0) {
        likedArticles.value = res.data?.records || []
        likedTotal.value = Number(res.data?.total) || 0
      }
    } catch (e) {
      console.warn('加载点赞列表失败:', e)
    }
  }

  async function loadFavoriteFolders() {
    const userId = route.params.id || userStore.id
    loadingFavorites.value = true
    try {
      const res = isMe.value
        ? await getMyFavoriteFolders()
        : await getUserFavoriteFolders(userId)
      if (res.code === 0) {
        favoriteFolders.value = res.data || []
      }
    } finally {
      loadingFavorites.value = false
    }
  }

  function openCreateFavoriteFolder() {
    favoriteCreateForm.value = { name: '', isPublic: 1 }
    favoriteCreateVisible.value = true
  }

  async function saveFavoriteFolder() {
    const name = favoriteCreateForm.value.name?.trim()
    if (!name) {
      ElMessage.warning('请输入收藏夹名称')
      return
    }
    favoriteCreateSaving.value = true
    try {
      const res = await createFavoriteFolder({
        name,
        isPublic: favoriteCreateForm.value.isPublic,
      })
      if (res.code === 0) {
        ElMessage.success('已创建')
        favoriteCreateVisible.value = false
        await loadFavoriteFolders()
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    } finally {
      favoriteCreateSaving.value = false
    }
  }

  const bgStyle = computed(() => `url(${userInfo.value?.backgroundUrl || defaultBg})`)

  const avatarSrc = computed(() => {
    if (isMe.value && userStore.avatarUrl) return userStore.avatarUrl
    return userInfo.value?.avatarUrl || defaultAvatar
  })

  const displayVipTier = computed(() => {
    if (isMe.value) return Number(userStore.vipTier) || 0
    return Number(userInfo.value?.vipTier) || 0
  })

  const displayVipExpireAt = computed(() => {
    if (isMe.value) return userStore.vipExpireAt ?? null
    return userInfo.value?.vipExpireAt ?? null
  })

  const showVipBadge = computed(() => {
    const t = Number(displayVipTier.value) || 0
    if (t <= 0) return false
    const exp = displayVipExpireAt.value
    if (!exp) return true
    const ms = new Date(exp).getTime()
    if (Number.isNaN(ms)) return true
    return Date.now() <= ms
  })

  const isMe = computed(() => {
    const targetId = route.params.id || userStore.id
    return String(targetId) === String(userStore.id)
  })

  onMounted(async () => {
    await loadProfile(1)
    await tryRestoreProfileState()
  })

  onActivated(async () => {
    await tryRestoreProfileState()
  })

  watch(() => route.params.id, async () => {
    resetPublicGroups()
    await loadProfile(1)
    if (activeTab.value === 'groups') {
      await loadPublicGroups(1)
    }
    await tryRestoreProfileState()
  })

  async function loadProfile(page = 1) {
    loading.value = true
    const userId = route.params.id || userStore.id
    notesPageNum.value = page
    notesPageInput.value = String(page)
    try {
      const res = await getArticleListWithUser({
        userId,
        pageNum: notesPageNum.value,
        pageSize: PROFILE_PAGE_SIZE,
      })
      if (res.code === 0) {
        userInfo.value = res.data.user
        articles.value = res.data.records || []
        total.value = res.data.total || 0
        notesTotal.value = Number(res.data.total) || 0
      }
      await loadFollowStats(userId)
    } catch (e) {
      console.error('加载个人主页失败:', e)
    } finally {
      loading.value = false
    }
  }

  function goNotesFirst() {
    loadProfile(1)
  }

  function goNotesPrev() {
    if (notesPageNum.value > 1) loadProfile(notesPageNum.value - 1)
  }

  function goNotesNext() {
    if (notesPageNum.value < notesTotalPages.value) loadProfile(notesPageNum.value + 1)
  }

  function jumpNotesPage() {
    const n = Number(notesPageInput.value)
    if (!Number.isFinite(n)) return
    loadProfile(Math.min(notesTotalPages.value, Math.max(1, Math.floor(n))))
  }

  function goLikedFirst() {
    loadLikedArticles(1)
  }

  function goLikedPrev() {
    if (likedPageNum.value > 1) loadLikedArticles(likedPageNum.value - 1)
  }

  function goLikedNext() {
    if (likedPageNum.value < likedTotalPages.value) loadLikedArticles(likedPageNum.value + 1)
  }

  function jumpLikedPage() {
    const n = Number(likedPageInput.value)
    if (!Number.isFinite(n)) return
    loadLikedArticles(Math.min(likedTotalPages.value, Math.max(1, Math.floor(n))))
  }

  async function loadMyGroupSessions() {
    if (!isMe.value) return
    try {
      const res = await getGroupChatSessions({ pageNum: 1, pageSize: 100 })
      if (res.code === 0) {
        myGroupSessions.value = res.data?.records || []
      }
    } catch {
      myGroupSessions.value = []
    }
  }

  async function loadPublicGroups(page = 1) {
    const userId = route.params.id || userStore.id
    publicGroupsPageNum.value = page
    publicGroupsPageInput.value = String(page)
    publicGroupsLoading.value = true
    try {
      const [groupsRes] = await Promise.all([
        isMe.value
          ? getPublicGroupChats({ pageNum: page, pageSize: PUBLIC_GROUP_PAGE_SIZE })
          : getUserPublicGroupChats(userId, { pageNum: page, pageSize: PUBLIC_GROUP_PAGE_SIZE }),
        loadMyGroupSessions(),
      ])
      if (groupsRes.code === 0) {
        publicGroups.value = groupsRes.data?.records || []
        publicGroupsTotal.value = Number(groupsRes.data?.total) || 0
      }
    } finally {
      publicGroupsLoading.value = false
    }
  }

  function resetPublicGroups() {
    publicGroups.value = []
    publicGroupsTotal.value = 0
    publicGroupsPageNum.value = 1
    publicGroupsPageInput.value = '1'
  }

  function goPublicGroupsFirst() {
    loadPublicGroups(1)
  }

  function goPublicGroupsPrev() {
    if (publicGroupsPageNum.value > 1) loadPublicGroups(publicGroupsPageNum.value - 1)
  }

  function goPublicGroupsNext() {
    if (publicGroupsPageNum.value < publicGroupsTotalPages.value) {
      loadPublicGroups(publicGroupsPageNum.value + 1)
    }
  }

  function jumpPublicGroupsPage() {
    const n = Number(publicGroupsPageInput.value)
    if (!Number.isFinite(n)) return
    loadPublicGroups(Math.min(publicGroupsTotalPages.value, Math.max(1, Math.floor(n))))
  }

  async function applyJoinPublicGroup(group) {
    const gid = group?.id
    if (!gid || joinedGroupIds.value.has(String(gid))) return
    joiningGroupId.value = gid
    try {
      const res = await joinPublicGroupChat(gid)
      if (res.code === 0) {
        ElMessage.success('已加入群聊')
        await Promise.all([loadMyGroupSessions(), loadPublicGroups(publicGroupsPageNum.value)])
      }
    } finally {
      joiningGroupId.value = null
    }
  }

  function groupAvatarText(group) {
    const name = group?.name || '群'
    return String(name).trim().charAt(0) || '群'
  }

  function isJoinedPublicGroup(group) {
    return joinedGroupIds.value.has(String(group?.id))
  }

  function formatProfileDate(time) {
    return formatChatSessionTimeShanghai(time)
  }

  async function openFavoriteDialog(folder) {
    const fid = folder?.id
    if (!fid) return
    activeFavoriteFolder.value = folder
    favoriteFolderPublic.value = Number(folder.isPublic) === 1 ? 1 : 0
    favoriteDialogTitle.value = folder.name || '收藏'
    favoriteFolderRenaming.value = false
    favoriteDialogVisible.value = true
    await loadFavoriteDialogArticles(1)
  }

  async function loadFavoriteDialogArticles(page = 1) {
    const fid = activeFavoriteFolder.value?.id
    if (!fid) return
    favoriteDialogPageNum.value = page
    favoriteDialogPageInput.value = String(page)
    favoriteDialogLoading.value = true
    try {
      const res = await getFavoriteFolderArticles(fid, {
        pageNum: favoriteDialogPageNum.value,
        pageSize: FAVORITE_DIALOG_PAGE_SIZE,
      })
      if (res.code === 0) {
        favoriteDialogItems.value = res.data?.records || []
        favoriteDialogTotal.value = Number(res.data?.total) || 0
      }
    } finally {
      favoriteDialogLoading.value = false
    }
  }

  function goFavoriteDialogFirst() {
    loadFavoriteDialogArticles(1)
  }

  function goFavoriteDialogPrev() {
    if (favoriteDialogPageNum.value > 1) loadFavoriteDialogArticles(favoriteDialogPageNum.value - 1)
  }

  function goFavoriteDialogNext() {
    if (favoriteDialogPageNum.value < favoriteDialogTotalPages.value) {
      loadFavoriteDialogArticles(favoriteDialogPageNum.value + 1)
    }
  }

  function jumpFavoriteDialogPage() {
    const n = Number(favoriteDialogPageInput.value)
    if (!Number.isFinite(n)) return
    loadFavoriteDialogArticles(Math.min(favoriteDialogTotalPages.value, Math.max(1, Math.floor(n))))
  }

  function startFavoriteFolderRename() {
    if (!isMe.value) return
    favoriteFolderRenameValue.value = activeFavoriteFolder.value?.name || ''
    favoriteFolderRenaming.value = true
  }

  async function confirmFavoriteFolderRename() {
    const folder = activeFavoriteFolder.value
    if (!folder?.id) return
    const name = favoriteFolderRenameValue.value.trim().slice(0, 25)
    if (!name) {
      ElMessage.warning('请输入收藏夹名称')
      return
    }
    const res = await updateFavoriteFolder({ folderId: folder.id, name })
    if (res.code === 0) {
      folder.name = name
      favoriteDialogTitle.value = name
      const idx = favoriteFolders.value.findIndex((f) => f.id === folder.id)
      if (idx >= 0) favoriteFolders.value[idx].name = name
      favoriteFolderRenaming.value = false
      ElMessage.success('已更新名称')
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  }

  function saveProfileReturnState(extra = {}) {
    const state = {
      tab: activeTab.value,
      profileUserId: route.params.id || userStore.id,
      scrollY: window.scrollY,
      ...extra,
    }
    sessionStorage.setItem(PROFILE_RETURN_KEY, JSON.stringify(state))
  }

  async function tryRestoreProfileState() {
    const raw = sessionStorage.getItem(PROFILE_RETURN_KEY)
    if (!raw) return
    let state
    try {
      state = JSON.parse(raw)
    } catch {
      sessionStorage.removeItem(PROFILE_RETURN_KEY)
      return
    }
    if (String(state.profileUserId) !== String(route.params.id || userStore.id)) return
    sessionStorage.removeItem(PROFILE_RETURN_KEY)
    if (state.tab) activeTab.value = state.tab
    if (state.tab === 'notes' && state.page) {
      await loadProfile(Number(state.page) || 1)
    }
    if (state.tab === 'liked' && state.page) {
      await loadLikedArticles(Number(state.page) || 1)
    }
    if (state.tab === 'collect' && state.folderId) {
      if (!favoriteFolders.value.length) await loadFavoriteFolders()
      const folder = favoriteFolders.value.find((f) => Number(f.id) === Number(state.folderId))
      if (folder) {
        activeFavoriteFolder.value = folder
        favoriteDialogTitle.value = folder.name || '收藏'
        favoriteDialogVisible.value = true
        await loadFavoriteDialogArticles(Number(state.page) || 1)
      }
    }
    await nextTick()
    if (Number.isFinite(state.scrollY)) {
      window.scrollTo({ top: Math.max(0, state.scrollY), behavior: 'auto' })
    }
  }

  async function toggleFavoriteFolderPublic(nextVal) {
    const folder = activeFavoriteFolder.value
    if (!folder?.id || !isMe.value) return
    const isPublic = nextVal ? 1 : 0
    favoriteVisibilitySaving.value = true
    try {
      const res = await updateFavoriteFolder({
        folderId: folder.id,
        isPublic,
      })
      if (res.code === 0) {
        favoriteFolderPublic.value = isPublic
        folder.isPublic = isPublic
        const idx = favoriteFolders.value.findIndex((f) => f.id === folder.id)
        if (idx >= 0) favoriteFolders.value[idx].isPublic = isPublic
        ElMessage.success(isPublic === 1 ? '已设为公开' : '已设为私密')
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } finally {
      favoriteVisibilitySaving.value = false
    }
  }

  function favoriteCoverStyle(article) {
    if (article?.coverImg) {
      return {
        backgroundImage: `url(${article.coverImg})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    }
    return { background: 'hsl(330, 70%, 94%)' }
  }

  function favoriteSnippet(article) {
    const raw = String(article?.content || '')
      .replace(/<[^>]+>/g, '')
      .replace(/\s+/g, '')
      .trim()
    return raw ? raw.slice(0, 15) : '暂无正文'
  }

  function openArticleFromFavorite(row) {
    const id = row?.article?.id
    if (!id) return
    saveProfileReturnState({
      tab: 'collect',
      folderId: activeFavoriteFolder.value?.id,
      page: favoriteDialogPageNum.value,
    })
    router.push({ path: `/article/${id}`, query: { from: 'profile' } })
  }

  function openArticleFromNotes(item) {
    const id = item?.article?.id || item?.id
    if (!id) return
    saveProfileReturnState({ tab: 'notes', page: notesPageNum.value })
    router.push({ path: `/article/${id}`, query: { from: 'profile' } })
  }

  function openArticleFromLiked(item) {
    const id = item?.article?.id || item?.id
    if (!id) return
    saveProfileReturnState({ tab: 'liked', page: likedPageNum.value })
    router.push({ path: `/article/${id}`, query: { from: 'profile' } })
  }

  function coverStyle(article) {
    if (article?.coverImg) {
      return {
        backgroundImage: `url(${article.coverImg})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    }
    const hues = [0, 200, 330, 260, 160]
    return { background: `hsl(${hues[Math.floor(Math.random() * hues.length)]}, 70%, 96%)` }
  }

  function handleChat() {
    const uid = userInfo.value?.id
    if (uid == null) return
    messageCenterUi.open({
      userId: Number(uid),
      nickname: userInfo.value?.nickname ? String(userInfo.value.nickname) : '',
      avatarUrl: userInfo.value?.avatarUrl ? String(userInfo.value.avatarUrl) : '',
    })
  }

  async function loadFollowStats(userId) {
    if (!userId) return
    try {
      const res = await getFollowStats(userId)
      if (res.code === 0 && res.data) {
        followingCount.value = Number(res.data.followingCount) || 0
        followerCount.value = Number(res.data.followerCount) || 0
        isFollowing.value = !!res.data.isFollowing
      }
    } catch {
      followingCount.value = 0
      followerCount.value = 0
      isFollowing.value = false
    }
  }

  async function toggleFollow() {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    const uid = userInfo.value?.id
    if (!uid || isMe.value) return
    followSaving.value = true
    try {
      const res = isFollowing.value
        ? await unfollowUser(uid)
        : await followUser(uid)
      if (res.code === 0) {
        isFollowing.value = !isFollowing.value
        followerCount.value += isFollowing.value ? 1 : -1
        if (followerCount.value < 0) followerCount.value = 0
        ElMessage.success(isFollowing.value ? '关注成功' : '已取消关注')
      } else {
        ElMessage.error(res.message || '操作失败')
      }
    } catch {
      ElMessage.error('操作异常')
    } finally {
      followSaving.value = false
    }
  }

  function triggerBgUpload() {
    bgFileInput.value?.click()
  }

  async function handleBgUpload(e) {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      ElMessage.warning('请选择图片文件')
      return
    }
    const pre = validateLocalImageFile(file)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      return
    }
    const formData = new FormData()
    formData.append('file', file)
    const loadingOverlay = openImageUploadLoading(file, '正在上传背景图…')
    try {
      const res = await uploadProfileBackground(formData)
      if (res.code === 0) {
        const url = res.data
        await updateBackgroundUrl(url)
        if (userInfo.value) {
          userInfo.value = { ...userInfo.value, backgroundUrl: url }
        }
        userStore.patchUserProfile({ backgroundUrl: url })
        ElMessage.success('背景图已更新')
      } else {
        ElMessage.error(res.message || '上传失败')
      }
    } catch {
      ElMessage.error('上传异常')
    } finally {
      loadingOverlay.close()
    }
    e.target.value = ''
  }

  return {
    Camera,
    Lock,
    Plus,
    Star,
    Unlock,
    activeTab,
    applyJoinPublicGroup,
    articles,
    avatarSrc,
    bgFileInput,
    bgStyle,
    confirmFavoriteFolderRename,
    coverStyle,
    favoriteDialogPageInput,
    favoriteDialogPageNum,
    favoriteDialogTotalPages,
    favoriteFolderRenaming,
    favoriteFolderRenameValue,
    goFavoriteDialogFirst,
    goFavoriteDialogNext,
    goFavoriteDialogPrev,
    goLikedFirst,
    goLikedNext,
    goLikedPrev,
    goNotesFirst,
    goNotesNext,
    goNotesPrev,
    handleBgUpload,
    handleChat,
    toggleFollow,
    isMe,
    isFollowing,
    followSaving,
    followingCount,
    followerCount,
    jumpFavoriteDialogPage,
    jumpLikedPage,
    jumpNotesPage,
    likedArticles,
    likedPageInput,
    likedPageNum,
    likedTotalPages,
    loadLikedArticles,
    favoriteCreateForm,
    favoriteCreateSaving,
    favoriteCreateVisible,
    favoriteCoverStyle,
    favoriteDialogItems,
    favoriteDialogLoading,
    favoriteDialogTitle,
    favoriteDialogVisible,
    favoriteFolderPublic,
    favoriteFolders,
    favoriteSnippet,
    favoriteVisibilitySaving,
    formatProfileDate,
    loadFavoriteFolders,
    loadPublicGroups,
    loadingFavorites,
    loading,
  notesPageInput,
    notesPageNum,
    notesTotalPages,
    openArticleFromFavorite,
    openArticleFromLiked,
    openArticleFromNotes,
    openCreateFavoriteFolder,
    openFavoriteDialog,
    publicGroups,
    publicGroupsLoading,
    publicGroupsPageInput,
    publicGroupsPageNum,
    publicGroupsTotalPages,
    profileIpRegion,
    goPublicGroupsFirst,
    goPublicGroupsNext,
    goPublicGroupsPrev,
    groupAvatarText,
    isJoinedPublicGroup,
    joiningGroupId,
    jumpPublicGroupsPage,
    saveFavoriteFolder,
    startFavoriteFolderRename,
    toggleFavoriteFolderPublic,
    loading,
    total,
    triggerBgUpload,
    userInfo,
    displayVipTier,
    displayVipExpireAt,
    showVipBadge,
  }
}
