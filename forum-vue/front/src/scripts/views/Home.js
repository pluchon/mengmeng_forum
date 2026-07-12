import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CircleCheck,
  Close,
  Search,
  Message,
  Notification,
  EditPen,
  ArrowDown,
} from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useBoardStore } from '@/stores/board'
import { useUserStore } from '@/stores/user'
import { useCheckinSnapshotStore } from '@/stores/checkinSnapshot'
import { useMessageStore } from '@/stores/message'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { getArticleList, getHotArticleList, getArticleDetail } from '@/api/article'
import {
  getRecommendationFeed,
  getRecommendationInterests,
  markRecommendationNotInterested,
  saveRecommendationInterests,
} from '@/api/recommendation'
import { getMyFollowingIds } from '@/api/userFollow'
import { getUnReadCount } from '@/api/message'
import { getSystemMessageUnreadCount } from '@/api/systemMessage'
import { useWebSocket } from '@/composables/useWebSocket'
import { shanghaiCalendarYmd } from '@/utils/datetime'
import { blockIfMuted } from '@/utils/userMute'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { useMascotUiStore } from '@/stores/mascotUi'
import { ARTICLE_STATUS } from '@/utils/articleStatus'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { ElMessage } from 'element-plus'
import aiSearchIconUrl from '@/assets/svg/AI搜索.svg?url'
import articleSearchIconUrl from '@/assets/svg/文章.svg?url'
import userSearchIconUrl from '@/assets/svg/用户.svg?url'

const CHECKIN_STRIP_DISMISS_LS = 'luntan_checkin_home_strip_dismissed'
const AI_SEARCH_LS = 'luntan_home_ai_search'
const SEARCH_TARGET_LS = 'luntan_search_target'

