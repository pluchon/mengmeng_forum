import { computed, defineAsyncComponent, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChatDotRound,
  Document,
  EditPen,
  Goods,
  MagicStick,
  Message,
  Picture,
  Refresh,
  Search,
  Star,
  Trophy,
  VideoCamera,
  View,
} from '@element-plus/icons-vue'
import { Rocket } from '@lucide/vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'DoorPortal' })
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import Particles from '@/components/common/Particles.vue'
import CircularGallery from '@/components/portal/CircularGallery.vue'

const DoorGuestScene = defineAsyncComponent(() => import('@/components/portal/DoorGuestScene.vue'))
import VipSubscribeDialog from '@/components/vip/VipSubscribeDialog/VipSubscribeDialog.vue'
import { getHotArticleListWithPage, getArticleListByUser, getArticleDetail } from '@/api/article'
import { getGameStatisticsRecords } from '@/api/game'
import { getShopList } from '@/api/shop'
import { useVipStatusEntry } from '@/composables/useVipStatusEntry'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import { useUserStore } from '@/stores/user'
import { ARTICLE_STATUS } from '@/utils/articleStatus'
import {
  DOOR_CHAT_SHOW_WEBP_URL as doorChatShowUrl,
  DOOR_CREATE_01_WEBP_URL as doorCreate01Url,
  DOOR_CREATE_02_WEBP_URL as doorCreate02Url,
  DOOR_CREATE_03_WEBP_URL as doorCreate03Url,
  DOOR_GAME_SHOW_WEBP_URL as doorGameShowUrl,
  ELUOSI_ALONE_WEBP_URL as tetrisCoverUrl,
  ELUOSI_PK_WEBP_URL as tetrisPkCoverUrl,
  JINZI_COVER_WEBP_URL as jinziCoverUrl,
  LOGIN_TITLE_WEBP_URL as loginTitleUrl,
  WUZIQI_COVER_WEBP_URL as gobangCoverUrl,
} from '@/utils/clientOss'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { captureFeedOpenFrom } from '@/utils/feedNavigation'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { SITE_ICP_NUMBER, SITE_ICP_URL, SITE_NAME as siteName } from '@/constants/site'

const AI_SEARCH_LS = 'luntan_home_ai_search'
const GAME_NAMES = Object.freeze({
  gobang: '五子棋',
  jinzi: '井字棋',
  tetris: '俄罗斯方块',
  tetris_pk: '俄罗斯方块对战',
})
const GAME_COVERS = Object.freeze({
  gobang: gobangCoverUrl,
  jinzi: jinziCoverUrl,
  tetris: tetrisCoverUrl,
  tetris_pk: tetrisPkCoverUrl,
})
const creatorImages = Object.freeze([
  { order: '01', url: doorCreate01Url },
  { order: '02', url: doorCreate02Url },
  { order: '03', url: doorCreate03Url },
])
const xiaomengAtlasUrl = `${(import.meta.env.BASE_URL || '/').replace(/\/+$/, '')}/mascot-assets/xiaomeng/spritesheet.webp`
const xiaomengActions = Object.freeze([
  { text: '原地待机', row: 0, frames: 6, fps: 6, tone: '#d9bddf' },
  { text: '向右小跑', row: 1, frames: 8, fps: 10, tone: '#c9d7ea' },
  { text: '向左小跑', row: 2, frames: 8, fps: 10, tone: '#c7dfdb' },
  { text: '挥手问好', row: 3, frames: 4, fps: 5, tone: '#e8bfd0' },
  { text: '开心跳跃', row: 4, frames: 5, fps: 8, tone: '#ead8ae' },
  { text: '有点沮丧', row: 5, frames: 8, fps: 6, tone: '#c9c6da' },
  { text: '等待输入', row: 6, frames: 6, fps: 6, tone: '#d8cce7' },
  { text: '忙碌执行', row: 7, frames: 6, fps: 10, tone: '#bfd9e3' },
  { text: '专注检查', row: 8, frames: 6, fps: 6, tone: '#d8c8bb' },
])

