import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getVipCenter, getVipPurchaseRecords } from '@/api/vip'
import { VIP_PLAN_META, formatYuan } from '@/constants/vipPlans'
import { VIP_DIALOG_ICONS } from '@/constants/vipDialogIcons'
import { formatForumDateOnlyShanghai, formatForumDateTimeShanghai } from '@/utils/datetime'
import {
  VIP_BG_WEBP_URL as vipBgUrl,
  VIP_FREE_VISUAL_WEBP_URL as planFreeVisualUrl,
  VIP_MAX_VISUAL_WEBP_URL as planMaxVisualUrl,
  VIP_PRO_VISUAL_WEBP_URL as planProVisualUrl,
} from '@/utils/clientOss'
import alipayIconUrl from '@/assets/svg/alipay.svg'
import wechatPayIconUrl from '@/assets/svg/WeChat_pay.svg'
import qqmailIconUrl from '@/assets/svg/qqmail.svg'
import outlookIconUrl from '@/assets/svg/outlook.svg'
import '@/assets/styles/vip-subscribe-dialog.css'

// 方案卡窄栏展示用：压缩长权益文案，避免撑破卡片
function shortenFeatureText(text) {
  const raw = String(text || '').trim()
  if (!raw) return ''
  return raw
    .replace(/、{2,}/g, '、')
    .replace(/^、|、$/g, '')
    .replace('Qwen 深度写作每日', '深度写作每日')
    .replace('Qwen 智能写作', '智能写作')
    .replace('Qwen Flash 每日', 'Flash 每日')
    .replace('推荐配图要点', '推荐配图')
}

const PLAN_VISUAL_URL = {
  free: planFreeVisualUrl,
  pro: planProVisualUrl,
  max: planMaxVisualUrl,
}

const COMMONS_ITEMS = [
  {
    key: 'identity',
    left: '18px',
    title: '专属身份',
    desc: '会员标识与装扮',
    icon: VIP_DIALOG_ICONS.benefitBadge,
  },
  {
    key: 'ai',
    left: '215px',
    title: '深度使用 AI',
    desc: '更高额度与优先响应',
    icon: VIP_DIALOG_ICONS.benefitBot,
  },
  {
    key: 'tools',
    left: '415px',
    title: '工具整合',
    desc: '各类小工具',
    icon: VIP_DIALOG_ICONS.benefitBlocks,
  },
]

const PURCHASE_PAGE_SIZE = 10

