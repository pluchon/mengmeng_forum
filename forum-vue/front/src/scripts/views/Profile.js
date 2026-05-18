import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Star, Camera } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { getArticleListWithUser } from '@/api/article'
import { getMyLikeList } from '@/api/like'
import { getMyFavoriteFolders, getUserFavoriteFolders } from '@/api/favorite'
import { uploadProfileBackground, updateBackgroundUrl } from '@/api/settings'
import { ElMessage } from 'element-plus'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'
import defaultBg from '@/assets/images/profileb_back.jpg'

export function useProfile() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const messageCenterUi = useMessageCenterUiStore()
  const defaultAvatar = DEFAULT_AVATAR

  const userInfo = ref(null)
  const articles = ref([])
  const loading = ref(true)
  const activeTab = ref('notes')
  const total = ref(0)
  const bgFileInput = ref(null)
  const likedArticles = ref([])
  const favoriteFolders = ref([])
  const loadingFavorites = ref(false)

  watch(activeTab, (tab) => {
    if (tab === 'liked' && likedArticles.value.length === 0) {
      loadLikedArticles()
    }
    if (tab === 'collect' && favoriteFolders.value.length === 0) {
      loadFavoriteFolders()
    }
  })

  async function loadLikedArticles() {
    try {
      const res = await getMyLikeList({ pageNum: 1, pageSize: 50 })
      if (res.code === 0) {
        likedArticles.value = res.data?.records || res.data || []
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

  const bgStyle = computed(() => {
    return `url(${userInfo.value?.backgroundUrl || defaultBg})`
  })

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

  /** 与首页侧栏一致：有效会员才展示皇冠 */
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
    loadProfile()
  })

  async function loadProfile() {
    loading.value = true
    const userId = route.params.id || userStore.id
    try {
      const res = await getArticleListWithUser({ userId, pageNum: 1, pageSize: 50 })
      if (res.code === 0) {
        userInfo.value = res.data.user
        articles.value = res.data.records || []
        total.value = res.data.total || 0
      }
    } catch (e) {
      console.error('加载个人主页失败:', e)
    } finally {
      loading.value = false
    }
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
    const q = {
      targetUserId: String(uid),
      nickname: userInfo.value?.nickname ? String(userInfo.value.nickname) : '',
      avatarUrl: userInfo.value?.avatarUrl ? String(userInfo.value.avatarUrl) : '',
    }
    messageCenterUi.open({
      userId: Number(uid),
      nickname: q.nickname,
      avatarUrl: q.avatarUrl,
    })
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
    const loading = openImageUploadLoading(file, '正在上传背景图…')
    try {
      const res = await uploadProfileBackground(formData)
      if (res.code === 0) {
        const url = res.data
        await updateBackgroundUrl(url)
        if (userInfo.value) {
          userInfo.value = { ...userInfo.value, backgroundUrl: url }
        }
        userStore.backgroundUrl = url
        ElMessage.success('背景图已更新')
      } else {
        ElMessage.error(res.message || '上传失败')
      }
    } catch {
      ElMessage.error('上传异常')
    } finally {
      loading.close()
    }
    e.target.value = ''
  }

  return {
    Camera,
    Star,
    activeTab,
    articles,
    avatarSrc,
    bgFileInput,
    bgStyle,
    coverStyle,
    handleBgUpload,
    handleChat,
    isMe,
    likedArticles,
    favoriteFolders,
    loadFavoriteFolders,
    loadingFavorites,
    loading,
    total,
    triggerBgUpload,
    userInfo,
    displayVipTier,
    displayVipExpireAt,
    showVipBadge,
  }
}