function createLoadState() {
  return reactive({ loading: false, loaded: false, error: '' })
}

const router = useRouter()
const userStore = useUserStore()
const messageCenterUi = useMessageCenterUiStore()
const defaultAvatar = DEFAULT_AVATAR

const {
  vipDialogVisible,
} = useVipStatusEntry(userStore)

const pageRef = ref(null)
const sparkCanvasRef = ref(null)
const searchInputRef = ref(null)
const brandMarkFailed = ref(false)
const brandTitleFailed = ref(false)
const searchQuery = ref('')
const aiSearchMode = ref(false)
const imageErrors = reactive({})
const scrollProgress = ref(0)
const rocketLaunching = ref(false)
const showScrollRocket = computed(() => scrollProgress.value > 0.05)
let rocketLaunchTimer = null
let scrollRafId = 0

try {
  aiSearchMode.value = localStorage.getItem(AI_SEARCH_LS) === '1'
} catch {
  aiSearchMode.value = false
}

const hotState = createLoadState()
const shopState = createLoadState()
const draftState = createLoadState()
const gameState = createLoadState()

const hotRecords = ref([])
const shopItems = ref([])
const latestDraft = ref(null)
const latestGameRecord = ref(null)

// 热帖列表已按热度排序；取各自品类第一条即为最高热度
const imageHotFeatured = computed(() => hotRecords.value.find((item) => (
  Number(item?.article?.mediaType) !== 1
)) || null)
const videoHotFeatured = computed(() => hotRecords.value.find((item) => (
  Number(item?.article?.mediaType) === 1
)) || null)
const draftTitle = computed(() => {
  const title = String(latestDraft.value?.title || '').trim()
  return title || '未命名草稿'
})
const gameTitle = computed(() => {
  const code = String(latestGameRecord.value?.gameCode || '')
  return GAME_NAMES[code] || '社区游戏'
})
const draftCoverUrl = computed(() => {
  const images = Array.isArray(latestDraft.value?.imageUrls) ? latestDraft.value.imageUrls : []
  return String(images[0] || '').trim()
})
const latestGameCoverUrl = computed(() => {
  const code = String(latestGameRecord.value?.gameCode || '')
  return GAME_COVERS[code] || ''
})

function markImageError(key) {
  imageErrors[key] = true
}

function startLoad(state) {
  state.loading = true
  state.loaded = false
  state.error = ''
}

function finishLoad(state) {
  state.loading = false
  state.loaded = true
}

function failLoad(state, message) {
  state.loading = false
  state.loaded = false
  state.error = message
}

function requireSuccess(response, fallbackMessage) {
  if (response?.code === 0) return response.data
  throw new Error(response?.message || response?.msg || fallbackMessage)
}

function friendlyLoadError(error, fallbackMessage) {
  const status = Number(error?.response?.status)
  if (status === 401 || status === 403) return '当前账号暂时无法查看这部分内容'
  if (!error?.response && error?.code === 'ERR_NETWORK') return '网络好像走神了，请稍后重试'
  if (!error?.response && error?.message) return error.message
  return fallbackMessage
}

async function loadHotArticles() {
  startLoad(hotState)
  hotRecords.value = []
  delete imageErrors['hot-image']
  delete imageErrors['hot-video']
  try {
    const responses = await Promise.all([
      getHotArticleListWithPage({ pageNum: 1, pageSize: 10 }),
      getHotArticleListWithPage({ pageNum: 2, pageSize: 10 }),
    ])
    const rows = responses.flatMap((response) => {
      const data = requireSuccess(response, '社区热帖加载失败') || {}
      return Array.isArray(data.records) ? data.records : []
    })
    const seen = new Set()
    hotRecords.value = rows.filter((item) => {
      const id = item?.article?.id
      if (!id || seen.has(String(id))) return false
      seen.add(String(id))
      return true
    })
    finishLoad(hotState)
  } catch (error) {
    failLoad(hotState, friendlyLoadError(error, '社区热帖暂时加载失败'))
  }
}

