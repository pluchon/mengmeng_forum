import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import {
  getShopDetail,
  purchaseShop,
  updateShopStatus,
  getShopEmojiAvailability,
} from '@/api/shop'
import { apiErrorCode } from '@/utils/apiData'
import { useUserStore } from '@/stores/user'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { formatCheckinLogDateOnly } from '@/utils/datetime'
import offlineImageUrl from '@/assets/images/biaoqing_offline.png'

// 与后端 ResultCode.FAILED_SHOP_OFFLINE 对齐；下架有专门的占位页，
// 不需要拦截器再弹一次 toast
const SHOP_OFFLINE_CODE = 1221

const ITEM_PAGE_SIZE = 8

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
    if (!Number.isFinite(p) || p <= 0) return '免费'
    return `${p}积分`
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

  async function setPreview(idx) {
    const url = detail.value?.imageUrls?.[idx]
    const shopId = detail.value?.id
    if (!url || !shopId) return
    try {
      const res = await getShopEmojiAvailability({ shopId, url })
      const status = res.data?.status
      if (res.code === 0 && status === 'AVAILABLE') {
        previewIndex.value = idx
        return
      }
      if (status === 'SERIES_OFFLINE') {
        ElMessage.warning('该表情包系列已下架')
        await loadDetail(shopId, itemPage.value, false)
      } else {
        ElMessage.warning('该表情已被删除')
        if (status === 'ITEM_DELETED') await loadDetail(shopId, itemPage.value, false)
        else close()
      }
    } catch {
      // 请求拦截器已提示
    }
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
      }, { silentBizCodes: [SHOP_OFFLINE_CODE] })
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
    } catch (error) {
      // 已下架：作者已经把系列撤了，但从评论/聊天点进来的用户仍然应该看到
      // 一个说明页，而不是一闪而过的报错。1221 是后端专门为此区分出来的码
      if (apiErrorCode(error) === SHOP_OFFLINE_CODE) {
        offlineNotice.value = true
        detail.value = null
      } else {
        close()
      }
    } finally {
      loading.value = false
    }
  }

  const offlineNotice = ref(false)
  const offlineImage = offlineImageUrl

  async function open(shopId) {
    const id = Number(shopId)
    if (!Number.isFinite(id) || id <= 0) return
    visible.value = true
    previewIndex.value = 0
    itemPage.value = 1
    itemPageSize.value = ITEM_PAGE_SIZE
    detail.value = null
    offlineNotice.value = false
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
    offlineNotice.value = false
    previewIndex.value = 0
    itemPage.value = 1
    itemPageSize.value = ITEM_PAGE_SIZE
    onClosed?.()
  }

  // 先跳转个人主页，避免 close/onClosed 的 replace 覆盖 router link 导航
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
      await confirmDialog(
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
      // 拦截器已提示
    }
  }

  watch(visible, (v) => {
    if (!v) detail.value = null
  })

  return {
    visible,
    loading,
    offlineNotice,
    offlineImage,
    purchasing,
    detail,
    previewIndex,
    itemPage,
    itemPageSize,
    previewUrl,
    imageCount,
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
    setPreview,
    open,
    onItemPageChange,
    close,
    goUploaderProfile,
    onPurchase,
    setShelf,
  }
}
