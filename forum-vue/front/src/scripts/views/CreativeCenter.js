import { ref, onMounted, computed, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View, Star } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { getArticleListByUser, deleteArticle } from '@/api/article'
import {
  articleStatusMeta,
  ARTICLE_STATUS,
} from '@/utils/articleStatus'
import editIconUrl from '@/assets/svg/编辑.svg?url'
import deleteIconUrl from '@/assets/svg/删除.svg?url'
import { formatForumDateOnlyShanghai } from '@/utils/datetime'

const FETCH_PAGE_SIZE = 200
const LIST_PAGE_SIZE = 10

const STATUS_FILTER_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: String(ARTICLE_STATUS.PUBLISHED), label: '已发布' },
  { value: String(ARTICLE_STATUS.DRAFT), label: '草稿' },
  { value: String(ARTICLE_STATUS.PENDING_AUDIT), label: '审核中' },
  { value: String(ARTICLE_STATUS.REJECTED), label: '审核未通过' },
  { value: String(ARTICLE_STATUS.AUDIT_ERROR), label: '审核异常' },
]

function pad2(n) {
  return String(n).padStart(2, '0')
}

function toDateKey(input) {
  if (!input) return ''
  const d = new Date(input)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function isCurrentMonth(input) {
  const key = toDateKey(input)
  if (!key) return false
  const now = new Date()
  const prefix = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`
  return key.startsWith(prefix)
}

function last30DayKeys() {
  const keys = []
  const end = new Date()
  for (let i = 29; i >= 0; i--) {
    const d = new Date(end)
    d.setDate(end.getDate() - i)
    keys.push(toDateKey(d))
  }
  return keys
}

function buildTrendFromArticles(rows) {
  const keys = last30DayKeys()
  const reads = keys.map(() => 0)
  const likes = keys.map(() => 0)
  for (const row of rows) {
    const a = row.article || row
    const key = toDateKey(a.createTime)
    const idx = keys.indexOf(key)
    if (idx < 0) continue
    reads[idx] += Number(a.visitCount ?? a.readCount ?? 0) || 0
    likes[idx] += Number(a.likeCount ?? 0) || 0
  }
  return {
    dates: keys.map((k) => k.slice(5)),
    reads,
    likes,
  }
}

function buildTrendChartOption(series) {
  const { dates, reads, likes } = series
  const hasData = dates.length && (reads.some((v) => v > 0) || likes.some((v) => v > 0))
  if (!hasData) {
    return {
      title: {
        text: '暂无足够数据绘制趋势',
        left: 'center',
        top: 'center',
        textStyle: { color: '#86909c', fontSize: 13, fontWeight: 500 },
      },
      xAxis: { type: 'category', show: false },
      yAxis: { type: 'value', show: false },
      series: [],
    }
  }
  return {
    animation: true,
    animationDuration: 700,
    grid: { left: 48, right: 16, top: 36, bottom: dates.length > 14 ? 72 : 40 },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11, color: '#86909c' },
    },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: { fontSize: 10, color: '#86909c' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { opacity: 0.25 } },
      axisLabel: { fontSize: 10, color: '#86909c' },
    },
    series: [
      {
        name: '阅读',
        type: 'line',
        smooth: 0.35,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2, color: '#185fa5' },
        itemStyle: { color: '#185fa5' },
        data: reads,
      },
      {
        name: '点赞',
        type: 'line',
        smooth: 0.35,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2, color: '#d4537e' },
        itemStyle: { color: '#d4537e' },
        data: likes,
      },
    ],
  }
}

export function useCreativeCenter() {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(false)
  const articles = ref([])
  const statusFilter = ref('')
  const keyword = ref('')
  const pageNum = ref(1)
  const trendChartOption = shallowRef(null)

  const isVipMember = computed(() => {
    const t = Number(userStore.vipTier) || 0
    if (t <= 0) return false
    const exp = userStore.vipExpireAt
    if (!exp) return true
    const ms = new Date(exp).getTime()
    if (Number.isNaN(ms)) return true
    return Date.now() <= ms
  })

  const totalPosts = computed(() => articles.value.length)

  const totalLikes = computed(() =>
    articles.value.reduce((acc, cur) => acc + (Number(cur.article?.likeCount) || 0), 0),
  )

  const totalReads = computed(() =>
    articles.value.reduce(
      (acc, cur) => acc + (Number(cur.article?.visitCount ?? cur.article?.readCount) || 0),
      0,
    ),
  )

  const monthNewPosts = computed(
    () => articles.value.filter((r) => isCurrentMonth(r.article?.createTime)).length,
  )

  const monthNewLikes = computed(() =>
    articles.value
      .filter((r) => isCurrentMonth(r.article?.createTime))
      .reduce((acc, cur) => acc + (Number(cur.article?.likeCount) || 0), 0),
  )

  const monthNewReads = computed(() =>
    articles.value
      .filter((r) => isCurrentMonth(r.article?.createTime))
      .reduce(
        (acc, cur) => acc + (Number(cur.article?.visitCount ?? cur.article?.readCount) || 0),
        0,
      ),
  )

  const filteredArticles = computed(() => {
    const kw = keyword.value.trim().toLowerCase()
    const sf = statusFilter.value
    return articles.value.filter((row) => {
      const a = row.article
      if (sf !== '' && String(a.status) !== sf) return false
      if (kw) {
        const title = (a.title || '').toLowerCase()
        if (!title.includes(kw)) return false
      }
      return true
    })
  })

  const listTotal = computed(() => filteredArticles.value.length)

  const pagedArticles = computed(() => {
    const start = (pageNum.value - 1) * LIST_PAGE_SIZE
    return filteredArticles.value.slice(start, start + LIST_PAGE_SIZE)
  })

  function refreshTrendChart() {
    trendChartOption.value = buildTrendChartOption(buildTrendFromArticles(articles.value))
  }

  const fetchArticles = async () => {
    if (!userStore.id) return
    loading.value = true
    try {
      const res = await getArticleListByUser({
        userId: userStore.id,
        pageNum: 1,
        pageSize: FETCH_PAGE_SIZE,
      })
      if (res.code === 0) {
        const rawList = res.data.records || res.data.list || res.data || []
        articles.value = Array.isArray(rawList)
          ? rawList.map((item) => (item.article ? item : { article: item }))
          : []
        refreshTrendChart()
      }
    } finally {
      loading.value = false
    }
  }

  watch([statusFilter, keyword], () => {
    pageNum.value = 1
  })

  const handleDelete = (id) => {
    ElMessageBox.confirm('确定要删除这篇帖子吗？该操作不可撤销', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }).then(async () => {
      const res = await deleteArticle(id)
      if (res.code === 0) {
        ElMessage.success('删除成功')
        fetchArticles()
      }
    })
  }

  const formatDate = (dateStr) => formatForumDateOnlyShanghai(dateStr)

  function postTitle(row) {
    const a = row.article
    const meta = articleStatusMeta(a.status)
    const title = (a.title || '').trim()
    if (meta.isDraft && !title) return '— 草稿：未命名帖子 —'
    return title || '无标题'
  }

  function postTitleClass(row) {
    return articleStatusMeta(row.article.status).isDraft ? 'creative-post-title--draft' : ''
  }

  function editTargetPath(row) {
    const id = row.article?.id
    const s = Number(row.article?.status)
    if (s === ARTICLE_STATUS.PENDING_AUDIT) return `/creative`
    return `/article/edit/${id}`
  }

  function editTip(row) {
    const s = Number(row.article?.status)
    if (s === ARTICLE_STATUS.PENDING_AUDIT) return '审核中，请留意站内信'
    return '编辑'
  }

  function interactDisplay(row) {
    const a = row.article
    const meta = articleStatusMeta(a.status)
    if (meta.isDraft) return null
    return {
      reads: Number(a.visitCount ?? a.readCount ?? 0) || 0,
      likes: Number(a.likeCount ?? 0) || 0,
    }
  }

  function goCreatePost() {
    if (blockIfMuted(userStore)) return
    router.push('/article/create')
  }

  onMounted(() => {
    fetchArticles()
  })

  return {
    Plus,
    Star,
    View,
    STATUS_FILTER_OPTIONS,
    articleStatusMeta,
    articles,
    deleteIconUrl,
    editIconUrl,
    editTargetPath,
    editTip,
    fetchArticles,
    formatDate,
    handleDelete,
    interactDisplay,
    isVipMember,
    keyword,
    listTotal,
    loading,
    monthNewLikes,
    monthNewPosts,
    monthNewReads,
    pageNum,
    pagedArticles,
    postTitle,
    postTitleClass,
    statusFilter,
    totalLikes,
    totalPosts,
    totalReads,
    trendChartOption,
    goCreatePost,
  }
}