async function loadShopItems() {
  startLoad(shopState)
  shopItems.value = []
  try {
    const data = requireSuccess(
      await getShopList({ pageNum: 1, pageSize: 2, sort: 'sales' }),
      '表情商城加载失败',
    ) || {}
    const rows = data.records || data.list || []
    shopItems.value = Array.isArray(rows) ? rows.slice(0, 2) : []
    finishLoad(shopState)
  } catch (error) {
    failLoad(shopState, friendlyLoadError(error, '表情商城暂时加载失败'))
  }
}

async function loadLatestDraft() {
  startLoad(draftState)
  latestDraft.value = null
  try {
    const data = requireSuccess(await getArticleListByUser({
      userId: userStore.id,
      status: ARTICLE_STATUS.DRAFT,
      pageNum: 1,
      pageSize: 1,
    }), '草稿加载失败') || {}
    const rows = data.records || data.list || []
    const draft = Array.isArray(rows) ? (rows[0]?.article || rows[0] || null) : null
    if (!draft?.id) {
      latestDraft.value = null
      finishLoad(draftState)
      return
    }
    const detail = requireSuccess(await getArticleDetail(draft.id), '草稿图片加载失败') || {}
    latestDraft.value = {
      ...draft,
      imageUrls: Array.isArray(detail.imageUrls) ? detail.imageUrls : [],
    }
    delete imageErrors['continue-create']
    finishLoad(draftState)
  } catch (error) {
    failLoad(draftState, friendlyLoadError(error, '最近草稿暂时加载失败'))
  }
}

async function loadLatestGame() {
  startLoad(gameState)
  latestGameRecord.value = null
  try {
    const data = requireSuccess(
      await getGameStatisticsRecords({ pageNum: 1, pageSize: 1 }),
      '对局记录加载失败',
    ) || {}
    const rows = data.records || data.list || []
    latestGameRecord.value = Array.isArray(rows) ? (rows[0] || null) : null
    delete imageErrors['continue-game']
    finishLoad(gameState)
  } catch (error) {
    failLoad(gameState, friendlyLoadError(error, '最近对局暂时加载失败'))
  }
}

function resetPrivateData() {
  latestDraft.value = null
  latestGameRecord.value = null
  const privateStates = [draftState, gameState]
  privateStates.forEach((state) => {
    state.loading = false
    state.loaded = false
    state.error = ''
  })
}

function loadPublicData() {
  void Promise.allSettled([loadHotArticles(), loadShopItems()])
}

function loadPrivateData() {
  if (!userStore.isLoggedIn || !userStore.id) return
  void Promise.allSettled([
    loadLatestDraft(),
    loadLatestGame(),
  ])
}

