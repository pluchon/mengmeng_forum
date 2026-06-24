import { ref, onMounted, computed, watch } from 'vue'
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
import { ElMessage } from 'element-plus'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'
import { clientOssUrl } from '@/utils/clientOss'

const PROFILE_PAGE_SIZE = 8

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
  const notesHasMore = ref(false)
  const notesLoadingMore = ref(false)

  const bgFileInput = ref(null)
  const likedArticles = ref([])
  const likedPageNum = ref(1)
  const likedHasMore = ref(false)
  const likedLoadingMore = ref(false)

  const favoriteFolders = ref([])
  const loadingFavorites = ref(false)
  const favoriteDialogVisible = ref(false)
  const favoriteDialogLoading = ref(false)
  const favoriteDialogTitle = ref('收藏')
  const favoriteDialogItems = ref([])
  const activeFavoriteFolder = ref(null)
  const favoriteFolderPublic = ref(1)
  const favoriteVisibilitySaving = ref(false)

  const favoriteCreateVisible = ref(false)
  const favoriteCreateSaving = ref(false)
  const favoriteCreateForm = ref({ name: '', isPublic: 1 })

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
      loadLikedArticles(true)
    }
    if (tab === 'collect' && favoriteFolders.value.length === 0) {
      loadFavoriteFolders()
    }
  })

  async function loadLikedArticles(reset = false) {
    if (!isMe.value) return
    if (reset) {
      likedPageNum.value = 1
      likedArticles.value = []
    }
    try {
      const res = await getMyLikeList({
        pageNum: likedPageNum.value,
        pageSize: PROFILE_PAGE_SIZE,
      })
      if (res.code === 0) {
        const rows = res.data?.records || []
        likedArticles.value = reset ? rows : [...likedArticles.value, ...rows]
        likedHasMore.value = !!res.data?.hasNextPage
      }
    } catch (e) {
      console.warn('加载点赞列表失败:', e)
    }
  }

  async function loadMoreLiked() {
    if (!likedHasMore.value || likedLoadingMore.value) return
    likedLoadingMore.value = true
    likedPageNum.value += 1
    try {
      await loadLikedArticles(false)
    } finally {
      likedLoadingMore.value = false
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

  onMounted(() => {
    loadProfile(true)
  })

  watch(() => route.params.id, () => {
    loadProfile(true)
  })

  async function loadProfile(resetNotes = true) {
    loading.value = true
    const userId = route.params.id || userStore.id
    if (resetNotes) {
      notesPageNum.value = 1
      articles.value = []
      likedArticles.value = []
      likedPageNum.value = 1
      favoriteFolders.value = []
    }
    try {
      const res = await getArticleListWithUser({
        userId,
        pageNum: notesPageNum.value,
        pageSize: PROFILE_PAGE_SIZE,
      })
      if (res.code === 0) {
        userInfo.value = res.data.user
        const rows = res.data.records || []
        articles.value = resetNotes ? rows : [...articles.value, ...rows]
        total.value = res.data.total || 0
        notesHasMore.value = !!res.data.hasNextPage
      }
      await loadFollowStats(userId)
    } catch (e) {
      console.error('加载个人主页失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function loadMoreNotes() {
    if (!notesHasMore.value || notesLoadingMore.value) return
    notesLoadingMore.value = true
    notesPageNum.value += 1
    try {
      await loadProfile(false)
    } finally {
      notesLoadingMore.value = false
    }
  }

  async function openFavoriteDialog(folder) {
    const fid = folder?.id
    if (!fid) return
    activeFavoriteFolder.value = folder
    favoriteFolderPublic.value = Number(folder.isPublic) === 1 ? 1 : 0
    favoriteDialogTitle.value = folder.name || '收藏'
    favoriteDialogVisible.value = true
    favoriteDialogLoading.value = true
    favoriteDialogItems.value = []
    try {
      const res = await getFavoriteFolderArticles(fid, { pageNum: 1, pageSize: 50 })
      if (res.code === 0) {
        favoriteDialogItems.value = res.data?.records || []
      }
    } finally {
      favoriteDialogLoading.value = false
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
    if (id) router.push(`/article/${id}`)
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
    articles,
    avatarSrc,
    bgFileInput,
    bgStyle,
    coverStyle,
    handleBgUpload,
    handleChat,
    toggleFollow,
    isMe,
    isFollowing,
    followSaving,
    followingCount,
    followerCount,
    likedArticles,
    likedHasMore,
    likedLoadingMore,
    loadMoreLiked,
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
    loadFavoriteFolders,
    loadMoreNotes,
    loadingFavorites,
    notesHasMore,
    notesLoadingMore,
    openArticleFromFavorite,
    openCreateFavoriteFolder,
    openFavoriteDialog,
    profileIpRegion,
    saveFavoriteFolder,
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
