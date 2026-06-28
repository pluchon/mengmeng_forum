import { ElMessage } from 'element-plus'
import {
  Trophy,
  Present,
  TrendCharts,
  InfoFilled,
  WarningFilled,
  ArrowDown,
  Coin,
  Grid,
} from '@element-plus/icons-vue'
import { LOTTERY_DEMO_NOTICE } from '@/constants/site'
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import {
  getLotteryActivities,
  getLotteryInfo,
  getLotteryRecords,
  lotteryDraw,
  claimLotterySurpriseBonus,
} from '@/api/lottery'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import EChart from '@/components/common/EChart.vue'
import recordIconUrl from '@/assets/svg/抽奖记录.svg?url'
import { clientOssUrl } from '@/utils/clientOss'

const VISIBLE_ACTIVITY_LIMIT = 3
const HISTORY_PAGE_SIZE = 12

const surpriseTeaserImg = clientOssUrl('抽奖惊喜.webp')
const surpriseRewardImg = clientOssUrl('抽奖.webp')

const CHART_PALETTE = ['#9ca3af', '#34d399', '#a78bfa', '#f59e0b', '#f97316', '#ec4899', '#60a5fa', '#d4537e']

/** 与后端 Constant.POINTS_LOTTERY_PAGE_SURPRISE_AMOUNT 一致（展示文案） */
const PAGE_SURPRISE_POINTS = 200

const loading = ref(true)
const busy = ref(false)
const surpriseClaimBusy = ref(false)

const activityCoverFallback = surpriseTeaserImg

const costRuleHints = computed(() => [
  '单次消耗积分参与抽奖',
  '积分奖即时到账，其它奖品以站内通知为准',
  '概率按活动权重动态计算（售罄档位自动剔除并重算）',
  '十连 Soft：至少 1 件稀有档（大奖/周边/VIP）',
  `累计 ${info.hardPityThreshold ?? 50} 抽未出神秘大奖则下一次必出神秘大奖档`,
])

const activityList = ref([])
const selectedActivityId = ref(null)
const activitySwitchVisible = ref(false)

const info = reactive({
  activityId: null,
  title: '',
  description: '',
  costPointsPerDraw: 30,
  balance: 0,
  prizes: [],
  pityDrawsSinceJackpot: 0,
  hardPityThreshold: 50,
  prizeWinHeat: [],
  recentDraws: [],
  lotterySurpriseClaimed: false,
})

const historyDialogVisible = ref(false)
const historyLoading = ref(false)
const historyRecords = ref([])
const historyPage = ref(1)
const historyPageSize = ref(HISTORY_PAGE_SIZE)
const historyTotal = ref(0)
const historyTableRows = computed(() =>
  historyRecords.value.map((r) => ({
    kind: r.multiDraw === 1 ? '十连' : '单抽',
    prizeName: (r.prizeName != null && String(r.prizeName).trim() !== '' ? String(r.prizeName).trim() : null) || '—',
    rewardDetail: (r.rewardDetail != null && String(r.rewardDetail).trim() !== '' ? String(r.rewardDetail).trim() : null) || '—',
    createTime: formatDrawTime(r.createTime),
  })),
)

const surprisePhaseVisible = ref(false)
const surprisePhase = ref(1)
const surprisePreviewRef = ref(null)

const surpriseDialogTitle = computed(() => {
  if (surprisePhase.value === 3) return '惊喜降临'
  return '温馨提示'
})

function openSurprisePhase1() {
  if (info.lotterySurpriseClaimed) {
    ElMessage.info('彩蛋积分已经领取过了～')
    return
  }
  surprisePhase.value = 1
  surprisePhaseVisible.value = true
}

async function confirmSurpriseClaim() {
  if (surpriseClaimBusy.value) return
  surpriseClaimBusy.value = true
  try {
    const res = await claimLotterySurpriseBonus()
    if (res.code !== 0 || !res.data) return
    const d = res.data
    if (d.alreadyClaimed) {
      info.lotterySurpriseClaimed = true
      ElMessage.info('已经领取过了～')
      surprisePhaseVisible.value = false
      return
    }
    if (d.granted && typeof d.balanceAfter === 'number') {
      info.balance = d.balanceAfter
      info.lotterySurpriseClaimed = true
      await pointsWallet.refresh()
      await loadInfo({ silent: true })
      surprisePhase.value = 3
    }
  } catch {
    /* request 已提示 */
  } finally {
    surpriseClaimBusy.value = false
  }
}

function resetSurpriseFlow() {
  surprisePhase.value = 1
}