export function useVipSubscribeDialog(props, emit) {
  const router = useRouter()
  const loading = ref(false)
  const plans = ref([])
  const selectedCode = ref('pro')
  const payChannel = ref('alipay')
  const agreeProtocol = ref(true)
  const membership = ref({ vipTier: 0, vipExpireAt: null })
  const purchaseHistoryVisible = ref(false)
  const purchaseHistoryLoading = ref(false)
  const purchaseRecords = ref([])
  const purchaseRecordTotal = ref(0)
  const purchaseRecordPage = ref(1)

  const visible = computed({
    get: () => props.modelValue,
    set: (v) => emit('update:modelValue', v),
  })

  const planCards = computed(() => {
    const byCode = Object.fromEntries((plans.value || []).map((p) => [p.code, p]))
    return ['free', 'pro', 'max'].map((code) => {
      const api = byCode[code] || {}
      const planMeta = VIP_PLAN_META[code]
      const original = Number(api.originalPrice) || 0
      const firstMonth = Number(api.firstMonthPrice) || 0
      const cash = {
        ...planMeta,
        original,
        firstMonth,
        save: Math.max(0, original - firstMonth),
      }
      const features = (api.features || [])
        .filter((f) => f.enabled)
        .map((f) => shortenFeatureText(f.text))
        .filter(Boolean)
      return {
        code,
        tier: planMeta.tier,
        name: code === 'free' ? '免费' : (api.name || cash.name),
        subtitle: api.subtitle || '',
        badge: api.badge || null,
        buttonState: api.buttonState || 'subscribe',
        buttonLabel: api.buttonLabel || (code === 'free' ? '当前方案' : `选择 ${cash.name}`),
        features,
        cash,
        firstPurchaseEligible: Boolean(api.firstPurchaseEligible),
      }
    })
  })

  const commonsItems = COMMONS_ITEMS

  const selectedPlan = computed(() => planCards.value.find((p) => p.code === selectedCode.value) || planCards.value[1])

  const showFirstMonth = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return false
    return p.buttonState === 'subscribe' && p.firstPurchaseEligible
  })

  const displayPrice = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return 0
    return showFirstMonth.value ? p.cash.firstMonth : p.cash.original
  })

  const payTitle = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return '当前为免费方案'
    return `升级为 ${p.name}`
  })
  const membershipExpiryText = computed(() => {
    const tier = Number(membership.value.vipTier) || 0
    const expireAt = membership.value.vipExpireAt
    if (tier <= 0 || !expireAt) return ''
    const tierName = tier >= 2 ? 'MAX' : 'PRO'
    return `${tierName}等级会员于${formatForumDateOnlyShanghai(expireAt)}到期`
  })

  function planVisualUrl(code) {
    return PLAN_VISUAL_URL[code] || PLAN_VISUAL_URL.free
  }

  async function loadPlans() {
    loading.value = true
    try {
      const res = await getVipCenter()
      plans.value = res?.data?.plans || []
      membership.value = {
        vipTier: Number(res?.data?.vipTier) || 0,
        vipExpireAt: res?.data?.vipExpireAt || null,
      }
      const cards = ['free', 'pro', 'max'].map((code) => {
        const api = (plans.value || []).find((p) => p.code === code) || {}
        return { code, buttonState: api.buttonState || 'subscribe' }
      })
      const prefer = cards.find((p) => p.code === 'pro' && p.buttonState === 'subscribe')
        || cards.find((p) => p.code === 'max' && p.buttonState === 'subscribe')
        || cards.find((p) => p.code === 'pro')
        || cards[0]
      selectedCode.value = prefer?.code || 'pro'
    } catch {
      ElMessage.error('加载会员方案失败')
      plans.value = []
      membership.value = { vipTier: 0, vipExpireAt: null }
    } finally {
      loading.value = false
    }
  }

  function selectPlan(plan) {
    if (!plan || plan.tier === 0) return
    selectedCode.value = plan.code
  }

  function close() {
    visible.value = false
  }

  function openVipAgreement() {
    close()
    router.push('/vip-agreement')
  }

  function maskOrderNo(orderNo) {
    const value = String(orderNo || '')
    if (value.length <= 10) return value || '—'
    return `${value.slice(0, 6)}…${value.slice(-4)}`
  }

  function paymentStateClass(state) {
    if (Number(state) === 1) return 'is-success'
    if (Number(state) === 2) return 'is-closed'
    return 'is-pending'
  }

  async function loadPurchaseRecords(page = purchaseRecordPage.value) {
    purchaseHistoryLoading.value = true
    purchaseRecordPage.value = page
    try {
      const res = await getVipPurchaseRecords({
        pageNum: page,
        pageSize: PURCHASE_PAGE_SIZE,
      })
      purchaseRecords.value = res?.data?.records || []
      purchaseRecordTotal.value = Number(res?.data?.total) || 0
    } catch {
      purchaseRecords.value = []
      purchaseRecordTotal.value = 0
      ElMessage.error('加载购买记录失败')
    } finally {
      purchaseHistoryLoading.value = false
    }
  }

  function openPurchaseHistory() {
    purchaseHistoryVisible.value = true
    loadPurchaseRecords(1)
  }

  watch(
    () => props.modelValue,
    (open) => {
      if (open) loadPlans()
    },
  )

  return {
    vipBgUrl,
    alipayIconUrl,
    wechatPayIconUrl,
    qqmailIconUrl,
    outlookIconUrl,
    visible,
    loading,
    planCards,
    commonsItems,
    selectedCode,
    selectedPlan,
    payChannel,
    agreeProtocol,
    showFirstMonth,
    displayPrice,
    payTitle,
    membershipExpiryText,
    purchaseHistoryVisible,
    purchaseHistoryLoading,
    purchaseRecords,
    purchaseRecordTotal,
    purchaseRecordPage,
    purchasePageSize: PURCHASE_PAGE_SIZE,
    formatYuan,
    formatPurchaseDateTime: formatForumDateTimeShanghai,
    formatPurchaseDate: formatForumDateOnlyShanghai,
    maskOrderNo,
    paymentStateClass,
    planVisualUrl,
    selectPlan,
    openVipAgreement,
    openPurchaseHistory,
    loadPurchaseRecords,
    close,
  }
}
