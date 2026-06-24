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
import { getMyFollowingIds } from '@/api/userFollow'
import { getUnReadCount } from '@/api/message'
import { getSystemMessageUnreadCount } from '@/api/systemMessage'
import { useWebSocket } from '@/composables/useWebSocket'
import { shanghaiCalendarYmd } from '@/utils/datetime'
import { blockIfMuted } from '@/utils/userMute'
import { ARTICLE_STATUS } from '@/utils/articleStatus'
import { DEFAULT_AVATAR } from '@/utils/constants'
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
  /** 左侧「热帖榜」专用瀑布流数据（含封面等） */
  const hotFeedList = ref([])

  /** 0 = 推荐；正数 = 分类 id；热帖榜单独用 menuActiveKey === 'hot' */
  const activeCategoryId = ref(0)
  const menuActiveKey = ref('rec')
  /** 非首页路由时侧栏不高亮具体分类，避免 el-menu 误匹配 */
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

  const searchInputPlaceholder = computed(() =>
    aiSearchMode.value ? 'AI 语义搜索帖子与用户…' : '搜索帖子、用户、标签…',
  )

  /** 推荐 / 热帖榜不展示板块选择行 */
  const showBoardPillsRow = computed(
    () => menuActiveKey.value !== 'rec' && menuActiveKey.value !== 'hot',
  )

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
    if (menuActiveKey.value === 'hot') return []
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

  async function fetchHotFeed() {
    loading.value = true
    hotFeedList.value = []
    try {
      let followingSet = new Set()
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
      const idRes = await getHotArticleList(30)
      if (idRes.code !== 0 || !idRes.data?.length) return
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
      hotFeedList.value = items
    } catch (e) {
      console.warn('加载热帖榜失败:', e)
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
    await fetchCheckinSummary()
    if (userStore.isLoggedIn) {
      await pointsWalletStore.refresh()
    }
  }

  async function fetchArticles(page = 1, opts = {}) {
    const preserveScroll = opts?.preserveScroll === true
    pageNum.value = page
    loading.value = true
    try {
      const params = {
        boardId: currentBoardId.value || 0,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      }
      const res = await getArticleList(params)
      if (res.code === 0) {
        articleList.value = res.data?.records || []
        total.value = res.data?.total || 0
      }
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
      fetchArticles(1)
      return
    }
    if (!String(index).startsWith('cat_')) return
    const id = Number(String(index).slice(4))
    if (!Number.isFinite(id)) return
    activeCategoryId.value = id
    menuActiveKey.value = `cat_${id}`
    hotFeedList.value = []
    const boards =
      boardStore.categoryList.find(x => x.category?.id === id)?.boardList || []
    if (boards.length) {
      currentBoardId.value = boards[0].id
    } else {
      currentBoardId.value = 0
    }
    fetchArticles(1)
  }

  function selectBoardPill(boardId) {
    hotFeedList.value = []
    currentBoardId.value = boardId
    fetchArticles(1)
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
    const query = { keyword: kw }
    if (aiSearchMode.value) query.ai = '1'
    router.push({ path: '/search', query })
  }

  function goToCreative() {
    if (blockIfMuted(userStore)) return
    router.push('/creative')
  }

  function showAnnouncement() {
    announcementRef.value?.show()
  }

  function handleLogout() {
    userStore.logout()
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
    boardsInCategory,
    categoriesWithId,
    checkinSummary,
    coverImageUrl,
    currentBoardId,
    defaultAvatar,
    dismissCheckinHomeStrip,
    effectiveVipTier,
    ensureHomeFeedLoaded,
    fetchArticles,
    fetchCheckinSummary,
    fetchHotFeed,
    getRandomPastel,
    goToCreative,
    handleLogout,
    hotFeedList,
    isHotFeed,
    loading,
    menuActiveKey,
    msgUnread,
    pageNum,
    pageSize,
    placeholderMinHeight,
    pointsBalance,
    searchInputPlaceholder,
    searchQuery,
    searchTargetMode,
    sidebarMenuActive,
    selectBoardPill,
    selectCategoryMenu,
    showBoardPillsRow,
    showAnnouncement,
    showCheckinHomeStrip,
    submitSearch,
    toggleAiSearchMode,
    toggleSearchTargetMode,
    total,
    userSearchIconUrl,
    userStore,
  }
}