function focusSurprisePreview() {
  nextTick(() => {
    surprisePreviewRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function onSurpriseImgError(e) {
  const el = e?.target
  if (el && el.src !== surpriseTeaserImg) {
    el.src = surpriseTeaserImg
  }
}

/** idle | single_shuffle | single_result | ten_shuffle | ten_result */
const phase = ref('idle')

const SHUFFLE_MIN_MS = 1000

function delay(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

const costPer = computed(() => info.costPointsPerDraw ?? 30)
const tenCost = computed(() => costPer.value * 10)

const ptsToTen = computed(() => Math.max(0, tenCost.value - (info.balance ?? 0)))

const tenProgressPct = computed(() => {
  const need = tenCost.value
  if (need <= 0) return 0
  return Math.min(100, Math.round(((info.balance ?? 0) / need) * 100))
})

const hardPityRemaining = computed(() => {
  const th = info.hardPityThreshold ?? 50
  const cur = info.pityDrawsSinceJackpot ?? 0
  return Math.max(0, th - cur)
})

const sidebarActivities = computed(() => {
  const list = activityList.value
  if (list.length <= VISIBLE_ACTIVITY_LIMIT) return list
  const selId = selectedActivityId.value
  const selected = list.find((a) => a.id === selId)
  const rest = list.filter((a) => a.id !== selId)
  const merged = selected ? [selected, ...rest] : list
  return merged.slice(0, VISIBLE_ACTIVITY_LIMIT)
})

const hasMoreActivities = computed(() => activityList.value.length > VISIBLE_ACTIVITY_LIMIT)

const canSwitchActivity = computed(() => phase.value === 'idle' && !busy.value)

function openActivitySwitch() {
  if (!canSwitchActivity.value || activityList.value.length <= VISIBLE_ACTIVITY_LIMIT) return
  activitySwitchVisible.value = true
}

function onActivityCoverError(e) {
  const el = e?.target
  if (el && el.src !== activityCoverFallback) {
    el.src = activityCoverFallback
  }
}

async function loadActivities() {
  try {
    const res = await getLotteryActivities()
    if (res.code === 0 && Array.isArray(res.data)) {
      activityList.value = res.data
    }
  } catch {
    /* request 已提示 */
  }
}

async function onSelectActivity(id) {
  if (!canSwitchActivity.value) return
  if (selectedActivityId.value === id) return
  selectedActivityId.value = id
  await loadInfo({ activityId: id, silent: true })
  if (historyDialogVisible.value) {
    historyPage.value = 1
    await loadHistoryRecords()
  }
}

async function onSelectActivityFromDialog(id) {
  await onSelectActivity(id)
  activitySwitchVisible.value = false
}

function formatPrizePercent(p) {
  const prizes = info.prizes || []
  const total = prizes.reduce((s, x) => s + (x.weight ?? 0), 0)
  if (!total) return '概率 —'
  const pct = (((p.weight ?? 0) / total) * 100).toFixed(2)
  return `概率 ${pct}%`
}

const pieOption = computed(() => {
  const prizes = info.prizes || []
  const filtered = prizes.filter((p) => (p.weight ?? 0) > 0)
  const total = filtered.reduce((s, p) => s + (p.weight ?? 0), 0)
  const data = filtered.map((p, i) => ({
    name: p.name,
    value: p.weight ?? 0,
    itemStyle: { color: CHART_PALETTE[i % CHART_PALETTE.length] },
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const pct =
          total > 0 ? ((((params.value ?? 0) / total) * 100).toFixed(2)) : '0.00'
        return `${params.name}<br/>概率: ${pct}%`
      },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      textStyle: { color: '#4e5969', fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['34%', '62%'],
        avoidLabelOverlap: true,
        label: { color: '#4e5969', fontSize: 11 },
        labelLine: { lineStyle: { color: 'rgba(0,0,0,0.15)' } },
        data,
      },
    ],
  }
})

const barOption = computed(() => {
  const rows = info.prizeWinHeat || []
  const names = rows.map((r) => r.prizeName ?? '')
  const vals = rows.map((r) => Number(r.winCount ?? 0))
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      textStyle: { color: '#4e5969', fontSize: 12 },
    },
    grid: { left: 10, right: 10, top: 22, bottom: names.some((n) => n.length > 5) ? 40 : 28 },
    xAxis: {
      type: 'category',
      data: names.length ? names : ['暂无数据'],
      axisLabel: { color: '#86909c', fontSize: 10, rotate: names.length > 6 ? 28 : 0 },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.12)' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#86909c', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
    },
    series: [
      {
        type: 'bar',
        data: names.length ? vals : [0],
        barMaxWidth: 28,
        itemStyle: {
          color: '#d4537e',
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
  }
})

const singleOutcome = ref(null)
const tenResults = ref([])
const jackpotOverlay = ref(false)
const jackpotOverlayText = ref('')

const pointsWallet = usePointsWalletStore()

function poolStockHint(sr) {
  if (sr === -1) return '不限量'
  return `剩余 ${sr}`
}

function poolStockScarce(sr) {
  return sr !== -1 && sr > 0 && sr <= 40
}

function formatOutcome(row) {
  if (!row) return ''
  const detail = row.rewardDetail ? String(row.rewardDetail).trim() : ''
  if (detail) {
    const name = row.prizeName ? String(row.prizeName).trim() : '神秘大奖'
    return `${name}：${detail}`
  }
  if (row.prizeName) return row.prizeName
  return '谢谢参与'
}

function formatDrawTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function loadInfo(opts = {}) {
  const silent = opts.silent === true
  const activityId = opts.activityId ?? selectedActivityId.value
  if (!silent) loading.value = true
  try {
    const res = await getLotteryInfo(activityId)
    if (res.code === 0 && res.data) {
      Object.assign(info, res.data)
      if (res.data.activityId != null) {
        selectedActivityId.value = res.data.activityId
      }
    }
  } catch {
    // request.js 已对 HTTP 错误弹出提示；此处避免 mounted 钩子未捕获 Promise 拒绝
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadHistoryRecords() {
  historyLoading.value = true
  try {
    const params = {
      pageNum: historyPage.value,
      pageSize: historyPageSize.value,
    }
    if (selectedActivityId.value != null) {
      params.activityId = selectedActivityId.value
    }
    const res = await getLotteryRecords(params)
    if (res.code === 0 && res.data) {
      historyRecords.value = Array.isArray(res.data.records) ? res.data.records : []
      historyTotal.value = Number(res.data.total) || 0
      historyPage.value = Number(res.data.pageNum) || historyPage.value
      historyPageSize.value = Number(res.data.pageSize) || HISTORY_PAGE_SIZE
    }
  } catch {
    /* request 已提示 */
  } finally {
    historyLoading.value = false
  }
}

async function openHistoryDialog() {
  historyDialogVisible.value = true
  historyPage.value = 1
  await loadHistoryRecords()
}

async function onHistoryPageChange(page) {
  historyPage.value = page
  await loadHistoryRecords()
}

function resetRound() {
  phase.value = 'idle'
  singleOutcome.value = null
  tenResults.value = []
  loadInfo({ silent: true })
}

function maybeJackpot(rows) {
  const hit = rows.find((r) => r.jackpot)
  if (hit) {
    jackpotOverlayText.value = formatOutcome(hit)
    jackpotOverlay.value = true
  }
}

async function syncAfterDraw(res) {
  if (typeof res?.data?.balanceAfter === 'number') {
    info.balance = res.data.balanceAfter
  }
  if (typeof res?.data?.pityDrawsSinceJackpot === 'number') {
    info.pityDrawsSinceJackpot = res.data.pityDrawsSinceJackpot
  }
  await pointsWallet.refresh()
  await loadInfo({ silent: true })
  if (historyDialogVisible.value) {
    historyPage.value = 1
    await loadHistoryRecords()
  }
}

async function onSingle() {
  const bal = Number(info.balance ?? 0)
  if (bal < costPer.value) {
    ElMessage.warning(`积分不足，单抽需要 ${costPer.value} 积分`)
    return
  }
  busy.value = true
  phase.value = 'single_shuffle'
  singleOutcome.value = null
  try {
    const [res] = await Promise.all([
      lotteryDraw(1, selectedActivityId.value),
      delay(SHUFFLE_MIN_MS),
    ])
    if (res.code !== 0 || !res.data?.results?.length) {
      phase.value = 'idle'
      return
    }
    singleOutcome.value = res.data.results[0]
    await syncAfterDraw(res)
    phase.value = 'single_result'
    const text = formatOutcome(singleOutcome.value)
    if (singleOutcome.value?.rewardDetail || singleOutcome.value?.grantPoints > 0) {
      ElMessage.success(`恭喜获得：${text}`)
    }
    if (singleOutcome.value?.jackpot) {
      jackpotOverlayText.value = text
      jackpotOverlay.value = true
    }
  } finally {
    busy.value = false
  }
}

async function onTen() {
  const bal = Number(info.balance ?? 0)
  if (bal < tenCost.value) {
    ElMessage.warning(`积分不足，十连需要 ${tenCost.value} 积分`)
    return
  }
  busy.value = true
  phase.value = 'ten_shuffle'
  tenResults.value = []
  try {
    const [res] = await Promise.all([
      lotteryDraw(10, selectedActivityId.value),
      delay(SHUFFLE_MIN_MS + 500),
    ])
    if (res.code !== 0 || !res.data?.results?.length) {
      phase.value = 'idle'
      return
    }
    tenResults.value = res.data.results
    await syncAfterDraw(res)
    phase.value = 'ten_result'
    setTimeout(() => maybeJackpot(res.data.results), 300)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadActivities()
    await loadInfo()
  } finally {
    loading.value = false
  }
})