async function goProtected(path, message) {
  if (!(await ensureLoggedIn(message))) return
  await router.push(path)
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function updateScrollProgress() {
  scrollRafId = 0
  const doc = document.documentElement
  const max = Math.max(0, doc.scrollHeight - window.innerHeight)
  scrollProgress.value = max > 0
    ? Math.min(1, Math.max(0, window.scrollY / max))
    : 0
  if (rocketLaunching.value && window.scrollY <= 2) {
    rocketLaunching.value = false
  }
}

function onWindowScroll() {
  if (scrollRafId) return
  scrollRafId = window.requestAnimationFrame(updateScrollProgress)
}

function scrollToTopWithRocket() {
  if (window.scrollY <= 2) return
  rocketLaunching.value = true
  if (rocketLaunchTimer) window.clearTimeout(rocketLaunchTimer)
  window.scrollTo({ top: 0, behavior: 'smooth' })
  rocketLaunchTimer = window.setTimeout(() => {
    rocketLaunching.value = false
    rocketLaunchTimer = null
  }, 1200)
}

const SPARK_DURATION = 460
const SPARK_COLORS = ['#ffffff', '#ece0f1', '#d7e4fb', '#f4dbc8']
let clickSparks = []
let sparkAnimationId = null
let sparkResizeObserver = null

// 鼠标点击位置向外扩散的细线火花，画布不参与事件命中
function createClickSparks(event) {
  const canvas = sparkCanvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const now = performance.now()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  clickSparks.push(...Array.from({ length: 10 }, (_, index) => ({
    x,
    y,
    angle: (Math.PI * 2 * index) / 10 + (Math.random() - 0.5) * 0.22,
    color: SPARK_COLORS[index % SPARK_COLORS.length],
    startTime: now,
  })))
  if (sparkAnimationId === null) sparkAnimationId = window.requestAnimationFrame(drawClickSparks)
}

function resizeSparkCanvas() {
  const canvas = sparkCanvasRef.value
  const page = pageRef.value
  if (!canvas || !page) return
  const pixelRatio = window.devicePixelRatio || 1
  const width = Math.ceil(page.clientWidth)
  const height = Math.ceil(page.scrollHeight)
  canvas.width = Math.max(1, Math.ceil(width * pixelRatio))
  canvas.height = Math.max(1, Math.ceil(height * pixelRatio))
  const context = canvas.getContext('2d')
  context?.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
}

function drawClickSparks(timestamp) {
  const canvas = sparkCanvasRef.value
  const context = canvas?.getContext('2d')
  if (!canvas || !context) {
    sparkAnimationId = null
    return
  }
  const pixelRatio = window.devicePixelRatio || 1
  context.clearRect(0, 0, canvas.width / pixelRatio, canvas.height / pixelRatio)
  clickSparks = clickSparks.filter((spark) => {
    const progress = (timestamp - spark.startTime) / SPARK_DURATION
    if (progress >= 1) return false
    const eased = 1 - ((1 - progress) ** 3)
    const distance = eased * 30
    const length = 12 * (1 - eased)
    const startX = spark.x + distance * Math.cos(spark.angle)
    const startY = spark.y + distance * Math.sin(spark.angle)
    const endX = spark.x + (distance + length) * Math.cos(spark.angle)
    const endY = spark.y + (distance + length) * Math.sin(spark.angle)
    context.globalAlpha = 1 - eased
    context.strokeStyle = spark.color
    context.lineWidth = 2
    context.beginPath()
    context.moveTo(startX, startY)
    context.lineTo(endX, endY)
    context.stroke()
    return true
  })
  context.globalAlpha = 1
  sparkAnimationId = clickSparks.length > 0
    ? window.requestAnimationFrame(drawClickSparks)
    : null
}

function toggleAiSearch() {
  void (async () => {
    if (!aiSearchMode.value && !(await ensureLoggedIn('AI 搜索需要登录'))) return
    aiSearchMode.value = !aiSearchMode.value
    await nextTick()
    searchInputRef.value?.focus?.()
  })()
}

function submitSearch() {
  const keyword = searchQuery.value.trim()
  if (!keyword) {
    ElMessage.info('输入想搜索的内容吧')
    searchInputRef.value?.focus?.()
    return
  }
  void (async () => {
    if (aiSearchMode.value && !(await ensureLoggedIn('AI 搜索需要登录'))) return
    const query = { keyword }
    if (aiSearchMode.value) query.ai = '1'
    await router.push({ path: '/search', query })
  })()
}

function focusAiSearch() {
  void (async () => {
    if (!(await ensureLoggedIn('AI 搜索需要登录'))) return
    aiSearchMode.value = true
    scrollToTop()
    await nextTick()
    searchInputRef.value?.focus?.()
  })()
}

function openArticle(item) {
  const id = item?.article?.id
  if (!id) return
  captureFeedOpenFrom('/')
  router.push(`/article/${id}`)
}

function openMessageCenter() {
  void (async () => {
    if (!(await ensureLoggedIn('查看私信需要登录'))) return
    messageCenterUi.open()
  })()
}

function handleLogout() {
  userStore.logout({ remote: true })
}

function continueCreating() {
  if (latestDraft.value?.id) {
    router.push(`/article/edit/${latestDraft.value.id}`)
    return
  }
  router.push('/creative')
}

function formatCompactNumber(value) {
  const number = Math.max(0, Number(value) || 0)
  if (number >= 10000) return `${(number / 10000).toFixed(number >= 100000 ? 0 : 1)}万`
  if (number >= 1000) return `${(number / 1000).toFixed(1)}k`
  if (!Number.isInteger(number)) return number.toFixed(1)
  return String(number)
}

function articleCover(item) {
  return String(item?.article?.coverImg || '').trim()
}

function articleTitle(item, fallback = '社区里的新鲜话题') {
  return String(item?.article?.title || '').trim() || fallback
}

function articleAuthor(item) {
  return String(item?.user?.nickname || '').trim() || '社区成员'
}

function articleAuthorAvatar(item) {
  return String(item?.user?.avatarUrl || '').trim() || defaultAvatar
}

function onFeaturedAvatarError(event) {
  const img = event?.target
  if (!img || img.dataset.fallbackApplied === '1') return
  img.dataset.fallbackApplied = '1'
  img.src = defaultAvatar
}

function articleExcerpt(item) {
  const raw = String(item?.article?.content || '')
  const plain = raw
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/\s+/g, ' ')
    .trim()
  if (!plain) return '点开看看这条社区热选吧'
  if (plain.length <= 72) return plain
  return `${plain.slice(0, 72).trim()}…`
}

