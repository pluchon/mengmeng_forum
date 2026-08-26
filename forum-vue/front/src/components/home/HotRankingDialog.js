import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close, User, TrendCharts, TopRight, BottomRight } from '@element-plus/icons-vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { getHotArticleListWithPage } from '@/api/article'
import { extractApiErrorMessage } from '@/api/httpError'
import { captureFeedOpenFrom } from '@/utils/feedNavigation'

const HOT_RANK_PAGE_SIZE = 14

const THUMB_COLORS = [
  '#F9D9C6',
  '#D8D5F5',
  '#CDE6F6',
  '#F3D5E3',
  '#E8D8F4',
  '#D8E9E6',
  '#F4E3BE',
  '#D2DDF2',
  '#E3E7EF',
  '#F5D9CC',
]

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const route = useRoute()
const router = useRouter()
const list = ref([])
const loading = ref(false)
const error = ref('')
const pageNum = ref(1)
const total = ref(0)
let loadSeq = 0

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const showPager = computed(() => total.value > HOT_RANK_PAGE_SIZE)

watch(visible, (open) => {
  if (open) {
    pageNum.value = 1
    loadList(1)
    return
  }
  error.value = ''
})

async function loadList(page = pageNum.value) {
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const res = await getHotArticleListWithPage({ pageNum: page, pageSize: HOT_RANK_PAGE_SIZE })
    if (seq !== loadSeq) return
    list.value = Array.isArray(res?.data?.records) ? res.data.records : []
    total.value = Number(res?.data?.total) || 0
    pageNum.value = Number(res?.data?.pageNum) || page
  } catch (err) {
    if (seq !== loadSeq) return
    list.value = []
    total.value = 0
    error.value = extractApiErrorMessage(err, '热帖榜加载失败')
  } finally {
    if (seq === loadSeq) {
      loading.value = false
    }
  }
}

function onPageChange(page) {
  pageNum.value = page
  loadList(page)
}

function rowClass(rank) {
  const n = Number(rank) || 0
  if (n === 1) return 'hot-ranking-row--top1'
  if (n === 2) return 'hot-ranking-row--top2'
  if (n === 3) return 'hot-ranking-row--top3'
  return 'hot-ranking-row--rest'
}

function rankClass(rank) {
  const n = Number(rank) || 0
  if (n === 1) return 'is-rank-1'
  if (n === 2) return 'is-rank-2'
  if (n === 3) return 'is-rank-3'
  return ''
}

function thumbBg(index) {
  return THUMB_COLORS[index % THUMB_COLORS.length]
}

function formatLikeCount(count) {
  const n = Number(count)
  if (!Number.isFinite(n) || n < 0) return 0
  return Math.round(n)
}

function formatHotScore(score) {
  const n = Number(score)
  if (!Number.isFinite(n) || n < 0) return '0 热度值'
  if (n >= 1000) {
    const k = n / 1000
    const text = k >= 10 ? k.toFixed(0) : k.toFixed(1).replace(/\.0$/, '')
    return `${text}k 热度值`
  }
  return `${Math.round(n)} 热度值`
}

function trendOf(item) {
  const raw = String(item?.trendDirection || 'STABLE').toUpperCase()
  if (raw === 'UP' || raw === 'DOWN') return raw
  return 'STABLE'
}

function openArticle(item) {
  const id = item?.article?.id
  if (!id) return
  visible.value = false
  const fromPath = route.fullPath?.startsWith('/') ? route.fullPath : '/community'
  captureFeedOpenFrom(fromPath)
  const query = fromPath.startsWith('/profile') ? { from: 'profile' } : { from: 'hot' }
  router.push({ path: `/article/${id}`, query })
}
