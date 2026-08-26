import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import {
  exchangeStarlightItem,
  getStarlightExchanges,
  getStarlightShopItems,
  getStarlightWallet,
  useStarlightItem,
} from '@/api/starlight'
import mengXinghuiIconUrl from '@/assets/svg/meng_xinghui.svg?url'
import AppPagination from '@/components/common/AppPagination.vue'
import {
  MENGBI_DIKOUQUAN_WEBP_URL as voucherCoverUrl,
  PRO_TIME_TO_TEST_WEBP_URL as vipTrialCoverUrl,
  QIANDAO_BUQIANKA_WEBP_URL as makeupCardCoverUrl,
} from '@/utils/clientOss'
import emptyShopItemUrl from '@/assets/images/mengxinghui_no_shop_item.png'

const CATEGORIES = [
  { key: 'HOT', label: '热门兑换' },
  { key: 'LIMITED', label: '限定收藏' },
  { key: 'COSMETIC', label: '外观装扮' },
  { key: 'UTILITY', label: '实用道具' },
]

const SHOP_PAGE_SIZE = 8
const BACKPACK_PAGE_SIZE = 8
const HISTORY_PAGE_SIZE = 5

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  balance: { type: Number, default: 0 },
})

const emit = defineEmits(['update:modelValue', 'balance-change', 'exchanged'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const viewMode = ref('shop')
const category = ref('HOT')
const pageNum = ref(1)
const total = ref(0)
const items = ref([])
const backpackItems = ref([])
const loading = ref(false)
const error = ref('')
const exchangingId = ref(null)
const usingId = ref(null)
const localBalance = ref(0)

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
const historyRecords = ref([])
const historyPage = ref(1)
const historyTotal = ref(0)

const currentPageSize = computed(() =>
  viewMode.value === 'backpack' ? BACKPACK_PAGE_SIZE : SHOP_PAGE_SIZE,
)

const pages = computed(() => Math.max(1, Math.ceil(total.value / currentPageSize.value) || 1))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    localBalance.value = Number(props.balance) || 0
    viewMode.value = 'shop'
    category.value = 'HOT'
    pageNum.value = 1
    await Promise.all([refreshBalance(), loadItems()])
  },
)

watch(
  () => props.balance,
  (v) => {
    if (props.modelValue) localBalance.value = Number(v) || 0
  },
)

async function refreshBalance() {
  try {
    const res = await getStarlightWallet()
    if (res.code === 0 && res.data) {
      localBalance.value = Number(res.data.balance) || 0
      emit('balance-change', localBalance.value)
    }
  } catch {
    // request 已提示
  }
}

async function reloadCurrent() {
  if (viewMode.value === 'backpack') {
    await loadBackpack()
  } else {
    await loadItems()
  }
}

