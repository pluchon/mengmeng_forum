import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  getArticleListByUser,
  deleteArticle,
  generateCreatorInsight,
  getCreatorInsightData,
  getCreatorDashboard,
} from '@/api/article'
import { searchCreatorArticles } from '@/api/search'
import { getCreatorMonthlyNewFollowers, getFollowStats } from '@/api/userFollow'
import { blockIfMuted } from '@/utils/userMute'
import { ARTICLE_STATUS, articleStatusMeta } from '@/utils/articleStatus'
import { formatForumDateOnlyShanghai } from '@/utils/datetime'
import { captureFeedOpenFrom } from '@/utils/feedNavigation'
import editIconUrl from '@/assets/svg/编辑.svg?url'
import deleteIconUrl from '@/assets/svg/删除.svg?url'

const LIST_PAGE_SIZE = 6
const STATUS_FILTER_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: String(ARTICLE_STATUS.PUBLISHED), label: '已发布' },
  { value: String(ARTICLE_STATUS.DRAFT), label: '草稿' },
  { value: String(ARTICLE_STATUS.PENDING_AUDIT), label: '审核中' },
  { value: String(ARTICLE_STATUS.REJECTED), label: '审核未通过' },
  { value: String(ARTICLE_STATUS.AUDIT_ERROR), label: '审核异常' },
]

const INSIGHT_PERIOD_OPTIONS = [
  { value: 'WEEK', label: '近一周' },
  { value: 'HALF_MONTH', label: '近半个月' },
  { value: 'MONTH', label: '近一个月' },
  { value: 'HALF_YEAR', label: '近半年' },
]