let revealObserver = null

function observeRevealNodes() {
  const nodes = pageRef.value?.querySelectorAll?.('.door-reveal:not(.is-visible)') || []
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reduceMotion || typeof IntersectionObserver === 'undefined') {
    nodes.forEach((node) => node.classList.add('is-visible'))
    return
  }
  if (!revealObserver) {
    revealObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        entry.target.classList.add('is-visible')
        revealObserver?.unobserve(entry.target)
      })
    }, {
      threshold: 0.08,
      rootMargin: '0px 0px -6% 0px',
    })
  }
  nodes.forEach((node) => revealObserver.observe(node))
}

function initRevealObserver() {
  observeRevealNodes()
}

watch(
  () => aiSearchMode.value,
  (value) => {
    try {
      localStorage.setItem(AI_SEARCH_LS, value ? '1' : '0')
    } catch {
      // 浏览器禁止本地存储时，搜索模式仍在当前页面有效
    }
  },
)

watch(
  () => [userStore.isLoggedIn, userStore.id],
  ([loggedIn, userId]) => {
    if (loggedIn && userId) {
      loadPrivateData()
    } else {
      resetPrivateData()
    }
    nextTick(() => observeRevealNodes())
  },
  { immediate: true },
)

onMounted(async () => {
  loadPublicData()
  await nextTick()
  initRevealObserver()
  updateScrollProgress()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  window.addEventListener('resize', onWindowScroll, { passive: true })
  resizeSparkCanvas()
  if (typeof ResizeObserver !== 'undefined' && pageRef.value) {
    sparkResizeObserver = new ResizeObserver(resizeSparkCanvas)
    sparkResizeObserver.observe(pageRef.value)
  }
})

onUnmounted(() => {
  if (rocketLaunchTimer) window.clearTimeout(rocketLaunchTimer)
  if (scrollRafId) window.cancelAnimationFrame(scrollRafId)
  window.removeEventListener('scroll', onWindowScroll)
  window.removeEventListener('resize', onWindowScroll)
  sparkResizeObserver?.disconnect()
  if (sparkAnimationId !== null) window.cancelAnimationFrame(sparkAnimationId)
  revealObserver?.disconnect()
  revealObserver = null
})
