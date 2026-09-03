import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createVipOrder,
  getVipCenter,
  getVipPurchaseRecords,
  mockPayVipOrder,
} from '@/api/vip'
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

// 真实渠道还没接，下单一律走本地模拟渠道。
// 接入之后把这里换成用户选中的渠道即可，后端按 channel 找网关，其余一行不用改
const ACTIVE_PAY_CHANNEL = 'mock'

// 已开通与当前方案两种状态不能下单
const UNBUYABLE_BUTTON_STATES = ['current', 'owned']

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
  const currentOrder = ref(null)
  const orderPaying = ref(false)

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
        upgradePrice: api.upgradePrice == null ? null : Number(api.upgradePrice),
        upgradeRemainingDays: Number(api.upgradeRemainingDays) || 0,
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

  const isUpgrade = computed(() => selectedPlan.value?.buttonState === 'upgrade')

  const displayPrice = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return 0
    // 升级收的是按剩余天数折算的差价，不是整月价，金额由后端算好带下来
    if (isUpgrade.value) return p.upgradePrice ?? 0
    return showFirstMonth.value ? p.cash.firstMonth : p.cash.original
  })

  const payTitle = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return '当前为免费方案'
    if (p.buttonState === 'upgrade') return `升级为 ${p.name}`
    if (p.buttonState === 'renew') return `续费 ${p.name}`
    if (UNBUYABLE_BUTTON_STATES.includes(p.buttonState)) return `${p.name} 已开通`
    return `开通 ${p.name}`
  })

  const priceHint = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return ''
    if (isUpgrade.value) return `剩余 ${p.upgradeRemainingDays} 天的档位差价`
    if (showFirstMonth.value) return '新用户专享优惠'
    if (p.buttonState === 'renew') return '续费从原到期日往后接'
    return '续费按原价计费'
  })

  const canSubmitOrder = computed(() => {
    const p = selectedPlan.value
    if (!p || p.tier === 0) return false
    return !UNBUYABLE_BUTTON_STATES.includes(p.buttonState)
  })

  // 扫码区上的浮层文案。它同时是支付按钮，不再另设一个"立即开通"
  const qrActionText = computed(() => {
    if (orderPaying.value) return '支付中…'
    if (!canSubmitOrder.value) return '当前档位无需支付'
    if (!agreeProtocol.value) return '请先同意服务协议'
    return `点击模拟扫码支付 ¥${formatYuan(displayPrice.value)}`
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
      // 默认选中一张真能下单的卡。MAX 用户的 PRO 卡是"已拥有更高档位"，
      // 选中它会让支付岛整块变成不可点，看起来像坏了
      const buyable = (p) => !UNBUYABLE_BUTTON_STATES.includes(p.buttonState)
      const prefer = cards.find((p) => p.code === 'pro' && buyable(p))
        || cards.find((p) => p.code === 'max' && buyable(p))
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
    // 换了档位，原来那张单的金额就不对了，丢掉重新下
    currentOrder.value = null
  }

  /**
   * 点二维码 = 扫码付款，一次走完下单与支付。
   *
   * 下单只把档位交给后端，金额、订单类型、定价体系全由服务端判定；
   * 付款走的是真实回调链路（后端按渠道回调的形状自签一份），
   * 验签、金额比对、幂等一个都不跳过。
   */
  async function payByQrCode() {
    if (!canSubmitOrder.value || orderPaying.value) return
    if (!agreeProtocol.value) {
      ElMessage.warning('请先阅读并同意《会员服务协议》')
      return
    }
    orderPaying.value = true
    try {
      // 上一笔没付成功的单还在就接着付，避免每点一次都开一张新单
      let orderNo = currentOrder.value?.orderNo
      if (!orderNo) {
        const created = await createVipOrder({
          tier: selectedPlan.value.tier,
          payChannel: ACTIVE_PAY_CHANNEL,
        })
        currentOrder.value = created?.data || null
        orderNo = currentOrder.value?.orderNo
      }
      if (!orderNo) return

      const res = await mockPayVipOrder({ orderNo })
      const paid = res?.data || null
      if (Number(paid?.paymentState) === 1) {
        ElMessage.success('开通成功，权益已到账')
        currentOrder.value = null
        // 重新拉一次方案，卡片状态立刻跟着变。
        // 顶栏那颗会员胶囊由 useVipStatusEntry 在弹窗关闭时回刷，这里不重复处理
        await loadPlans()
      } else {
        currentOrder.value = paid
        ElMessage.warning('支付尚未完成，可在购买记录中查看这笔订单')
      }
    } catch {
      // 失败提示由 request 拦截器统一弹出。
      // 这张单可能已经建出来了，丢掉本地引用让下次重新报价，免得拿着过期金额去付
      currentOrder.value = null
    } finally {
      orderPaying.value = false
    }
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
      if (open) {
        currentOrder.value = null
        loadPlans()
      }
    },
  )

  return {
    vipBgUrl,
    alipayIconUrl,
    wechatPayIconUrl,
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
    isUpgrade,
    displayPrice,
    payTitle,
    priceHint,
    canSubmitOrder,
    qrActionText,
    currentOrder,
    orderPaying,
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
    payByQrCode,
    openVipAgreement,
    openPurchaseHistory,
    loadPurchaseRecords,
    close,
  }
}
