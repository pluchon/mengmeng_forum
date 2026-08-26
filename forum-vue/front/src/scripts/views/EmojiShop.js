import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  getShopList,
  getShopMyDrafts,
  getShopMyPublished,
  getShopMyPurchases,
} from '@/api/shop'
import { useUserStore } from '@/stores/user'
import { unwrapPageRecords } from '@/utils/apiData'
import uploadIconUrl from '@/assets/svg/上传.svg?url'
export { EMOJI_SHOP_CATEGORY_TABS } from '@/constants/emojiShopCategory'
import { EMOJI_SHOP_CATEGORY_TABS } from '@/constants/emojiShopCategory'

export const SORT_OPTIONS = [
  { label: '综合排序', value: 'comprehensive' },
  { label: '按照发布时间降序', value: 'published_desc' },
  { label: '按照发布时间升序', value: 'published_asc' },
  { label: '积分从低到高', value: 'price_asc', arrow: '↑' },
  { label: '积分从高到低', value: 'price_desc', arrow: '↓' },
  { label: '销量从高到低', value: 'sales_desc' },
  { label: '销量从低到高', value: 'sales_asc' },
]

export function useEmojiShop(detailDialogRef, uploadDialogRef) {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()

  const loading = ref(false)
  const records = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(8)
  const sort = ref('comprehensive')
  const category = ref('ALL')
  const keyword = ref('')
  const viewMode = ref('all')

  let searchTimer = null

  const isOwnedView = computed(() => viewMode.value === 'owned')
  const isDraftView = computed(() => viewMode.value === 'draft')
  const isPublishedView = computed(() => viewMode.value === 'published')

  const emptyDescription = computed(() => {
    if (isDraftView.value) return '还没有保存过表情包草稿哦'
    if (isPublishedView.value) return '还没有发布过表情包哦'
    if (isOwnedView.value) return '还没有购买过表情包哦'
    return '暂无表情包商品，稍后再来看看吧'
  })

  const isVipMember = computed(() => {
    const t = Number(userStore.vipTier) || 0
    if (t <= 0) return false
    const exp = userStore.vipExpireAt
    if (!exp) return true
    const ms = new Date(exp).getTime()
    if (Number.isNaN(ms)) return true
    return Date.now() <= ms
  })

  function formatPrice(price) {
    const n = Number(price)
    if (!Number.isFinite(n) || n <= 0) return '免费'
    return `${n} 积分`
  }

  function setSort(value) {
    if (sort.value === value && viewMode.value === 'all') return
    viewMode.value = 'all'
    sort.value = value
    loadList(1)
  }

  function setCategory(value) {
    if (category.value === value && viewMode.value === 'all') return
    viewMode.value = 'all'
    category.value = value
    loadList(1)
  }

  async function showMyOwned() {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('查看已购表情包需要登录'))) return
    }
    if (viewMode.value === 'owned') return
    viewMode.value = 'owned'
    await loadMyPacks(1)
  }

  async function showMyDrafts() {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('查看表情包草稿需要登录'))) return
    }
    if (viewMode.value === 'draft') return
    viewMode.value = 'draft'
    await loadMyDrafts(1)
  }

  async function showMyPublished() {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('查看已发布表情包需要登录'))) return
    }
    if (viewMode.value === 'published') return
    viewMode.value = 'published'
    await loadMyPublished(1)
  }

  function onSearchInput() {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      loadCurrentView(1)
    }, 320)
  }

  function goDetail(id) {
    detailDialogRef.value?.open(id)
    router.replace({ path: '/emoji-shop', query: { detail: String(id) } }).catch(() => {})
  }

  function onCardClick(item) {
    if (isDraftView.value) {
      goUpload(item.id)
      return
    }
    if (isPublishedView.value) {
      if (Number(item?.status) === 0) {
        ElMessage.info('审核中，通过后可编辑')
        return
      }
      goUpload(item.id, 'published')
      return
    }
    goDetail(item.id)
  }

  function onDetailClosed() {
    if (route.query.detail) {
      router.replace({ path: '/emoji-shop', query: {} }).catch(() => {})
    }
  }

  function onDetailPurchased(shopId) {
    const row = records.value.find((r) => r.id === shopId)
    if (row) row.owned = true
    if (viewMode.value === 'owned') loadMyPacks(pageNum.value)
  }

  async function goUpload(draftId = null, mode = 'draft') {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('上传表情包需要登录'))) return
    }
    const query = { ...route.query, upload: '1' }
    if (draftId && mode === 'published') query.published = String(draftId)
    else if (draftId) query.draft = String(draftId)
    router.replace({ path: '/emoji-shop', query }).catch(() => {})
  }

  function onUploadClosed() {
    if (route.query.upload) {
      const q = { ...route.query }
      delete q.upload
      delete q.draft
      delete q.published
      router.replace({ path: '/emoji-shop', query: q }).catch(() => {})
    }
  }

  function onUploadCreated() {
    loadList(1)
    onUploadClosed()
  }

  function onDraftSaved() {
    if (viewMode.value === 'draft') loadMyDrafts(pageNum.value)
  }

  function onPublishedUpdated() {
    if (viewMode.value === 'published') loadMyPublished(pageNum.value)
    else loadList(1)
    onUploadClosed()
  }

  function onPublishedDeleted() {
    loadMyPublished(1)
    onUploadClosed()
  }

  function tryOpenUploadFromRoute() {
    if (route.query.upload === '1' && userStore.isLoggedIn) {
      uploadDialogRef.value?.open(route.query.published || route.query.draft, route.query.published ? 'published' : 'draft')
    }
  }

  async function loadMyPacks(p = pageNum.value) {
    if (!userStore.isLoggedIn) {
      records.value = []
      total.value = 0
      return
    }
    pageNum.value = p
    loading.value = true
    try {
      const params = { pageNum: pageNum.value, pageSize: pageSize.value }
      const q = keyword.value.trim()
      if (q) params.keyword = q
      const res = await getShopMyPurchases(params)
      if (res.code === 0 && res.data) {
        records.value = unwrapPageRecords(res.data)
        total.value = Number(res.data.total) || records.value.length
      } else {
        records.value = []
        total.value = 0
      }
    } finally {
      loading.value = false
    }
  }

  async function loadMyPublished(p = pageNum.value) {
    if (!userStore.isLoggedIn) {
      records.value = []
      total.value = 0
      return
    }
    pageNum.value = p
    loading.value = true
    try {
      const params = { pageNum: pageNum.value, pageSize: pageSize.value }
      const q = keyword.value.trim()
      if (q) params.keyword = q
      const res = await getShopMyPublished(params)
      if (res.code === 0 && res.data) {
        records.value = unwrapPageRecords(res.data)
        total.value = Number(res.data.total) || records.value.length
      } else {
        records.value = []
        total.value = 0
      }
    } finally {
      loading.value = false
    }
  }

  async function loadMyDrafts(p = pageNum.value) {
    if (!userStore.isLoggedIn) {
      records.value = []
      total.value = 0
      return
    }
    pageNum.value = p
    loading.value = true
    try {
      const params = { pageNum: pageNum.value, pageSize: pageSize.value }
      const q = keyword.value.trim()
      if (q) params.keyword = q
      const res = await getShopMyDrafts(params)
      if (res.code === 0 && res.data) {
        records.value = unwrapPageRecords(res.data)
        total.value = Number(res.data.total) || records.value.length
      } else {
        records.value = []
        total.value = 0
      }
    } finally {
      loading.value = false
    }
  }

  function loadCurrentView(p = pageNum.value) {
    if (viewMode.value === 'owned') return loadMyPacks(p)
    if (viewMode.value === 'draft') return loadMyDrafts(p)
    if (viewMode.value === 'published') return loadMyPublished(p)
    return loadList(p)
  }

  async function loadList(p = pageNum.value) {
    viewMode.value = 'all'
    pageNum.value = p
    loading.value = true
    try {
      const params = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        sort: sort.value,
        category: category.value,
      }
      const q = keyword.value.trim()
      if (q) params.keyword = q

      const res = await getShopList(params)
      if (res.code === 0 && res.data) {
        records.value = unwrapPageRecords(res.data)
        total.value = Number(res.data.total) || records.value.length
      }
    } finally {
      loading.value = false
    }
  }

  function tryOpenDetailFromRoute() {
    const q = route.query.detail ?? route.params.id
    if (!q) return
    const id = Number(q)
    if (!Number.isFinite(id) || id <= 0) return
    detailDialogRef.value?.open(id)
  }

  onMounted(() => {
    loadList(1)
    tryOpenDetailFromRoute()
    tryOpenUploadFromRoute()
  })

  watch(
    () => route.query.detail,
    (v) => {
      if (v) tryOpenDetailFromRoute()
    },
  )

  watch(
    () => route.query.upload,
    (v) => {
      if (v === '1') tryOpenUploadFromRoute()
    },
  )

  return {
    Search,
    uploadIconUrl,
    userStore,
    loading,
    records,
    total,
    pageNum,
    pageSize,
    sort,
    category,
    keyword,
    viewMode,
    isOwnedView,
    isDraftView,
    isPublishedView,
    isVipMember,
    emptyDescription,
    SORT_OPTIONS,
    EMOJI_SHOP_CATEGORY_TABS,
    formatPrice,
    setSort,
    setCategory,
    showMyOwned,
    showMyDrafts,
    showMyPublished,
    onSearchInput,
    goDetail,
    onCardClick,
    goUpload,
    onUploadClosed,
    onUploadCreated,
    onDraftSaved,
    onPublishedUpdated,
    onPublishedDeleted,
    onDetailClosed,
    onDetailPurchased,
    loadList,
    loadCurrentView,
    loadMyPacks,
    loadMyDrafts,
    loadMyPublished,
  }
}
