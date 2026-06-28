import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { getShopList, getShopMyPacks } from '@/api/shop'
import { useUserStore } from '@/stores/user'
import { unwrapPageRecords } from '@/utils/apiData'
import uploadIconUrl from '@/assets/svg/上传.svg?url'

export const MAIN_TABS = [
  { label: '最新上架', value: 'new' },
  { label: '热门排行', value: 'hot' },
]

export const PRICE_SORTS = [
  { label: '积分从低到高', value: 'price_asc', arrow: '↑' },
  { label: '积分从高到低', value: 'price_desc', arrow: '↓' },
]

/** @deprecated 保留兼容；列表页改用 MAIN_TABS + PRICE_SORTS */
export const SORT_OPTIONS = [...MAIN_TABS, ...PRICE_SORTS]

export function useEmojiShop(detailDialogRef, uploadDialogRef) {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()

  const loading = ref(false)
  const records = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(12)
  const sort = ref('new')
  const keyword = ref('')
  const viewMode = ref('all')

  let searchTimer = null

  const isOwnedView = computed(() => viewMode.value === 'owned')

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

  function mapMyPackToListItem(pack) {
    return {
      id: pack.shopId,
      name: pack.name,
      coverUrl: pack.coverUrl,
      price: pack.pricePaid,
      owned: true,
      salesCount: 0,
    }
  }

  function setSort(value) {
    if (sort.value === value && viewMode.value === 'all') return
    viewMode.value = 'all'
    sort.value = value
    loadList(1)
  }

  async function showMyOwned() {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('查看已购表情包需要登录'))) return
    }
    if (viewMode.value === 'owned') return
    viewMode.value = 'owned'
    await loadMyPacks()
  }

  function onSearchInput() {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      if (viewMode.value === 'owned') loadMyPacks()
      else loadList(1)
    }, 320)
  }

  function goDetail(id) {
    detailDialogRef.value?.open(id)
    router.replace({ path: '/emoji-shop', query: { detail: String(id) } }).catch(() => {})
  }

  function onDetailClosed() {
    if (route.query.detail) {
      router.replace({ path: '/emoji-shop', query: {} }).catch(() => {})
    }
  }

  function onDetailPurchased(shopId) {
    const row = records.value.find((r) => r.id === shopId)
    if (row) row.owned = true
    if (viewMode.value === 'owned') loadMyPacks()
  }

  async function goUpload() {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('上传表情包需要登录'))) return
    }
    uploadDialogRef.value?.open()
    router.replace({ path: '/emoji-shop', query: { ...route.query, upload: '1' } }).catch(() => {})
  }

  function onUploadClosed() {
    if (route.query.upload) {
      const q = { ...route.query }
      delete q.upload
      router.replace({ path: '/emoji-shop', query: q }).catch(() => {})
    }
  }

  function onUploadCreated() {
    loadList(1)
    onUploadClosed()
  }

  function tryOpenUploadFromRoute() {
    if (route.query.upload === '1' && userStore.isLoggedIn) {
      uploadDialogRef.value?.open()
    }
  }

  async function loadMyPacks() {
    if (!userStore.isLoggedIn) {
      records.value = []
      total.value = 0
      return
    }
    pageNum.value = 1
    loading.value = true
    try {
      const res = await getShopMyPacks()
      if (res.code === 0 && Array.isArray(res.data)) {
        let items = res.data.map(mapMyPackToListItem)
        const q = keyword.value.trim()
        if (q) items = items.filter((item) => (item.name || '').includes(q))
        records.value = items
        total.value = items.length
      } else {
        records.value = []
        total.value = 0
      }
    } finally {
      loading.value = false
    }
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
    keyword,
    viewMode,
    isOwnedView,
    isVipMember,
    SORT_OPTIONS,
    MAIN_TABS,
    PRICE_SORTS,
    formatPrice,
    setSort,
    showMyOwned,
    onSearchInput,
    goDetail,
    goUpload,
    onUploadClosed,
    onUploadCreated,
    onDetailClosed,
    onDetailPurchased,
    loadList,
    loadMyPacks,
  }
}