async function loadItems() {
  loading.value = true
  error.value = ''
  try {
    const res = await getStarlightShopItems({
      category: category.value,
      pageNum: pageNum.value,
      pageSize: SHOP_PAGE_SIZE,
    })
    if (res.code === 0 && res.data) {
      items.value = Array.isArray(res.data.records) ? res.data.records : []
      total.value = Number(res.data.total) || 0
      pageNum.value = Number(res.data.pageNum) || pageNum.value
    } else {
      error.value = res.message || '加载失败'
      items.value = []
      total.value = 0
    }
  } catch (e) {
    error.value = e?.message || '加载失败'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadBackpack() {
  loading.value = true
  error.value = ''
  try {
    const res = await getStarlightExchanges({
      pageNum: pageNum.value,
      pageSize: BACKPACK_PAGE_SIZE,
    })
    if (res.code === 0 && res.data) {
      backpackItems.value = Array.isArray(res.data.records) ? res.data.records : []
      total.value = Number(res.data.total) || 0
      pageNum.value = Number(res.data.pageNum) || pageNum.value
    } else {
      error.value = res.message || '加载失败'
      backpackItems.value = []
      total.value = 0
    }
  } catch (e) {
    error.value = e?.message || '加载失败'
    backpackItems.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onCategory(key) {
  viewMode.value = 'shop'
  if (category.value === key && items.value.length) {
    return
  }
  category.value = key
  pageNum.value = 1
  await loadItems()
}

async function openBackpack() {
  if (viewMode.value === 'backpack') return
  viewMode.value = 'backpack'
  pageNum.value = 1
  await loadBackpack()
}

async function goPage(p) {
  if (p < 1 || p > pages.value || p === pageNum.value) return
  pageNum.value = p
  await reloadCurrent()
}

function stockText(item) {
  const daily = Number(item?.dailyLimit) || 0
  if (daily > 0) return `每日限购 ${daily} 次`
  const stock = Number(item?.stockRemaining)
  if (!Number.isFinite(stock) || stock < 0) return '不限量'
  return `剩余 ${stock} 件`
}

function isSoldOut(item) {
  const stock = Number(item?.stockRemaining)
  return Number.isFinite(stock) && stock === 0
}

async function onExchange(item) {
  if (!item?.id || exchangingId.value != null) return
  if (isSoldOut(item)) {
    ElMessage.warning('商品已售罄')
    return
  }
  if (localBalance.value < Number(item.priceStarlight || 0)) {
    ElMessage.warning('萌星辉不足')
    return
  }
  exchangingId.value = item.id
  try {
    const res = await exchangeStarlightItem({ itemId: item.id })
    if (res.code === 0 && res.data) {
      localBalance.value = Number(res.data.starlightBalanceAfter) || localBalance.value
      emit('balance-change', localBalance.value)
      emit('exchanged', res.data)
      if (isLotteryVoucherItem(item) || String(res.data.rewardType || '').toUpperCase() === 'LOTTERY_VOUCHER') {
        ElMessage.success('抵扣券已到账')
      } else if (isMakeupCardItem(item) || String(res.data.rewardType || '').toUpperCase() === 'MAKEUP_CARD') {
        ElMessage.success('补签卡已到账')
      } else {
        ElMessage.success('奖品已发放至您的背包')
      }
      await loadItems()
      if (historyVisible.value) {
        historyPage.value = 1
        await loadHistory()
      }
    }
  } catch {
    // request 已提示
  } finally {
    exchangingId.value = null
  }
}

async function onUse(row) {
  if (!row?.id || usingId.value != null) return
  if (Number(row.useStatus) === 1 || isLotteryVoucherRecord(row) || isMakeupCardRecord(row)) {
    ElMessage.info('该物品已使用')
    return
  }
  usingId.value = row.id
  try {
    const res = await useStarlightItem({ exchangeId: row.id })
    if (res.code === 0 && res.data) {
      ElMessage.success(
        res.data.rewardSummary && String(res.data.rewardSummary).startsWith('过期时间')
          ? res.data.rewardSummary
          : '使用成功',
      )
      await loadBackpack()
      if (historyVisible.value) {
        await loadHistory()
      }
    }
  } catch {
    // request 已提示
  } finally {
    usingId.value = null
  }
}

async function openHistory() {
  historyVisible.value = true
  historyPage.value = 1
  await loadHistory()
}

async function loadHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await getStarlightExchanges({
      pageNum: historyPage.value,
      pageSize: HISTORY_PAGE_SIZE,
    })
    if (res.code === 0 && res.data) {
      historyRecords.value = Array.isArray(res.data.records) ? res.data.records : []
      historyTotal.value = Number(res.data.total) || 0
      historyPage.value = Number(res.data.pageNum) || historyPage.value
    } else {
      historyError.value = res.message || '加载失败'
      historyRecords.value = []
    }
  } catch (e) {
    historyError.value = e?.message || '加载失败'
    historyRecords.value = []
  } finally {
    historyLoading.value = false
  }
}

function isVipTrialItem(item) {
  return String(item?.rewardType || '').toUpperCase() === 'VIP_DAYS'
}

function isLotteryVoucherItem(item) {
  return String(item?.rewardType || '').toUpperCase() === 'LOTTERY_VOUCHER'
}

function isMakeupCardItem(item) {
  return String(item?.rewardType || '').toUpperCase() === 'MAKEUP_CARD'
}

function isVipTrialRecord(row) {
  return String(row?.rewardType || '').toUpperCase() === 'VIP_DAYS'
    || String(row?.itemName || '').includes('体验卡')
    || String(row?.itemName || '').includes('VIP')
}

function isLotteryVoucherRecord(row) {
  return String(row?.rewardType || '').toUpperCase() === 'LOTTERY_VOUCHER'
    || String(row?.itemName || '').includes('抵扣券')
}

function isMakeupCardRecord(row) {
  return String(row?.rewardType || '').toUpperCase() === 'MAKEUP_CARD'
    || String(row?.itemName || '').includes('补签卡')
}

async function onHistoryPageChange(p) {
  if (historyLoading.value || p === historyPage.value) return
  historyPage.value = p
  await loadHistory()
}

function formatTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function closeShop() {
  visible.value = false
}
