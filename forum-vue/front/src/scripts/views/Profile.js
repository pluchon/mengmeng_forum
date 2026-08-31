import { ref, onMounted, onActivated, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Star, Camera, Plus, ZoomIn } from '@element-plus/icons-vue'
import { Bookmark, MessageCircle, ThumbsUp, Trash2 } from '@lucide/vue'
import { useUserStore } from '@/stores/user'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { getArticleListWithUser } from '@/api/article'
import { getMyLikeList, getUserLikeList } from '@/api/like'
import { cancelArticleFavorite } from '@/api/favorite'
import {
  getFavoriteFolderArticles,
  getMyFavoriteFolders,
  getUserFavoriteFolders,
  createFavoriteFolder,
  deleteFavoriteFolder,
  uploadFavoriteFolderCover,
  updateFavoriteFolder,
} from '@/api/favorite'
import { uploadProfileBackground, updateBackgroundUrl } from '@/api/settings'
import { followUser, unfollowUser, getFollowStats } from '@/api/userFollow'
import {
  getUserPublicGroupChats,
  joinPublicGroupChat,
} from '@/api/groupChat'
import { getNotInterestedArticles, restoreRecommendationInterested } from '@/api/recommendation'
import { useNotInterestedArticleStore } from '@/stores/notInterestedArticle'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'
import { clientOssUrl } from '@/utils/clientOss'
import { parseForumDateTime } from '@/utils/datetime'
import { canOpenArticleDetail, favoriteBlockedReason } from '@/utils/articleStatus'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { captureFeedOpenFrom, getFeedReturnPath } from '@/utils/feedNavigation'
import emptyFavoriteArticleUrl from '@/assets/images/shoucang_article_not.png'

const PROFILE_PAGE_SIZE = 8
const FAVORITE_FOLDER_PAGE_SIZE = 5
const FAVORITE_DIALOG_PAGE_SIZE = 5
const PUBLIC_GROUP_PAGE_SIZE = 5
const PROFILE_RETURN_KEY = 'profile-return-state'