export function useCreativeCenter(iconSet) {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(true)
  const listLoading = ref(false)
  const articles = ref([])
  const followerCount = ref(0)
  const monthNewFollowerCount = ref(0)
  const dashboard = ref(null)
  const statusFilter = ref('')
  const keyword = ref('')
  const searchMode = ref('traditional')
  const pageNum = ref(1)
  const listTotal = ref(0)
  const deletingArticleId = ref(null)
  const insightPeriod = ref('WEEK')
  const insightLoading = ref(false)
  const creatorInsights = ref({})
  const insightData = ref({})
  const insightPage = ref(0)
  const insightDataLoading = ref(false)
  let searchTimer = null

  const isVipMember = computed(() => {
    const tier = Number(userStore.vipTier) || 0
    if (tier <= 0) return false
    if (!userStore.vipExpireAt) return true
    const expireAt = new Date(userStore.vipExpireAt).getTime()
    return Number.isNaN(expireAt) || Date.now() <= expireAt
  })

  const weeklyReads = computed(() => (dashboard.value?.trendDays || [])
    .reduce((total, day) => total + (Number(day.readCount) || 0), 0))
  const dataGardenItems = computed(() => [
    { kind: 'read', label: '本周阅读', value: weeklyReads.value, monthIncrease: Number(dashboard.value?.monthNewReadCount) || 0, changeTone: 'read', icon: iconSet.View, changeIcon: iconSet.TrendCharts },
    { kind: 'like', label: '收到喜欢', value: Number(dashboard.value?.totalLikeCount) || 0, monthIncrease: Number(dashboard.value?.monthNewLikeCount) || 0, changeTone: 'like', icon: iconSet.Star, changeIcon: iconSet.TrendCharts },
    { kind: 'fan', label: '新粉丝', value: followerCount.value, monthIncrease: monthNewFollowerCount.value, changeTone: 'fan', icon: iconSet.User, changeIcon: iconSet.User },
    { kind: 'work', label: '发布作品', value: Number(dashboard.value?.totalWorkCount) || 0, monthIncrease: Number(dashboard.value?.monthNewWorkCount) || 0, changeTone: 'work', icon: iconSet.Picture, changeIcon: iconSet.Picture },
  ])
  const pagedArticles = computed(() => articles.value)
  const currentInsight = computed(() => creatorInsights.value[insightPeriod.value] || null)
  const currentTrendPoints = computed(() => insightData.value[insightPeriod.value]?.trendPoints || [])
  const insightPages = [
    { key: 'readCount', title: '阅读变化', color: '#64a4d8' },
    { key: 'likeCount', title: '喜欢变化', color: '#ef78a9' },
    { key: 'followerCount', title: '粉丝变化', color: '#75c59c' },
    { key: 'workCount', title: '作品变化', color: '#9a82dc' },
  ]
  const activeInsightPage = computed(() => insightPages[insightPage.value] || null)
  const insightChartOption = computed(() => {
    const page = activeInsightPage.value
    if (!page) return null
    const points = currentTrendPoints.value
    return {
      animationDuration: 520,
      color: [page.color],
      grid: { left: 14, right: 18, top: 18, bottom: 12, containLabel: true },
      tooltip: { trigger: 'axis', confine: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: points.map((item) => formatTrendLabel(item.label)),
        axisLine: { lineStyle: { color: '#eadfe8' } },
        axisTick: { show: false },
        axisLabel: { color: '#aa97a6', fontSize: 11, hideOverlap: true, margin: 10 },
      },
      yAxis: {
        type: 'value', min: 0, minInterval: 1,
        splitLine: { lineStyle: { color: '#f2eaf0' } },
        axisLabel: { color: '#aa97a6', fontSize: 11 },
      },
      series: [{
        name: page.title,
        type: 'line',
        smooth: false,
        clip: true,
        showSymbol: points.length <= 31,
        symbolSize: 4,
        data: points.map((item) => Number(item[page.key]) || 0),
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 },
      }],
    }
  })
  const insightPeriodLabel = computed(() => INSIGHT_PERIOD_OPTIONS
    .find((option) => option.value === insightPeriod.value)?.label || '近一周')

  async function fetchDashboard() {
    if (!userStore.id) {
      dashboard.value = null
      return
    }
    try {
      const response = await getCreatorDashboard({ weekOffset: 0 })
      if (response.code !== 0) return
      dashboard.value = response.data || null
    } catch {
      dashboard.value = null
    }
  }

  async function fetchArticles() {
    if (!userStore.id) return
    listLoading.value = true
    try {
      const searchKeyword = keyword.value.trim()
      const params = {
        pageNum: pageNum.value,
        pageSize: LIST_PAGE_SIZE,
        status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
      }
      const response = searchKeyword
        ? await searchCreatorArticles({
            ...params,
            keyword: searchKeyword,
            ai: searchMode.value === 'ai' ? 1 : undefined,
          })
        : await getArticleListByUser({ ...params, userId: userStore.id })
      if (response.code !== 0) return
      const payload = searchKeyword ? (response.data?.page || {}) : (response.data || {})
      const rawList = payload.records || payload.list || payload || []
      const apiRows = Array.isArray(rawList)
        ? rawList.map((item) => (item.article ? item : { article: item }))
        : []
      articles.value = apiRows
      listTotal.value = Number(payload.total) || apiRows.length
    } finally {
      listLoading.value = false
    }
  }

  function formatTrendLabel(value) {
    const raw = String(value || '')
    return raw.length >= 10 ? `${Number(raw.slice(5, 7))}/${Number(raw.slice(8, 10))}` : raw
  }

  async function fetchInsightData(period = insightPeriod.value) {
    insightDataLoading.value = true
    try {
      const response = await getCreatorInsightData(period)
      if (response.code !== 0 || !response.data) return
      insightData.value = { ...insightData.value, [period]: response.data }
      if (response.data.insight) {
        creatorInsights.value = { ...creatorInsights.value, [period]: response.data.insight }
      }
    } finally {
      insightDataLoading.value = false
    }
  }

  async function fetchFollowerCount() {
    if (!userStore.id) return
    try {
      const [statsResponse, monthlyResponse] = await Promise.all([
        getFollowStats(userStore.id),
        getCreatorMonthlyNewFollowers(),
      ])
      if (statsResponse.code === 0) followerCount.value = Number(statsResponse.data?.followerCount) || 0
      if (monthlyResponse.code === 0) monthNewFollowerCount.value = Number(monthlyResponse.data) || 0
    } catch {
      followerCount.value = 0
      monthNewFollowerCount.value = 0
    }
  }

  async function requestCreatorInsight() {
    if (insightLoading.value) return
    insightLoading.value = true
    try {
      const response = await generateCreatorInsight(insightPeriod.value)
      if (response.code !== 0 || !response.data) {
        throw new Error(response.message || response.msg || 'AI 小结生成失败')
      }
      creatorInsights.value = {
        ...creatorInsights.value,
        [insightPeriod.value]: response.data,
      }
      insightPage.value = 4
    } catch (error) {
      ElMessage.error(error?.message || 'AI 暂时走神了，请稍后再试')
    } finally {
      insightLoading.value = false
    }
  }

  function selectInsightPeriod(period) {
    if (!INSIGHT_PERIOD_OPTIONS.some((option) => option.value === period) || insightLoading.value) return
    insightPeriod.value = period
    insightPage.value = 0
    if (!insightData.value[period]) void fetchInsightData(period)
  }

  function selectInsightPage(index) {
    insightPage.value = Math.max(0, Math.min(4, Number(index) || 0))
  }

  function moveInsightPage(delta) {
    insightPage.value = (insightPage.value + delta + 5) % 5
  }

  function toggleSearchMode() {
    searchMode.value = searchMode.value === 'ai' ? 'traditional' : 'ai'
  }

  function postTitle(row) {
    const article = row.article
    const title = (article.title || '').trim()
    return articleStatusMeta(article.status).isDraft && !title ? '— 草稿：未命名帖子 —' : (title || '无标题')
  }

  function postTitleClass(row) {
    return articleStatusMeta(row.article.status).isDraft ? 'creative-post-title--draft' : ''
  }

  function postStatus(row) {
    if (Number(row.article?.state) === 1) return { label: '已下架', tone: 'offline' }
    const meta = articleStatusMeta(row.article?.status)
    return { label: meta.tip.replace('（待发布）', ''), tone: meta.isDraft ? 'draft' : 'published' }
  }

  function postMetrics(row) {
    if (articleStatusMeta(row.article.status).isDraft) return null
    return {
      reads: Number(row.article.visitCount ?? 0) || 0,
      likes: Number(row.article.likeCount ?? 0) || 0,
      favorites: Number(row.article.favoriteCount ?? 0) || 0,
    }
  }

  function postCoverUrl(row) {
    return String(row.article?.coverImg || '').trim()
  }

  function formatShortDate(date) {
    const value = formatForumDateOnlyShanghai(date)
    return value && value.length >= 5 ? value.slice(-5) : value
  }

  function editTargetPath(row) {
    return Number(row.article?.status) === ARTICLE_STATUS.PENDING_AUDIT ? '/creative' : `/article/edit/${row.article.id}`
  }

  function editTip(row) {
    return Number(row.article?.status) === ARTICLE_STATUS.PENDING_AUDIT ? '审核中，请留意站内信' : '编辑'
  }

  function goCreatePost() {
    if (blockIfMuted(userStore)) return
    router.push('/article/create')
  }

  function openArticle(row) {
    if (articleStatusMeta(row.article.status).isDraft) return
    const id = row.article?.id
    if (id == null) return
    // 与个人主页一致：详情叠在整理台上，关闭后回到 /creative，不经首页
    captureFeedOpenFrom('/creative')
    router.push({ path: `/article/${id}`, query: { from: 'creative' } })
  }

  function requestDelete(id) {
    deletingArticleId.value = id
  }

  function cancelDelete() {
    deletingArticleId.value = null
  }

  async function confirmDelete(id) {
    if (deletingArticleId.value !== id) return
    try {
      const response = await deleteArticle(id)
      if (response.code !== 0) return
      const removedLastItem = articles.value.length === 1 && pageNum.value > 1
      articles.value = articles.value.filter((row) => row.article.id !== id)
      listTotal.value = Math.max(0, listTotal.value - 1)
      deletingArticleId.value = null
      ElMessage.success('删除成功')
      if (removedLastItem) {
        pageNum.value -= 1
      } else {
        await fetchArticles()
      }
      await fetchDashboard()
    } finally {
      if (deletingArticleId.value === id) deletingArticleId.value = null
    }
  }

  watch([statusFilter, searchMode], () => {
    pageNum.value = 1
    void fetchArticles()
  })

  watch(keyword, () => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      pageNum.value = 1
      void fetchArticles()
    }, 320)
  })

  watch(pageNum, () => {
    void fetchArticles()
  })

  onMounted(async () => {
    try {
      await Promise.all([fetchArticles(), fetchFollowerCount(), fetchDashboard(), fetchInsightData()])
    } finally {
      loading.value = false
    }
  })

  return {
    INSIGHT_PERIOD_OPTIONS,
    STATUS_FILTER_OPTIONS,
    dataGardenItems,
    currentInsight,
    deletingArticleId,
    deleteIconUrl,
    editIconUrl,
    editTargetPath,
    editTip,
    formatShortDate,
    goCreatePost,
    insightLoading,
    insightDataLoading,
    insightPage,
    insightPages,
    activeInsightPage,
    insightChartOption,
    selectInsightPage,
    moveInsightPage,
    insightPeriod,
    insightPeriodLabel,
    cancelDelete,
    confirmDelete,
    isVipMember,
    keyword,
    searchMode,
    listTotal,
    loading,
    listLoading,
    pageNum,
    pagedArticles,
    postStatus,
    postCoverUrl,
    postMetrics,
    postTitle,
    postTitleClass,
    openArticle,
    requestCreatorInsight,
    requestDelete,
    statusFilter,
    selectInsightPeriod,
    toggleSearchMode,
  }
}