export function useHome() {
  const router = useRouter()
  const route = useRoute()
  const boardStore = useBoardStore()
  const userStore = useUserStore()
  const checkinSnapshotStore = useCheckinSnapshotStore()
  const messageStore = useMessageStore()
  const pointsWalletStore = usePointsWalletStore()
  const messageCenterUi = useMessageCenterUiStore()
  const mascotUi = useMascotUiStore()
  const { initWebSocket, closeWebSocket } = useWebSocket()

  const { streakDays, totalPoints, todaySigned, loaded: checkinLoaded } = storeToRefs(checkinSnapshotStore)
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
  /** 左侧「热帖榜」专用瀑布流数据（含封面等） */
  const hotFeedList = ref([])
  /** 首页右侧悬浮热帖榜数据（保留原热度排序，不再占用左侧导航） */
  const homeHotList = ref([])
  const homeHotLoading = ref(false)
  const recommendationPreferences = ref({ personalizedEnabled: true, boardIds: [] })
  const recommendationPreferenceLoaded = ref(false)
  const recommendationDialogVisible = ref(false)
  const recommendationDraftBoardIds = ref([])
  const recommendationSaving = ref(false)

  /** 0 = 首页全站流；正数 = 首页顶部导航中选中的分类。 */
  const activeCategoryId = ref(0)
  const menuActiveKey = ref('home')
  /** 非首页路由时侧栏不高亮具体入口。 */
  const sidebarMenuActive = computed(() => (route.path === '/' ? menuActiveKey.value : undefined))
  const searchQuery = ref('')
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

  const announcementRef = ref(null)

  const msgUnread = computed(
    () => (Number(messageStore.unreadCount) || 0) + (Number(messageStore.systemUnreadCount) || 0),
  )

  const isHotFeed = computed(() => menuActiveKey.value === 'hot')
  const isHomeFeed = computed(() => menuActiveKey.value === 'home')
  const isRecommendationFeed = computed(() => menuActiveKey.value === 'rec')
  const hasRecommendationInterests = computed(() => recommendationPreferences.value.boardIds.length > 0)
  const isPersonalizedRecommendation = computed(() =>
    userStore.isLoggedIn
      && recommendationPreferences.value.personalizedEnabled
      && hasRecommendationInterests.value,
  )
  const showRecommendationInterestMask = computed(() =>
    isRecommendationFeed.value
      && userStore.isLoggedIn
      && recommendationPreferenceLoaded.value
      && !hasRecommendationInterests.value,
  )

  const searchInputPlaceholder = computed(() =>
    aiSearchMode.value ? 'AI 语义搜索帖子与用户…' : '搜索帖子、用户、标签…',
  )

  /** 首页承载分类导航；推荐与热帖榜保持内容流聚焦。 */
  const showCategoryNavigator = computed(() => isHomeFeed.value)

  const effectiveVipTier = computed(() => {
    const t = Number(userStore.vipTier) || 0
    if (t <= 0) return 0
    const exp = userStore.vipExpireAt
    if (!exp) return t
    const ms = new Date(exp).getTime()
    if (Number.isNaN(ms)) return t
    return Date.now() > ms ? 0 : t
  })

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
        /* ignore */
      }
    },
  )

  watch(
    () => searchTargetMode.value,
    (v) => {
      try {
        localStorage.setItem(SEARCH_TARGET_LS, v === 'user' ? 'user' : 'article')
      } catch {
        /* ignore */
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

  const checkinSummary = computed(() => {
    if (!userStore.isLoggedIn || !checkinLoaded.value) return null
    return {
      streakDays: streakDays.value,
      totalPoints: totalPoints.value,
      todaySigned: todaySigned.value,
    }
  })

  const checkinStripDismissedToday = ref(false)

  function syncCheckinStripDismissState() {
    try {
      checkinStripDismissedToday.value = localStorage.getItem(CHECKIN_STRIP_DISMISS_LS) === shanghaiCalendarYmd()
    } catch {
      checkinStripDismissedToday.value = false
    }
  }

  const showCheckinHomeStrip = computed(() => {
    const s = checkinSummary.value
    if (!userStore.isLoggedIn || !s) return false
    if (!s.todaySigned) return true
    return !checkinStripDismissedToday.value
  })

  function dismissCheckinHomeStrip(ev) {
    ev?.stopPropagation?.()
    try {
      localStorage.setItem(CHECKIN_STRIP_DISMISS_LS, shanghaiCalendarYmd())
    } catch {
      /* ignore */
    }
    checkinStripDismissedToday.value = true
  }

  async function fetchCheckinSummary() {
    if (!userStore.isLoggedIn) {
      checkinSnapshotStore.clear()
      return
    }
    try {
      await checkinSnapshotStore.refresh()
      syncCheckinStripDismissState()
    } catch {
      checkinSnapshotStore.clear()
    }
  }

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
      /* ignore */
    }
  }

  watch([todaySigned, checkinLoaded], () => {
    syncCheckinStripDismissState()
  })

  watch(
    () => userStore.isLoggedIn,
    (val) => {
      if (val) {
        userStore.fetchUserInfo()
        initWebSocket()
        fetchUnread()
        checkinSnapshotStore.refresh()
        pointsWalletStore.refresh()
      } else {
        closeWebSocket()
        checkinSnapshotStore.clear()
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

  onUnmounted(() => {
    if (incomingUnreadTimer) clearTimeout(incomingUnreadTimer)
  })

  async function loadHotArticles(topN) {
    let followingSet = new Set()
    try {
      if (userStore.isLoggedIn) {
        try {
          const fidRes = await getMyFollowingIds()
          if (fidRes.code === 0 && Array.isArray(fidRes.data)) {
            followingSet = new Set(fidRes.data.map((id) => Number(id)))
          }
        } catch {
          followingSet = new Set()
        }
      }
      const idRes = await getHotArticleList(topN)
      if (idRes.code !== 0 || !idRes.data?.length) return []
      const ids = idRes.data
      const promises = ids.map(id => getArticleDetail(id))
      const results = await Promise.allSettled(promises)
      const items = []
      for (const r of results) {
        if (r.status !== 'fulfilled' || r.value.code !== 0 || !r.value.data) continue
        const st = Number(r.value.data.article?.status)
        if (st === ARTICLE_STATUS.PUBLISHED) {
          const row = r.value.data
          const authorId = Number(row.user?.id ?? row.article?.userId)
          row.fromFollowing = followingSet.has(authorId)
          items.push(row)
        }
      }
      return items
    } catch {
      return []
    }
  }

  async function fetchHomeHotList() {
    homeHotLoading.value = true
    try {
      homeHotList.value = await loadHotArticles(6)
    } finally {
      homeHotLoading.value = false
    }
  }

  async function fetchHotFeed() {
    loading.value = true
    hotFeedList.value = []
    feedError.value = ''
    feedForbidden.value = false
    try {
      hotFeedList.value = await loadHotArticles(30)
    } finally {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      loading.value = false
    }
  }

  const homeFeedInitialized = ref(false)

  async function ensureHomeFeedLoaded() {
    if (homeFeedInitialized.value) return
    homeFeedInitialized.value = true
    await fetchArticles(1)
    void fetchHomeHotList()
    await fetchCheckinSummary()
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
      feedError.value = error?.message || '内容加载失败，请稍后重试'
      feedForbidden.value = error?.response?.status === 403
    } finally {
      if (!preserveScroll) {
        window.scrollTo({ top: 0, behavior: 'smooth' })
      }
      loading.value = false
    }
  }

  async function selectCategoryMenu(index) {
    if (route.path !== '/') {
      await router.push('/')
      await nextTick()
    }
    if (index === 'home') {
      activeCategoryId.value = 0
      menuActiveKey.value = 'home'
      currentBoardId.value = 0
      hotFeedList.value = []
      fetchArticles(1)
      void fetchHomeHotList()
      return
    }
    if (index === 'hot') {
      menuActiveKey.value = 'hot'
      activeCategoryId.value = -1
      currentBoardId.value = 0
      fetchHotFeed()
      return
    }
    if (index === 'rec') {
      activeCategoryId.value = 0
      menuActiveKey.value = 'rec'
      currentBoardId.value = 0
      hotFeedList.value = []
      await loadRecommendationPreferences()
      fetchArticles(1)
      return
    }
  }

  async function loadRecommendationPreferences() {
    if (!userStore.isLoggedIn) {
      recommendationPreferences.value = { personalizedEnabled: false, boardIds: [] }
      recommendationPreferenceLoaded.value = true
      return
    }
    try {
      const res = await getRecommendationInterests()
      if (res.code === 0) {
        recommendationPreferences.value = {
          personalizedEnabled: res.data?.personalizedEnabled !== false,
          boardIds: Array.isArray(res.data?.boardIds) ? res.data.boardIds.map(Number) : [],
        }
      }
    } catch {
      recommendationPreferences.value = { personalizedEnabled: true, boardIds: [] }
    } finally {
      recommendationPreferenceLoaded.value = true
    }
  }

  async function openRecommendationPreferences() {
    if (!(await ensureLoggedIn('管理推荐兴趣需要登录'))) return
    if (boardStore.categoryList.length === 0) await boardStore.fetchCategoryList()
    if (!recommendationPreferenceLoaded.value) await loadRecommendationPreferences()
    recommendationDraftBoardIds.value = [...recommendationPreferences.value.boardIds]
    recommendationDialogVisible.value = true
  }

  async function saveRecommendationPreferences() {
    if (recommendationDraftBoardIds.value.length > 8) {
      ElMessage.warning('最多选择 8 个细分板块')
      return
    }
    recommendationSaving.value = true
    try {
      await saveRecommendationInterests({
        personalizedEnabled: true,
        boardIds: recommendationDraftBoardIds.value,
      })
      recommendationPreferences.value = {
        personalizedEnabled: true,
        boardIds: [...recommendationDraftBoardIds.value],
      }
      recommendationPreferenceLoaded.value = true
      recommendationDialogVisible.value = false
      ElMessage.success('推荐兴趣已更新')
      if (isRecommendationFeed.value) await fetchArticles(1)
    } finally {
      recommendationSaving.value = false
    }
  }

  async function hideRecommendedArticle(articleId) {
    if (!articleId || !isRecommendationFeed.value || recommendationSaving.value) return
    if (!(await ensureLoggedIn('调整推荐内容需要登录'))) return
    recommendationSaving.value = true
    try {
      await markRecommendationNotInterested(articleId)
      articleList.value = articleList.value.filter(item => Number(item.article?.id) !== Number(articleId))
      total.value = Math.max(0, Number(total.value) - 1)
      ElMessage.success('已减少此类推荐')
    } finally {
      recommendationSaving.value = false
    }
  }

  async function selectHomeBoard(categoryId, boardId) {
    if (route.path !== '/') {
      await router.push('/')
      await nextTick()
    }
    const normalizedCategoryId = Number(categoryId)
    const normalizedBoardId = Number(boardId)
    if (!Number.isFinite(normalizedCategoryId) || !Number.isFinite(normalizedBoardId)) return
    activeCategoryId.value = normalizedCategoryId
    menuActiveKey.value = 'home'
    currentBoardId.value = normalizedBoardId
    hotFeedList.value = []
    fetchArticles(1)
    void fetchHomeHotList()
  }

  function toggleSearchTargetMode(ev) {
    ev?.stopPropagation?.()
    ev?.preventDefault?.()
    searchTargetMode.value = searchTargetMode.value === 'article' ? 'user' : 'article'
  }

  function toggleAiSearchMode(ev) {
    ev?.stopPropagation?.()
    ev?.preventDefault?.()
    aiSearchMode.value = !aiSearchMode.value
  }

  function submitSearch() {
    const kw = searchQuery.value?.trim()
    if (!kw) return
    void (async () => {
      if (!(await ensureLoggedIn('搜索需要登录'))) return
      const query = { keyword: kw }
      if (aiSearchMode.value) query.ai = '1'
      router.push({ path: '/search', query })
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
      if (!(await ensureLoggedIn('积分中心需要登录'))) return
      router.push('/points')
    })()
  }

  function goProfile() {
    void (async () => {
      if (!(await ensureLoggedIn('个人主页需要登录'))) return
      router.push(`/profile/${userStore.id}`)
    })()
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

  function toggleMascotPassthrough() {
    void (async () => {
      if (!(await ensureLoggedIn('该功能需要登录'))) return
      mascotUi.togglePointerPassThrough()
    })()
  }

  function showAnnouncement() {
    announcementRef.value?.show()
  }

  function handleLogout() {
    userStore.logout({ remote: true })
  }

  function getRandomPastel() {
    const hues = [0, 200, 330, 260, 160]
    const hue = hues[Math.floor(Math.random() * hues.length)]
    return `hsl(${hue}, 70%, 92%)`
  }

  /** 无封面时给瀑布流一个随机高度区间，避免完全等高 */
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
    Notification,
    Search,
    activeCategoryId,
    announcementRef,
    articleList,
    boardStore,
    categoriesWithId,
    checkinSummary,
    coverImageUrl,
    currentBoardId,
    defaultAvatar,
    dismissCheckinHomeStrip,
    effectiveVipTier,
    ensureHomeFeedLoaded,
    feedError,
    feedForbidden,
    fetchArticles,
    fetchCheckinSummary,
    fetchHotFeed,
    fetchHomeHotList,
    getRandomPastel,
    goCheckin,
    goLottery,
    goPoints,
    goProfile,
    goSettings,
    goToCreative,
    handleLogout,
    hasRecommendationInterests,
    hideRecommendedArticle,
    homeHotList,
    homeHotLoading,
    hotFeedList,
    isHomeFeed,
    isHotFeed,
    isPersonalizedRecommendation,
    isRecommendationFeed,
    loading,
    mascotUi,
    menuActiveKey,
    msgUnread,
    openMessageCenter,
    openRecommendationPreferences,
    pageNum,
    pageSize,
    placeholderMinHeight,
    pointsBalance,
    recommendationDialogVisible,
    recommendationDraftBoardIds,
    recommendationPreferenceLoaded,
    recommendationSaving,
    saveRecommendationPreferences,
    searchInputPlaceholder,
    searchQuery,
    searchTargetMode,
    sidebarMenuActive,
    selectCategoryMenu,
    selectHomeBoard,
    showCategoryNavigator,
    showAnnouncement,
    showCheckinHomeStrip,
    showRecommendationInterestMask,
    submitSearch,
    toggleAiSearchMode,
    toggleMascotPassthrough,
    toggleSearchTargetMode,
    total,
    userSearchIconUrl,
    userStore,
  }
}
