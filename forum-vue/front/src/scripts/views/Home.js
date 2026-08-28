import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CircleCheck,
  Close,
  Search,
  Message,
  EditPen,
  ArrowDown,
} from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useBoardStore } from '@/stores/board'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { getArticleList } from '@/api/article'
import { getRecommendationFeed } from '@/api/recommendation'
import { onRecommendationSettingChanged } from '@/utils/recommendationSettingEvent'
import { getUnReadCount } from '@/api/message'
import { getSystemMessageUnreadCount } from '@/api/systemMessage'
import { useWebSocket } from '@/composables/useWebSocket'
import { blockIfMuted } from '@/utils/userMute'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { ElMessage } from 'element-plus'
import aiSearchIconUrl from '@/assets/svg/AI搜索.svg?url'
import articleSearchIconUrl from '@/assets/svg/文章.svg?url'
import userSearchIconUrl from '@/assets/svg/用户.svg?url'

const AI_SEARCH_LS = 'luntan_home_ai_search'
const SEARCH_TARGET_LS = 'luntan_search_target'

export function useHome() {
  const router = useRouter()
  const route = useRoute()
  const boardStore = useBoardStore()
  const userStore = useUserStore()
  const messageStore = useMessageStore()
  const pointsWalletStore = usePointsWalletStore()
  const messageCenterUi = useMessageCenterUiStore()
  const { initWebSocket, closeWebSocket } = useWebSocket()

  const { balance: pointsBalance } = storeToRefs(pointsWalletStore)
  const defaultAvatar = DEFAULT_AVATAR

  const loading = ref(false)
  const articleList = ref([])
  const currentBoardId = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const feedError = ref('')
  const feedForbidden = ref(false)
  // server | network | forbidden | generic
  const feedErrorKind = ref('')

  // 0 首页全站流；正数 首页顶部导航中选中的分类
  const activeCategoryId = ref(0)
  const menuActiveKey = ref('home')
  // 侧栏高亮：按当前路由映射入口；社区流再区分首页 / 推荐
  const sidebarMenuActive = computed(() => {
    const path = route.path || ''
    if (path === '/community' || path.startsWith('/board/')) {
      return menuActiveKey.value === 'rec' ? 'rec' : 'home'
    }
    if (path === '/profile' || path.startsWith('/profile/')) return 'profile'
    if (path === '/game' || path.startsWith('/game/') || path === '/games' || path.startsWith('/games/')) return 'game'
    if (path === '/music-hall' || path.startsWith('/music-hall/')) return 'music'
    if (path === '/creative' || path.startsWith('/creative/')) return 'creative'
    if (path === '/checkin' || path.startsWith('/checkin/')) return 'checkin'
    if (path === '/emoji-shop' || path.startsWith('/emoji-shop/')) return 'emoji'
    if (path === '/lottery' || path.startsWith('/lottery/')) return 'lottery'
    if (path === '/article/create' || path.startsWith('/article/edit/')) return 'creative'
    return ''
  })
  const searchQuery = ref('')
  const searchSubmitVersion = ref(0)
  const aiSearchMode = ref(false)
  try {
    aiSearchMode.value = typeof localStorage !== 'undefined' && localStorage.getItem(AI_SEARCH_LS) === '1'
  } catch {
    aiSearchMode.value = false
  }

  const searchTargetMode = ref('article')
  try {
    const t = typeof localStorage !== 'undefined' ? localStorage.getItem(SEARCH_TARGET_LS) : null
    if (t === 'user' || t === 'article') searchTargetMode.value = t
  } catch {
    searchTargetMode.value = 'article'
  }

  const msgUnread = computed(
    () => (Number(messageStore.unreadCount) || 0) + (Number(messageStore.systemUnreadCount) || 0),
  )

  const isHomeFeed = computed(() => menuActiveKey.value === 'home')
  const isRecommendationFeed = computed(() => menuActiveKey.value === 'rec')

  const searchInputPlaceholder = computed(() => '搜你所想......')

  // 首页承载分类导航；推荐与热帖榜保持内容流聚焦
  const showCategoryNavigator = computed(() => isHomeFeed.value)

  const boardsInCategory = computed(() => {
    if (!isHomeFeed.value) return []
    if (activeCategoryId.value === 0) return []
    const cat = boardStore.categoryList.find(
      x => x.category?.id === activeCategoryId.value,
    )
    return cat?.boardList || []
  })

  const categoriesWithId = computed(() =>
    boardStore.categoryList.filter(x => x.category?.id),
  )

  watch(
    () => aiSearchMode.value,
    (v) => {
      try {
        localStorage.setItem(AI_SEARCH_LS, v ? '1' : '0')
      } catch {
        // 忽略
      }
    },
  )

  watch(
    () => searchTargetMode.value,
    (v) => {
      try {
        localStorage.setItem(SEARCH_TARGET_LS, v === 'user' ? 'user' : 'article')
      } catch {
        // 忽略
      }
    },
  )

  watch(
    () => ({
      path: route.path,
      ai: route.query?.ai,
      kw: route.query?.keyword,
    }),
    ({ path, ai, kw }) => {
      if (path === '/search/user') {
        searchTargetMode.value = 'user'
      } else if (path === '/search') {
        searchTargetMode.value = 'article'
      }
      if ((path === '/search' || path === '/search/user') && (ai === '1' || ai === 'true')) {
        aiSearchMode.value = true
      }
      const k = (kw ?? '').toString()
      if ((path === '/search' || path === '/search/user') && k) {
        searchQuery.value = k
      }
    },
    { immediate: true },
  )

  async function fetchUnread() {
    if (!userStore.isLoggedIn) return
    try {
      const [msgRes, sysRes] = await Promise.all([getUnReadCount(), getSystemMessageUnreadCount()])
      const api = msgRes?.code === 0 ? Number(msgRes.data) || 0 : 0
      messageStore.setUnreadCount(api, { keepTip: messageStore.showTip })
      if (sysRes?.code === 0) {
        messageStore.setSystemUnreadCount(Number(sysRes.data) || 0)
      }
    } catch {
      // 忽略
    }
  }

  watch(
    () => userStore.isLoggedIn,
    (val) => {
      if (val) {
        userStore.fetchUserInfo()
        initWebSocket()
        fetchUnread()
        pointsWalletStore.refresh()
      } else {
        closeWebSocket()
      }
    },
    { immediate: true },
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

  // 切回页面时刷新积分：手动改库 / 他端消费后顶栏能跟上 权威表是 points_wallet
  function onForumPointsVisibilityRefresh() {
    if (document.visibilityState !== 'visible') return
    if (!userStore.isLoggedIn) return
    pointsWalletStore.refresh()
  }

  let stopRecommendationSettingListen = null

  onMounted(() => {
    document.addEventListener('visibilitychange', onForumPointsVisibilityRefresh)
    stopRecommendationSettingListen = onRecommendationSettingChanged(() => {
      if (!isRecommendationFeed.value) return
      fetchArticles(1)
    })
  })

  onUnmounted(() => {
    if (incomingUnreadTimer) clearTimeout(incomingUnreadTimer)
    document.removeEventListener('visibilitychange', onForumPointsVisibilityRefresh)
    if (typeof stopRecommendationSettingListen === 'function') {
      stopRecommendationSettingListen()
      stopRecommendationSettingListen = null
    }
  })

  const homeFeedInitialized = ref(false)

  async function ensureHomeFeedLoaded() {
    if (homeFeedInitialized.value) return
    homeFeedInitialized.value = true
    await fetchArticles(1)
    if (userStore.isLoggedIn) {
      await pointsWalletStore.refresh()
    }
  }

  async function fetchArticles(page = 1, opts = {}) {
    const preserveScroll = opts?.preserveScroll === true
    pageNum.value = page
    loading.value = true
    feedError.value = ''
    feedForbidden.value = false
    feedErrorKind.value = ''
    // 换页 / 重载时先清空列表，只显示右侧整区灰色动态骨架，避免旧帖闪现
    articleList.value = []
    total.value = 0
    try {
      const params = { pageNum: pageNum.value, pageSize: pageSize.value }
      const res = isRecommendationFeed.value
        ? await getRecommendationFeed(params)
        : await getArticleList({ ...params, boardId: currentBoardId.value || 0 })
      if (res.code === 0) {
        articleList.value = res.data?.records || []
        total.value = res.data?.total || 0
      }
    } catch (error) {
      articleList.value = []
      total.value = 0
      const status = Number(error?.response?.status)
      const kind = status === 403
        ? 'forbidden'
        : status >= 500
          ? 'server'
          : !error?.response
            ? 'network'
            : 'generic'
      feedErrorKind.value = kind
      feedForbidden.value = kind === 'forbidden'
      // 品牌化兜底只展示固定文案，不把技术错误细节铺在界面上
      if (kind === 'forbidden') {
        feedError.value = '暂时无法访问这部分内容'
      } else if (kind === 'server') {
        feedError.value = '服务器开小差了，请重试'
      } else if (kind === 'network') {
        feedError.value = '网络好像走神了，请重试'
      } else {
        feedError.value = error?.message || '内容加载失败，请稍后重试'
      }
    } finally {
      if (!preserveScroll) {
        window.scrollTo({ top: 0, behavior: 'smooth' })
      }
      loading.value = false
    }
  }

  async function selectCategoryMenu(index) {
    const nextMenuKey = index === 'rec' ? 'rec' : 'home'
    const isCurrentFeed = nextMenuKey === 'rec'
      ? menuActiveKey.value === 'rec'
      : menuActiveKey.value === 'home'
        && Number(activeCategoryId.value) === 0
        && Number(currentBoardId.value) === 0
    if (route.path !== '/community') {
      await router.push('/community')
      await nextTick()
    }
    // 从其他页面回到已停留的信息流时，KeepAlive 中的帖子、页码和滚动位置就是用户离开前的状态
    // 仅在切换 首页 / 为你推荐 时才重新请求另一套信息流
    if (isCurrentFeed) return
    if (index === 'home') {
      activeCategoryId.value = 0
      menuActiveKey.value = 'home'
      currentBoardId.value = 0
      fetchArticles(1)
      return
    }
    if (index === 'rec') {
      if (!(await ensureLoggedIn('为你推荐需要登录'))) return
      activeCategoryId.value = 0
      menuActiveKey.value = 'rec'
      currentBoardId.value = 0
      fetchArticles(1)
      return
    }
  }

  async function selectHomeBoard(categoryId, boardId) {
    if (route.path !== '/community') {
      await router.push('/community')
      await nextTick()
    }
    const normalizedCategoryId = Number(categoryId)
    const normalizedBoardId = Number(boardId)
    if (!Number.isFinite(normalizedCategoryId) || !Number.isFinite(normalizedBoardId)) return
    const isCurrentBoard = menuActiveKey.value === 'home'
      && Number(activeCategoryId.value) === normalizedCategoryId
      && Number(currentBoardId.value) === normalizedBoardId
    if (isCurrentBoard) return
    activeCategoryId.value = normalizedCategoryId
    menuActiveKey.value = 'home'
    currentBoardId.value = normalizedBoardId
    fetchArticles(1)
  }

  function toggleSearchTargetMode(ev) {
    ev?.stopPropagation?.()
    ev?.preventDefault?.()
    searchTargetMode.value = searchTargetMode.value === 'article' ? 'user' : 'article'
  }

  async function toggleAiSearchMode(ev) {
    ev?.stopPropagation?.()
    ev?.preventDefault?.()
    if (!aiSearchMode.value && !(await ensureLoggedIn('AI 搜索需要登录'))) return
    aiSearchMode.value = !aiSearchMode.value
  }

  function submitSearch() {
    const kw = searchQuery.value?.trim()
    if (!kw) return
    void (async () => {
      if (aiSearchMode.value && !(await ensureLoggedIn('AI 搜索需要登录'))) return
      const query = { keyword: kw }
      if (aiSearchMode.value) query.ai = '1'
      await router.push({ path: '/search', query })
      searchSubmitVersion.value += 1
    })()
  }

  function goToCreative() {
    void (async () => {
      if (!(await ensureLoggedIn('创作中心需要登录'))) return
      if (blockIfMuted(userStore)) return
      router.push('/creative')
    })()
  }

  function openMessageCenter() {
    void (async () => {
      if (!(await ensureLoggedIn('查看消息需要登录'))) return
      messageCenterUi.open()
    })()
  }

  function goSettings() {
    void (async () => {
      if (!(await ensureLoggedIn('设置需要登录'))) return
      router.push('/settings')
    })()
  }

  function goPoints() {
    void (async () => {
      if (!(await ensureLoggedIn('萌币中心需要登录'))) return
      router.push('/points')
    })()
  }

  function goProfile() {
    void (async () => {
      if (!(await ensureLoggedIn('个人主页需要登录'))) return
      router.push(`/profile/${userStore.id}`)
    })()
  }

  function goMusicHall() {
    router.push('/music-hall')
  }

  function goCheckin() {
    void (async () => {
      if (!(await ensureLoggedIn('签到需要登录'))) return
      router.push('/checkin')
    })()
  }

  function goLottery() {
    void (async () => {
      if (!(await ensureLoggedIn('积分抽奖需要登录'))) return
      router.push('/lottery')
    })()
  }

  function handleLogout() {
    userStore.logout({ remote: true })
  }

  function getRandomPastel() {
    const hues = [0, 200, 330, 260, 160]
    const hue = hues[Math.floor(Math.random() * hues.length)]
    return `hsl(${hue}, 70%, 92%)`
  }

  // 无封面时给瀑布流一个随机高度区间，避免完全等高
  function placeholderMinHeight(seed) {
    const n = Number(seed) || 0
    const h = 160 + (n % 5) * 36
    return `${h}px`
  }

  function coverImageUrl(item) {
    return item.article?.coverImg || ''
  }

  return {
    aiSearchIconUrl,
    aiSearchMode,
    articleSearchIconUrl,
    ArrowDown,
    CircleCheck,
    Close,
    EditPen,
    Message,
    Search,
    activeCategoryId,
    articleList,
    boardStore,
    categoriesWithId,
    coverImageUrl,
    currentBoardId,
    defaultAvatar,
    ensureHomeFeedLoaded,
    feedError,
    feedForbidden,
    feedErrorKind,
    fetchArticles,
    getRandomPastel,
    goCheckin,
    goLottery,
    goMusicHall,
    goPoints,
    goProfile,
    goSettings,
    goToCreative,
    handleLogout,
    isHomeFeed,
    isRecommendationFeed,
    loading,
    menuActiveKey,
    msgUnread,
    openMessageCenter,
    pageNum,
    pageSize,
    placeholderMinHeight,
    pointsBalance,
    searchInputPlaceholder,
    searchQuery,
    searchSubmitVersion,
    searchTargetMode,
    sidebarMenuActive,
    selectCategoryMenu,
    selectHomeBoard,
    showCategoryNavigator,
    submitSearch,
    toggleAiSearchMode,
    toggleSearchTargetMode,
    total,
    userSearchIconUrl,
    userStore,
  }
}