export function useProfile() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const messageCenterUi = useMessageCenterUiStore()
  const notInterestedArticleStore = useNotInterestedArticleStore()
  const defaultAvatar = DEFAULT_AVATAR

  // 详情路由下 params.id 是帖子 ID，背景态个人主页必须改读来源用户
  function resolveProfileUserId() {
    if (route.name === 'articleDetail') {
      try {
        const raw = sessionStorage.getItem(PROFILE_RETURN_KEY)
        if (raw) {
          const state = JSON.parse(raw)
          if (state?.profileUserId != null && String(state.profileUserId).trim() !== '') {
            return state.profileUserId
          }
        }
      } catch {
        // 忽略
      }
      const returnPath = getFeedReturnPath()
      const matched = returnPath.match(/^\/profile\/(\d+)/)
      if (matched) return matched[1]
      return userStore.id
    }
    return route.params.id || userStore.id
  }

  const userInfo = ref(null)
  const articles = ref([])
  const loading = ref(true)
  const activeTab = ref('notes')
  const total = ref(0)
  const notesPageNum = ref(1)
  const notesTotal = ref(0)

  const bgFileInput = ref(null)
  const bannerDialogRef = ref(null)
  const likedArticles = ref([])
  const likedPageNum = ref(1)
  const likedTotal = ref(0)

  const favoriteFolders = ref([])
  const loadingFavorites = ref(false)
  const favoriteFolderError = ref('')
  const favoriteFolderPageNum = ref(1)
  const favoriteFolderTotal = ref(0)
  const favoriteDialogVisible = ref(false)
  const favoriteDialogLoading = ref(false)
  const favoriteDialogTitle = ref('收藏')
  const favoriteDialogItems = ref([])
  const favoriteDialogPageNum = ref(1)
  const favoriteDialogTotal = ref(0)
  const favoriteFolderRenaming = ref(false)
  const favoriteFolderRenameValue = ref('')
  const favoriteFolderRenameSaving = ref(false)
  const activeFavoriteFolder = ref(null)
  const favoriteFolderPublic = ref(1)
  const favoriteVisibilitySaving = ref(false)
  const favoriteCoverInputRef = ref(null)
  const favoriteCoverTarget = ref(null)
  const favoriteCoverUploadingId = ref(null)

  const favoriteCreateVisible = ref(false)
  const favoriteCreateSaving = ref(false)
  const favoriteCreateForm = ref({ name: '', isPublic: 1 })

  const publicGroups = ref([])
  const publicGroupsLoading = ref(false)
  const publicGroupsPageNum = ref(1)
  const publicGroupsTotal = ref(0)
  const joiningGroupId = ref(null)

  const notInterestedArticles = ref([])
  const notInterestedLoading = ref(false)
  const notInterestedPageNum = ref(1)
  const notInterestedTotal = ref(0)
  const notInterestedRestoringId = ref(null)

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
    // 笔记/点赞/不感兴趣由 selectProfileTab 主动刷新；其它 Tab 仍懒加载
    if (tab === 'collect' && favoriteFolders.value.length === 0) {
      loadFavoriteFolders(1)
    }
    if (tab === 'groups' && publicGroups.value.length === 0) {
      loadPublicGroups(1)
    }
  })

  function selectProfileTab(tab) {
    const next = String(tab || '')
    if (!next) return
    activeTab.value = next
    if (next === 'notes') {
      void loadProfile(notesPageNum.value || 1)
      return
    }
    if (next === 'liked') {
      void loadLikedArticles(likedPageNum.value || 1)
      return
    }
    if (next === 'not-interested' && isMe.value) {
      void loadNotInterestedArticles(notInterestedPageNum.value || 1)
    }
  }


  async function loadLikedArticles(page = 1) {
    const userId = resolveProfileUserId()
    likedPageNum.value = page
    try {
      const params = {
        pageNum: likedPageNum.value,
        pageSize: PROFILE_PAGE_SIZE,
      }
      const res = isMe.value
        ? await getMyLikeList(params)
        : await getUserLikeList(userId, params)
      if (res.code === 0) {
        likedArticles.value = res.data?.records || []
        likedTotal.value = Number(res.data?.total) || 0
      }
    } catch {
      ElMessage.error('加载点赞列表失败，请稍后重试')
    }
  }

  async function loadFavoriteFolders(page = 1) {
    const userId = resolveProfileUserId()
    favoriteFolderPageNum.value = page
    loadingFavorites.value = true
    favoriteFolderError.value = ''
    try {
      const res = isMe.value
        ? await getMyFavoriteFolders({ pageNum: page, pageSize: FAVORITE_FOLDER_PAGE_SIZE })
        : await getUserFavoriteFolders(userId, { pageNum: page, pageSize: FAVORITE_FOLDER_PAGE_SIZE })
      if (res.code === 0) {
        favoriteFolders.value = res.data?.records || []
        favoriteFolderTotal.value = Number(res.data?.total) || 0
      } else {
        favoriteFolders.value = []
        favoriteFolderTotal.value = 0
        favoriteFolderError.value = res.message || '收藏夹加载失败'
      }
    } catch {
      favoriteFolders.value = []
      favoriteFolderTotal.value = 0
      favoriteFolderError.value = '收藏夹加载失败'
    } finally {
      loadingFavorites.value = false
    }
  }


  function displayAuthorNickname(name) {
    const text = String(name || '').trim() || '匿名用户'
    const chars = Array.from(text)
    if (chars.length <= 6) return text
    return `${chars.slice(0, 6).join('')}…`
  }

  function isDefaultFavoriteFolder(folder) {
    return Number(folder?.isDefault) === 1
  }

  function favoriteFolderInitial(folder) {
    const name = String(folder?.name || '收').trim()
    return name.charAt(0) || '收'
  }

  async function triggerFavoriteCoverUpload(folder) {
    if (!isMe.value || !folder?.id) return
    // 后端已拒绝默认收藏夹改封面；这里先挡住，否则图片会先传上 OSS 再被拒，留下孤儿文件
    if (isDefaultFavoriteFolder(folder)) {
      ElMessage.warning('默认收藏夹不支持设置封面')
      return
    }
    if (!(await ensureLoggedIn('上传收藏夹封面需要登录'))) return
    favoriteCoverTarget.value = folder
    favoriteCoverInputRef.value?.click()
  }

  async function handleFavoriteCoverFile(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    const folder = favoriteCoverTarget.value
    if (!file || !folder?.id) return
    const check = validateLocalImageFile(file)
    if (!check.ok) {
      ElMessage.warning(check.message)
      return
    }
    favoriteCoverUploadingId.value = folder.id
    try {
      const uploadRes = await uploadFavoriteFolderCover(file)
      if (uploadRes.code !== 0 || !uploadRes.data) {
        ElMessage.error(uploadRes.message || '封面上传失败')
        return
      }
      const updateRes = await updateFavoriteFolder({
        folderId: folder.id,
        coverUrl: uploadRes.data,
      })
      if (updateRes.code !== 0) {
        ElMessage.error(updateRes.message || '封面保存失败')
        return
      }
      folder.coverUrl = uploadRes.data
      if (Number(activeFavoriteFolder.value?.id) === Number(folder.id)) {
        activeFavoriteFolder.value.coverUrl = uploadRes.data
      }
      ElMessage.success('收藏夹封面已更新')
    } catch {
      ElMessage.error('封面上传失败，请稍后重试')
    } finally {
      favoriteCoverUploadingId.value = null
      favoriteCoverTarget.value = null
    }
  }

  function openCreateFavoriteFolder() {
    favoriteCreateForm.value = { name: '', isPublic: 1 }
    favoriteCreateVisible.value = true
  }

  function setFavoriteCreateVisibility(isPublic) {
    favoriteCreateForm.value.isPublic = Number(isPublic) === 1 ? 1 : 0
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
      ElMessage.success('已创建')
      favoriteCreateVisible.value = false
      await loadFavoriteFolders(1)
    } catch {
      // 失败原因由响应拦截器统一提示
    } finally {
      favoriteCreateSaving.value = false
    }
  }

  // 默认背景图已下线，没有背景就走纯色，不要再兜一张会 404 的图
  const bgStyle = computed(() => {
    const url = String(userInfo.value?.backgroundUrl || '').trim()
    return url ? `url(${url})` : 'none'
  })
  const hasBannerImage = computed(() => Boolean(String(userInfo.value?.backgroundUrl || '').trim()))

  const avatarSrc = computed(() => {
    if (isMe.value && userStore.avatarUrl) return userStore.avatarUrl
    return userInfo.value?.avatarUrl || defaultAvatar
  })

  const isMe = computed(() => {
    const targetId = resolveProfileUserId()
    return String(targetId) === String(userStore.id)
  })

  // 默认收藏夹不能改名，按钮直接不显示，不要让用户点了才知道
  const canRenameActiveFavoriteFolder = computed(() =>
    isMe.value
      && activeFavoriteFolder.value?.id != null
      && !isDefaultFavoriteFolder(activeFavoriteFolder.value),
  )

  const canDeleteActiveFavoriteFolder = computed(() =>
    isMe.value
      && activeFavoriteFolder.value?.id != null
      && Number(activeFavoriteFolder.value?.isDefault) !== 1,
  )

  onMounted(async () => {
    await loadProfile(1)
    await tryRestoreProfileState()
  })

  onActivated(async () => {
    await tryRestoreProfileState()
  })

  watch(
    () => [route.name, String(route.params.id || '')],
    async ([name], prev) => {
      if (name !== 'profile' && name !== 'myProfile') return
      // 从帖子详情返回：keep-alive 已保留列表，禁止重拉打乱顺序
      if (prev?.[0] === 'articleDetail') return
      // 首次进入由 onMounted 加载，避免双拉
      if (prev == null) return
      resetPublicGroups()
      await loadProfile(1)
      if (activeTab.value === 'liked') {
        await loadLikedArticles(1)
      }
      if (activeTab.value === 'groups') {
        await loadPublicGroups(1)
      }
      if (activeTab.value === 'not-interested' && isMe.value) {
        await loadNotInterestedArticles(1)
      }
      if (activeTab.value === 'collect') {
        await loadFavoriteFolders(1)
      }
      await tryRestoreProfileState()
    },
  )

  async function loadProfile(page = 1) {
    loading.value = true
    const userId = resolveProfileUserId()
    notesPageNum.value = page
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
    } catch {
      ElMessage.error('加载个人主页失败，请稍后重试')
    } finally {
      loading.value = false
    }
  }


  async function loadPublicGroups(page = 1) {
    const userId = resolveProfileUserId()
    publicGroupsPageNum.value = page
    publicGroupsLoading.value = true
    try {
      // 之前本人主页走的是"全站公开群"接口，别人主页走"该用户创建的群"，
      // 同一个 tab 两种语义，自己创建的群反而混在全站列表里找不到
      const groupsRes = await getUserPublicGroupChats(userId, {
        pageNum: page,
        pageSize: PUBLIC_GROUP_PAGE_SIZE,
      })
      publicGroups.value = groupsRes.data?.records || []
      publicGroupsTotal.value = Number(groupsRes.data?.total) || 0
    } catch {
      // 失败不能留上一页的数据配新页码
      publicGroups.value = []
      publicGroupsTotal.value = 0
    } finally {
      publicGroupsLoading.value = false
    }
  }

  async function loadNotInterestedArticles(page = 1) {
    if (!isMe.value) return
    notInterestedPageNum.value = page
    notInterestedLoading.value = true
    try {
      const res = await getNotInterestedArticles({ pageNum: page, pageSize: PROFILE_PAGE_SIZE })
      notInterestedArticles.value = res.data?.records || []
      notInterestedTotal.value = Number(res.data?.total) || 0
    } catch {
      // 失败不能留上一页数据配新页码
      notInterestedArticles.value = []
      notInterestedTotal.value = 0
    } finally {
      notInterestedLoading.value = false
    }
  }


  async function restoreNotInterestedArticle(item) {
    const articleId = item?.article?.id
    if (!articleId || notInterestedRestoringId.value != null) return
    notInterestedRestoringId.value = articleId
    try {
      await restoreRecommendationInterested(articleId)
      notInterestedArticleStore.restoreInterested(articleId)
      notInterestedArticles.value = notInterestedArticles.value.filter(row => Number(row.article?.id) !== Number(articleId))
      notInterestedTotal.value = Math.max(0, notInterestedTotal.value - 1)
      ElMessage.success('已恢复兴趣')
      if (notInterestedArticles.value.length === 0 && notInterestedPageNum.value > 1) {
        const maxPage = Math.max(1, Math.ceil((notInterestedTotal.value || 0) / PROFILE_PAGE_SIZE))
        await loadNotInterestedArticles(Math.min(notInterestedPageNum.value - 1, maxPage))
      }
    } catch {
      // 失败原因由响应拦截器统一提示
    } finally {
      notInterestedRestoringId.value = null
    }
  }

  function resetPublicGroups() {
    publicGroups.value = []
    publicGroupsTotal.value = 0
    publicGroupsPageNum.value = 1
  }


  async function applyJoinPublicGroup(group) {
    const gid = group?.id
    if (!gid) return
    if (isJoinedPublicGroup(group)) {
      openJoinedGroup(group)
      return
    }
    if (isPendingPublicGroup(group)) return
    joiningGroupId.value = gid
    try {
      const res = await joinPublicGroupChat(gid)
      ElMessage.success('申请已发送')
      if (res.data?.id) {
        group.currentUserRequestId = res.data.id
        group.currentUserRequestStatus = res.data.status
      }
      // 局部状态已更新，不必整页重拉——重拉会让服务端重算群状态、顺序也可能变
    } catch {
      // 失败原因由响应拦截器统一提示
    } finally {
      joiningGroupId.value = null
    }
  }

  function openPublicGroupCard(group) {
    if (!isJoinedPublicGroup(group)) return
    openJoinedGroup(group)
  }

  function openJoinedGroup(group) {
    const gid = group?.id
    if (!gid) return
    messageCenterUi.open({ groupId: Number(gid) })
  }

  function groupAvatarText(group) {
    const name = group?.name || '群'
    return String(name).trim().charAt(0) || '群'
  }

  // 后端 fillViewerRelations 已经算好，不用前端再拉一遍会话列表自己判断
  function isJoinedPublicGroup(group) {
    return group?.currentUserJoined === true
  }

  function isPendingPublicGroup(group) {
    return Number(group?.currentUserRequestStatus) === 0
  }

  function formatProfileDate(time) {
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
    favoriteDialogLoading.value = true
    try {
      const res = await getFavoriteFolderArticles(fid, {
        pageNum: favoriteDialogPageNum.value,
        pageSize: FAVORITE_DIALOG_PAGE_SIZE,
      })
      favoriteDialogItems.value = res.data?.records || []
      favoriteDialogTotal.value = Number(res.data?.total) || 0
    } catch {
      favoriteDialogItems.value = []
      favoriteDialogTotal.value = 0
    } finally {
      favoriteDialogLoading.value = false
    }
  }


  function startFavoriteFolderRename() {
    if (!isMe.value) return
    if (isDefaultFavoriteFolder(activeFavoriteFolder.value)) {
      ElMessage.warning('默认收藏夹不支持改名')
      return
    }
    favoriteFolderRenameValue.value = activeFavoriteFolder.value?.name || ''
    favoriteFolderRenaming.value = true
  }

  async function confirmFavoriteFolderRename() {
    const folder = activeFavoriteFolder.value
    if (!folder?.id || favoriteFolderRenameSaving.value) return
    // 不再静默截断：输入框已限长，超长交给后端拒绝，别背着用户改内容
    const name = favoriteFolderRenameValue.value.trim()
    if (!name) {
      ElMessage.warning('请输入收藏夹名称')
      return
    }
    favoriteFolderRenameSaving.value = true
    try {
      await updateFavoriteFolder({ folderId: folder.id, name })
      folder.name = name
      favoriteDialogTitle.value = name
      const idx = favoriteFolders.value.findIndex((f) => f.id === folder.id)
      if (idx >= 0) favoriteFolders.value[idx].name = name
      favoriteFolderRenaming.value = false
      ElMessage.success('已更新名称')
    } catch {
      // 失败原因由响应拦截器统一提示
    } finally {
      favoriteFolderRenameSaving.value = false
    }
  }

  function saveProfileReturnState(extra = {}) {
    const state = {
      tab: activeTab.value,
      profileUserId: resolveProfileUserId(),
      scrollY: window.scrollY,
      ...extra,
    }
    try {
      sessionStorage.setItem(PROFILE_RETURN_KEY, JSON.stringify(state))
    } catch {
      // 隐私模式或存储已满：记不住返回位置可以接受，但不能因此打断跳转
    }
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
    if (String(state.profileUserId) !== String(resolveProfileUserId())) return
    sessionStorage.removeItem(PROFILE_RETURN_KEY)
    // 只还原 Tab / 滚动 / 收藏夹弹层，不重拉笔记与点赞（避免关闭详情后顺序变化）
    const restoredTab = state.tab || 'notes'
    if (restoredTab && restoredTab !== activeTab.value) {
      activeTab.value = restoredTab
    }
    if (restoredTab === 'collect' && state.folderId) {
      if (!favoriteFolders.value.length) {
        await loadFavoriteFolders(Number(state.folderPage) || 1)
      }
      const folder = favoriteFolders.value.find((f) => Number(f.id) === Number(state.folderId))
      if (folder) {
        activeFavoriteFolder.value = folder
        favoriteDialogTitle.value = folder.name || '收藏'
        favoriteDialogVisible.value = true
        if (!favoriteDialogItems.value.length) {
          await loadFavoriteDialogArticles(Number(state.page) || 1)
        }
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
      favoriteFolderPublic.value = isPublic
      folder.isPublic = isPublic
      const idx = favoriteFolders.value.findIndex((f) => f.id === folder.id)
      if (idx >= 0) favoriteFolders.value[idx].isPublic = isPublic
      ElMessage.success(isPublic === 1 ? '已设为公开' : '已设为私密')
    } catch {
      // 失败原因由响应拦截器统一提示
    } finally {
      favoriteVisibilitySaving.value = false
    }
  }

  async function deleteCurrentFavoriteFolder() {
    const folder = activeFavoriteFolder.value
    if (!canDeleteActiveFavoriteFolder.value || favoriteDialogLoading.value) return
    try {
      await confirmDialog(
        `删除“${folder.name}”后不可恢复，收藏记录也会一并移除。`,
        '删除收藏夹',
        {
          showCancelButton: false,
          confirmButtonText: '确定删除',
          closeOnClickModal: false,
          closeOnPressEscape: true,
          customClass: 'profile-fav-delete-confirm',
          type: 'warning',
        },
      )
    } catch {
      return
    }
    favoriteDialogLoading.value = true
    try {
      const res = await deleteFavoriteFolder(folder.id)
      if (res.code !== 0) {
        ElMessage.error(res.message || '删除失败')
        return
      }
      favoriteDialogVisible.value = false
      activeFavoriteFolder.value = null
      const remaining = Math.max(0, favoriteFolderTotal.value - 1)
      const lastPage = Math.max(1, Math.ceil(remaining / FAVORITE_FOLDER_PAGE_SIZE))
      await loadFavoriteFolders(Math.min(favoriteFolderPageNum.value, lastPage))
      ElMessage.success('收藏夹已删除')
    } finally {
      favoriteDialogLoading.value = false
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

  function captureProfileOpenFrom() {
    const profilePath = route.fullPath?.startsWith('/profile')
      ? route.fullPath
      : `/profile/${resolveProfileUserId() || ''}`
    captureFeedOpenFrom(profilePath)
  }

  const favoriteRemovingId = ref(null)

  function favoriteRowBlockedReason(row) {
    return favoriteBlockedReason(row?.article?.status)
  }

  function isFavoriteRowBlocked(row) {
    return Boolean(favoriteRowBlockedReason(row))
  }

  // 从当前收藏夹移除。失效的帖子原本静默不显示，用户既看不见也删不掉，
  // 现在置灰展示 + 这个入口，才算能自己清理
  async function removeFavoriteRow(row) {
    const articleId = row?.article?.id
    if (!articleId || favoriteRemovingId.value != null) return
    try {
      await confirmDialog('确定把这条帖子从收藏夹移除吗？', '移除收藏', {
        confirmButtonText: '移除',
        closeOnClickModal: false,
      })
    } catch {
      return
    }
    favoriteRemovingId.value = articleId
    try {
      await cancelArticleFavorite(articleId)
      favoriteDialogItems.value = favoriteDialogItems.value
        .filter((item) => Number(item?.article?.id) !== Number(articleId))
      favoriteDialogTotal.value = Math.max(0, favoriteDialogTotal.value - 1)
      const folder = activeFavoriteFolder.value
      if (folder) folder.itemCount = Math.max(0, Number(folder.itemCount || 0) - 1)
      ElMessage.success('已移除')
      // 移空了就退回上一页，别停在空列表
      if (favoriteDialogItems.value.length === 0 && favoriteDialogPageNum.value > 1) {
        const maxPage = Math.max(1, Math.ceil(favoriteDialogTotal.value / FAVORITE_DIALOG_PAGE_SIZE))
        await loadFavoriteDialogArticles(Math.min(favoriteDialogPageNum.value - 1, maxPage))
      }
    } catch {
      // 失败原因由响应拦截器统一提示
    } finally {
      favoriteRemovingId.value = null
    }
  }

  function openArticleFromFavorite(row) {
    const id = row?.article?.id
    if (!id) return
    // 失效的帖子点进去也会被详情页弹回来，直接在这儿说清楚原因
    const blocked = favoriteRowBlockedReason(row)
    if (blocked) {
      ElMessage.info(`${blocked}，无法查看`)
      return
    }
    if (!canOpenArticleDetail(row?.article?.status)) return
    saveProfileReturnState({
      tab: 'collect',
      folderId: activeFavoriteFolder.value?.id,
      folderPage: favoriteFolderPageNum.value,
      page: favoriteDialogPageNum.value,
    })
    captureProfileOpenFrom()
    router.push({ path: `/article/${id}`, query: { from: 'profile' } })
  }

  function openArticleFromNotes(item) {
    const id = item?.article?.id || item?.id
    if (!id) return
    saveProfileReturnState({ tab: 'notes', page: notesPageNum.value })
    captureProfileOpenFrom()
    router.push({ path: `/article/${id}`, query: { from: 'profile' } })
  }

  function openArticleFromLiked(item) {
    const id = item?.article?.id || item?.id
    if (!id) return
    saveProfileReturnState({ tab: 'liked', page: likedPageNum.value })
    captureProfileOpenFrom()
    router.push({ path: `/article/${id}`, query: { from: 'profile' } })
  }

  function openArticleFromNotInterested(item) {
    const id = item?.article?.id
    if (!id) return
    saveProfileReturnState({ tab: 'not-interested', page: notInterestedPageNum.value })
    captureProfileOpenFrom()
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

  async function handleChat() {
    if (!(await ensureLoggedIn('私信需要登录'))) return
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
    if (!(await ensureLoggedIn('关注用户需要登录'))) return
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

  function openBannerPreview() {
    const url = String(userInfo.value?.backgroundUrl || '').trim()
    if (!url) return
    bannerDialogRef.value?.openView(url)
  }

  async function handleBgUpload(e) {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      ElMessage.warning('请选择图片文件')
      e.target.value = ''
      return
    }
    const pre = validateLocalImageFile(file)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      e.target.value = ''
      return
    }
    bannerDialogRef.value?.openCrop(file)
    e.target.value = ''
  }

  async function onBannerCropConfirm(blob) {
    if (!blob) return
    const file = new File([blob], 'profile-banner.jpg', { type: blob.type || 'image/jpeg' })
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
  }

  return {
    Bookmark,
    Camera,
    FAVORITE_DIALOG_PAGE_SIZE,
    FAVORITE_FOLDER_PAGE_SIZE,
    MessageCircle,
    PROFILE_PAGE_SIZE,
    PUBLIC_GROUP_PAGE_SIZE,
    Plus,
    selectProfileTab,
    Star,
    ThumbsUp,
    Trash2,
    ZoomIn,
    activeTab,
    applyJoinPublicGroup,
    articles,
    avatarSrc,
    bannerDialogRef,
    bgFileInput,
    bgStyle,
    canDeleteActiveFavoriteFolder,
    canRenameActiveFavoriteFolder,
    confirmFavoriteFolderRename,
    coverStyle,
    defaultAvatar,
    deleteCurrentFavoriteFolder,
    displayAuthorNickname,
    emptyFavoriteArticleUrl,
    favoriteCoverInputRef,
    favoriteCoverStyle,
    favoriteRemovingId,
    favoriteRowBlockedReason,
    isFavoriteRowBlocked,
    removeFavoriteRow,
    favoriteCoverUploadingId,
    favoriteCreateForm,
    favoriteCreateSaving,
    favoriteCreateVisible,
    favoriteDialogItems,
    favoriteDialogLoading,
    favoriteDialogPageNum,
    favoriteDialogTitle,
    favoriteDialogTotal,
    favoriteDialogVisible,
    favoriteFolderError,
    favoriteFolderInitial,
    favoriteFolderPageNum,
    favoriteFolderPublic,
    favoriteFolderRenaming,
    favoriteFolderRenameSaving,
    favoriteFolderRenameValue,
    favoriteFolderTotal,
    favoriteFolders,
    favoriteVisibilitySaving,
    followSaving,
    followerCount,
    followingCount,
    formatProfileDate,
    groupAvatarText,
    handleBgUpload,
    handleChat,
    handleFavoriteCoverFile,
    isFollowing,
    isJoinedPublicGroup,
    isMe,
    hasBannerImage,
    isPendingPublicGroup,
    joiningGroupId,
    likedArticles,
    likedPageNum,
    likedTotal,
    loadFavoriteDialogArticles,
    loadFavoriteFolders,
    loadLikedArticles,
    loadNotInterestedArticles,
    loadProfile,
    loadPublicGroups,
    loading,
    loadingFavorites,
    notesPageNum,
    notesTotal,
    notInterestedArticles,
    notInterestedLoading,
    notInterestedPageNum,
    notInterestedRestoringId,
    notInterestedTotal,
    onBannerCropConfirm,
    openArticleFromFavorite,
    openArticleFromLiked,
    openArticleFromNotInterested,
    openArticleFromNotes,
    openBannerPreview,
    openCreateFavoriteFolder,
    openFavoriteDialog,
    openPublicGroupCard,
    profileIpRegion,
    publicGroups,
    publicGroupsLoading,
    publicGroupsPageNum,
    publicGroupsTotal,
    restoreNotInterestedArticle,
    saveFavoriteFolder,
    setFavoriteCreateVisibility,
    startFavoriteFolderRename,
    toggleFavoriteFolderPublic,
    toggleFollow,
    total,
    triggerBgUpload,
    triggerFavoriteCoverUpload,
    userInfo,
  }
}
