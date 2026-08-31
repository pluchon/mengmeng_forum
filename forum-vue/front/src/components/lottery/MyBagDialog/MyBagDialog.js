import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { getBagItems, useBagItem } from '@/api/bag'
import mengXinghuiIconUrl from '@/assets/svg/meng_xinghui.svg?url'
import AppPagination from '@/components/common/AppPagination.vue'
import {
  MENGBI_DIKOUQUAN_WEBP_URL as voucherCoverUrl,
  VIP_RESET_CARD_WEBP_URL as quotaResetCoverUrl,
  QIANDAO_BUQIANKA_WEBP_URL as makeupCardCoverUrl,
  PRO_TIME_TO_TEST_WEBP_URL as vipCardCoverUrl,
  PRIZE_ZHOUBIAN_WEBP_URL as goodsCoverUrl,
} from '@/utils/clientOss'
import emptyBagUrl from '@/assets/images/mengxinghui_no_shop_item.png'

// 与后端 UserBagServiceImpl 的状态常量对齐
const STATUS_UNUSED = 0
const STATUS_USED = 1
const STATUS_PENDING = 2

const FILTERS = [
  { key: 'ALL', label: '全部', status: null },
  { key: 'UNUSED', label: '未使用', status: STATUS_UNUSED },
  { key: 'USED', label: '已使用', status: STATUS_USED },
  { key: 'PENDING', label: '待发放', status: STATUS_PENDING },
]

const PAGE_SIZE = 8

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'used'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const filterKey = ref('ALL')
const pageNum = ref(1)
const total = ref(0)
const items = ref([])
const loading = ref(false)
const error = ref('')
const usingId = ref(null)

const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE) || 1))

const currentStatus = computed(
  () => FILTERS.find((f) => f.key === filterKey.value)?.status ?? null,
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    filterKey.value = 'ALL'
    pageNum.value = 1
    await load()
  },
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { pageNum: pageNum.value, pageSize: PAGE_SIZE }
    if (currentStatus.value !== null) params.useStatus = currentStatus.value
    const res = await getBagItems(params)
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

async function onFilter(key) {
  if (filterKey.value === key) return
  filterKey.value = key
  pageNum.value = 1
  await load()
}

async function goPage(p) {
  if (p < 1 || p > pages.value || p === pageNum.value) return
  pageNum.value = p
  await load()
}

function coverOf(row) {
  switch (String(row?.rewardType || '').toUpperCase()) {
    case 'LOTTERY_VOUCHER':
      return voucherCoverUrl
    case 'MAKEUP_CARD':
      return makeupCardCoverUrl
    case 'QUOTA_RESET':
      return quotaResetCoverUrl
    case 'VIP_DAYS':
      return vipCardCoverUrl
    case 'GOODS':
      return goodsCoverUrl
    default:
      return ''
  }
}

function sourceLabel(row) {
  return String(row?.source || '').toUpperCase() === 'LOTTERY' ? '抽奖所得' : '商城兑换'
}

// 已使用的显示后端写回的发放结果；未使用的显示这件东西是什么
function detailText(row) {
  if (row?.grantSummary) return row.grantSummary
  const value = Number(row?.rewardValue) || 0
  switch (String(row?.rewardType || '').toUpperCase()) {
    case 'LOTTERY_VOUCHER':
      return `抵扣券 ×${value}`
    case 'MAKEUP_CARD':
      return `补签卡 ×${value}`
    case 'QUOTA_RESET':
      return 'AI 额度重置卡'
    case 'VIP_DAYS':
      return `${Number(row?.vipTier) === 2 ? 'MAX' : 'PRO'} 会员体验 ${value} 天`
    case 'GOODS':
      return '周边实物'
    default:
      return '背包物品'
  }
}

function statusOf(row) {
  return Number(row?.useStatus) || STATUS_UNUSED
}

function btnText(row) {
  if (usingId.value === row.id) return '使用中...'
  const s = statusOf(row)
  if (s === STATUS_USED) return '已使用'
  if (s === STATUS_PENDING) return '待发放'
  return '使用'
}

function btnDisabled(row) {
  return usingId.value != null || statusOf(row) !== STATUS_UNUSED
}

async function onUse(row) {
  if (!row?.id || btnDisabled(row)) return
  usingId.value = row.id
  try {
    const res = await useBagItem(row.id)
    if (res.code === 0 && res.data) {
      ElMessage.success(res.data.grantSummary || '使用成功')
      emit('used', res.data)
      await load()
    }
  } catch {
    // request 已提示
  } finally {
    usingId.value = null
  }
}

function closeBag() {
  visible.value = false
}
