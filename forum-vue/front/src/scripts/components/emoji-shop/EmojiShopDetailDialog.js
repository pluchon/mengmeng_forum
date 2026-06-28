import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getShopDetail, purchaseShop, updateShopStatus } from '@/api/shop'
import { useUserStore } from '@/stores/user'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { formatCheckinLogDateOnly } from '@/utils/datetime'

const ITEM_PAGE_SIZE = 9

function effectiveVipTier(tier, expireAt) {
  const t = Number(tier) || 0
  if (t <= 0) return 0
  if (!expireAt) return t
  const ms = new Date(expireAt).getTime()
  if (Number.isNaN(ms)) return t
  return Date.now() > ms ? 0 : t
}

export function useEmojiShopDetailDialog({ onPurchased, onClosed } = {}) {
  const router = useRouter()
  const userStore = useUserStore()
  const wallet = usePointsWalletStore()

  const visible = ref(false)
  const loading = ref(false)
  const purchasing = ref(false)
  const detail = ref(null)
  const previewIndex = ref(0)
  const itemPage = ref(1)
  const itemPageSize = ref(ITEM_PAGE_SIZE)

  const previewUrl = computed(() => {
    const urls = detail.value?.imageUrls
    if (urls?.length) return urls[previewIndex.value] || urls[0]
    return detail.value?.coverUrl || ''
  })

  const imageCount = computed(() => {
    const total = Number(detail.value?.imagePage?.total)
    if (Number.isFinite(total)) return total
    return detail.value?.imageUrls?.length || 0
  })

  const uploaderVipTier = computed(() =>
    effectiveVipTier(detail.value?.uploadUserVipTier, detail.value?.uploadUserVipExpireAt),
  )

  const statusLabel = computed(() => {
    const s = detail.value?.status
    if (s === 1) return { text: '上架中', type: 'success' }
    if (s === 2) return { text: '已下架', type: 'warning' }
    if (s === 0) return { text: '待审核', type: 'info' }
    return { text: '未知', type: 'info' }
  })

  const createDateText = computed(() =>
    detail.value?.createTime ? formatCheckinLogDateOnly(detail.value.createTime) : '—',
  )

  const dialogTitle = computed(() => {
    const name = detail.value?.name?.trim()
    return name ? `「${name}」表情包详情` : '表情包详情'
  })

  const descriptionText = computed(() => {
    const d = detail.value?.description?.trim()
    if (d) return d
    return '购买后在私信输入「已购」即可选择发送。支持所有私信场景。'
  })

  const priceText = computed(() => {
    const p = Number(detail.value?.price)
    if (!Number.isFinite(p) || p <= 0) return { main: '免费', unit: '' }
    return { main: String(p), unit: '积分' }
  })

  const purchaseHint = computed(() => {
    if (!detail.value || detail.value.owned) return ''
    const p = Number(detail.value.price) || 0
    if (!userStore.isLoggedIn) return ''
    if (p === 0) return '免费领取'
    const remain = wallet.balance - p
    return `购买后剩余 ${remain} 积分`
  })

  const isAuthor = computed(() => {
    const uid = userStore.id
    const authorId = detail.value?.uploadUserId
    return uid != null && authorId != null && String(uid) === String(authorId)
  })

  const canPurchase = computed(
    () =>
      userStore.isLoggedIn &&
      detail.value &&
      !detail.value.owned &&
      !isAuthor.value &&
      detail.value.status === 1,
  )

  const purchaseDisabled = computed(() => {
    if (!detail.value) return true
    const p = Number(detail.value.price) || 0
    return p > 0 && wallet.balance < p
  })

  const purchaseLabel = computed(() => {
    if (!detail.value) return '立即购买'
    if (detail.value.owned) return '已拥有'
    if (!userStore.isLoggedIn) return '登录后购买'
    if (detail.value.price === 0) return '免费领取'
    const hint = purchaseHint.value
    return hint ? `立即购买 · ${hint}` : '立即购买'
  })

  function setPreview(idx) {
    previewIndex.value = idx
  }

  async function loadDetail(id, page, clearDetail = false) {
    loading.value = true
    previewIndex.value = 0
    if (clearDetail) {
      detail.value = null
    }
    try {
      const res = await getShopDetail(id, {
        itemPageNum: page,
        itemPageSize: itemPageSize.value,
      })
      if (res.code === 0 && res.data) {
        detail.value = res.data
        const pageData = res.data.imagePage
        itemPage.value = Number(pageData?.pageNum) || page
        itemPageSize.value = Number(pageData?.pageSize) || ITEM_PAGE_SIZE
        if (isAuthor.value) {
          detail.value.owned = true
        }
      } else {
        ElMessage.warning('商品不存在或已下架')
        close()
      }
    } finally {
      loading.value = false
    }
  }

  async function open(shopId) {
    const id = Number(shopId)
    if (!Number.isFinite(id) || id <= 0) return
    visible.value = true
    previewIndex.value = 0
    itemPage.value = 1
    itemPageSize.value = ITEM_PAGE_SIZE
    detail.value = null
    try {
      if (userStore.isLoggedIn) await wallet.refresh()
      await loadDetail(id, 1, true)
    } catch {
      loading.value = false
    }
  }

  async function onItemPageChange(page) {
    const id = Number(detail.value?.id)
    if (!Number.isFinite(id) || id <= 0) return
    await loadDetail(id, page, false)
  }

  function close() {
    visible.value = false
    detail.value = null
    previewIndex.value = 0
    itemPage.value = 1
    itemPageSize.value = ITEM_PAGE_SIZE
    onClosed?.()
  }

  /** 先跳转个人主页，避免 close/onClosed 的 replace 覆盖 router-link 导航 */
  function goUploaderProfile() {
    const uid = Number(detail.value?.uploadUserId)
    if (!Number.isFinite(uid) || uid <= 0) return
    visible.value = false
    detail.value = null
    previewIndex.value = 0
    itemPage.value = 1
    itemPageSize.value = ITEM_PAGE_SIZE
    router.push(`/profile/${uid}`)
  }

  async function onPurchase() {
    if (!detail.value || detail.value.owned) return
    if (!userStore.isLoggedIn) {
      router.push('/sign-in')
      return
    }
    try {
      await ElMessageBox.confirm(
        detail.value.price === 0 ? '确认领取该表情包？' : `确认消耗 ${detail.value.price} 积分购买？`,
        '购买确认',
        { type: 'warning' },
      )
    } catch {
      return
    }
    purchasing.value = true
    try {
      const res = await purchaseShop(detail.value.id)
      if (res.code === 0) {
        wallet.setBalance(res.data)
        detail.value.owned = true
        ElMessage.success('购买成功')
        onPurchased?.(detail.value.id)
      }
    } finally {
      purchasing.value = false
    }
  }

  async function setShelf(status) {
    if (!detail.value) return
    try {
      const res = await updateShopStatus(detail.value.id, status)
      if (res.code === 0) {
        ElMessage.success(status === 1 ? '已上架' : '已下架')
        detail.value.status = status
      }
    } catch {
      /* 拦截器已提示 */
    }
  }

  watch(visible, (v) => {
    if (!v) detail.value = null
  })

  return {
    visible,
    loading,
    purchasing,
    detail,
    previewIndex,
    itemPage,
    itemPageSize,
    previewUrl,
    imageCount,
    uploaderVipTier,
    statusLabel,
    createDateText,
    dialogTitle,
    descriptionText,
    priceText,
    userStore,
    wallet,
    isAuthor,
    canPurchase,
    purchaseDisabled,
    purchaseLabel,
    setPreview,
    open,
    onItemPageChange,
    close,
    goUploaderProfile,
    onPurchase,
    setShelf,
  }
}
