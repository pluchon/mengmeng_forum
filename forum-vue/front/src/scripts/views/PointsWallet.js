import { computed, onMounted, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Calendar,
  CaretBottom,
  CaretTop,
  EditPen,
  MagicStick,
  Present,
  Refresh,
  ShoppingCart,
  Star,
  Tickets,
  Trophy,
} from '@element-plus/icons-vue'
import { MENG_COIN_CENTER_WEBP_URL as mengCoinCenterImageUrl } from '@/utils/clientOss'
import {
  claimMengCoinMilestone,
  getMengCoinCenterChart,
  getMengCoinCenterLog,
  getMengCoinCenterOverview,
  getMengCoinCenterTrend,
} from '@/api/points'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'

const SOURCE_OPTIONS = [
  { value: 0, label: '签到' },
  { value: 1, label: '连续签到奖励' },
  { value: 2, label: '表情商城' },
  { value: 3, label: '退款回补' },
  { value: 4, label: '积分抽奖' },
  { value: 5, label: '积分抽奖中奖' },
  { value: 6, label: '注册赠送' },
  { value: 7, label: '会员订阅' },
  { value: 9, label: '看板娘陪伴' },
  { value: 10, label: 'AI 生图' },
  { value: 14, label: '签到惊喜奖励' },
  { value: 15, label: '萌币里程碑奖励' },
  { value: 16, label: '幸运收集册奖励' },
  { value: 99, label: '管理员调整' },
]

const TIME_RANGE_OPTIONS = [
  { value: 'LAST_7_DAYS', label: '近 7 天' },
  { value: 'LAST_30_DAYS', label: '最近 30 天' },
  { value: 'THIS_MONTH', label: '本月' },
]

const EMPTY_OVERVIEW = {
  balance: 0,
  monthIncome: 0,
  monthExpense: 0,
  trendStartDate: '',
  trendEndDate: '',
  hasPreviousWeek: false,
  hasNextWeek: false,
  trendWeekComplete: false,
  dailyTrend: [],
  cumulativeIncome: 0,
  milestones: [],
  incomeSources: [],
  expenseSources: [],
}

function formatNumber(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value) || 0)
}

function formatCompact(value) {
  const amount = Number(value) || 0
  return amount >= 1000 ? `${amount / 1000}K` : String(amount)
}

function formatTime(value) {
  const date = parseForumDateTime(value)
  if (!date) return '—'
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function sourceLabel(row) {
  if (row?.remark) return row.remark
  return SOURCE_OPTIONS.find((item) => item.value === Number(row?.sourceType))?.label || '其他来源'
}

function sourceIcon(sourceType) {
  const type = Number(sourceType)
  if (type === 0 || type === 1 || type === 14) return Calendar
  if (type === 2 || type === 7 || type === 9 || type === 10) return ShoppingCart
  if (type === 4 || type === 5) return Trophy
  if (type === 11 || type === 12 || type === 13) return Star
  if (type === 15 || type === 16) return Present
  return EditPen
}

function buildTrendOption(rows) {
  const days = rows.map((row) => String(row.day || '').slice(5).replace('-', '/'))
  return {
    animationDuration: 650,
    color: ['#8d6ae9', '#f06e97'],
    grid: { left: 38, right: 18, top: 22, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,.96)',
      borderColor: '#eadfec',
      textStyle: { color: '#5d4a64', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: days,
      axisLine: { lineStyle: { color: '#e7dfe8' } },
      axisLabel: { color: '#a99aa9', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#b5a8b7', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f1ebf2' } },
    },
    series: [
      {
        name: '获得',
        type: 'bar',
        data: rows.map((row) => Number(row.inTotal) || 0),
      },
      {
        name: '消耗',
        type: 'bar',
        data: rows.map((row) => Number(row.outTotal) || 0),
      },
    ],
  }
}

function buildLedgerChartOption(data = {}) {
  const incomeTotal = Math.max(0, Number(data.incomeTotal) || 0)
  const expenseTotal = Math.max(0, Number(data.expenseTotal) || 0)
  const incomePalette = ['#2bb673', '#4ecf8a', '#7dd9a4', '#a6e4bf', '#c8efd7', '#5cbc8a']
  const expensePalette = ['#e85b7f', '#f07896', '#f59aaf', '#f8b6c5', '#d9486f', '#ef8fa8']

  const incomeChildren = (data.incomeSources || [])
    .map((item, index) => ({
      name: item.sourceLabel || '其他来源',
      value: Math.max(0, Number(item.amount) || 0),
      itemStyle: { color: incomePalette[index % incomePalette.length] },
    }))
    .filter((item) => item.value > 0)
  const expenseChildren = (data.expenseSources || [])
    .map((item, index) => ({
      name: item.sourceLabel || '其他消耗',
      value: Math.max(0, Number(item.amount) || 0),
      itemStyle: { color: expensePalette[index % expensePalette.length] },
    }))
    .filter((item) => item.value > 0)

  const sunburstData = []
  if (incomeTotal > 0 || incomeChildren.length > 0) {
    sunburstData.push({
      name: '获得',
      value: incomeTotal || incomeChildren.reduce((sum, item) => sum + item.value, 0),
      itemStyle: { color: '#2bb673' },
      children: incomeChildren.length
        ? incomeChildren
        : [{ name: '获得合计', value: incomeTotal, itemStyle: { color: '#4ecf8a' } }],
    })
  }
  if (expenseTotal > 0 || expenseChildren.length > 0) {
    sunburstData.push({
      name: '消耗',
      value: expenseTotal || expenseChildren.reduce((sum, item) => sum + item.value, 0),
      itemStyle: { color: '#e85b7f' },
      children: expenseChildren.length
        ? expenseChildren
        : [{ name: '消耗合计', value: expenseTotal, itemStyle: { color: '#f07896' } }],
    })
  }

  return {
    animationDuration: 560,
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const name = params?.name || ''
        const value = Number(params?.value) || 0
        const percent = params?.percent == null ? '' : ` · ${params.percent}%`
        const tree = Array.isArray(params?.treePathInfo)
          ? params.treePathInfo.map((node) => node.name).filter(Boolean).join(' / ')
          : name
        return `${tree || name}<br/>${value} 萌币${percent}`
      },
    },
    series: [
      {
        type: 'sunburst',
        radius: [0, '82%'],
        center: ['42%', '50%'],
        sort: undefined,
        nodeClick: false,
        emphasis: { focus: 'ancestor' },
        levels: [
          {},
          {
            r0: '18%',
            r: '48%',
            itemStyle: { borderWidth: 2, borderColor: '#fff' },
            label: {
              rotate: 'tangential',
              color: '#fff',
              fontSize: 13,
              fontWeight: 700,
              minAngle: 12,
            },
          },
          {
            r0: '50%',
            r: '78%',
            itemStyle: { borderWidth: 2, borderColor: '#fff' },
            label: {
              rotate: 'radial',
              color: '#5d4a64',
              fontSize: 11,
              minAngle: 8,
            },
          },
        ],
        data: sunburstData,
      },
    ],
    graphic: [
      {
        type: 'group',
        right: 18,
        top: 'middle',
        children: [
          {
            type: 'text',
            style: {
              text: `获得 ${formatNumber(incomeTotal)}`,
              fill: '#2bb673',
              fontSize: 13,
              fontWeight: 700,
            },
            top: -18,
          },
          {
            type: 'text',
            style: {
              text: `消耗 ${formatNumber(expenseTotal)}`,
              fill: '#e85b7f',
              fontSize: 13,
              fontWeight: 700,
            },
            top: 8,
          },
        ],
      },
    ],
  }
}

export function usePointsWallet() {
  const walletStore = usePointsWalletStore()
  const overview = ref({ ...EMPTY_OVERVIEW })
  const overviewLoading = ref(false)
  const pageError = ref('')
  const claimingCode = ref('')
  const trendOption = shallowRef(buildTrendOption([]))
  const trendWeekOffset = ref(0)
  const trendCache = new Map()
  const logLoading = ref(false)
  const logRows = ref([])
  const logTotal = ref(0)
  const chartVisible = ref(false)
  const chartLoading = ref(false)
  const ledgerChartOption = shallowRef(buildLedgerChartOption())
  const chartIsEmpty = ref(true)
  const logQuery = ref({ pageNum: 1, pageSize: 10, direction: 'ALL', sourceType: undefined, timeRange: 'LAST_30_DAYS' })

  const trendTitle = computed(() => {
    const month = Number(overview.value.trendStartDate?.slice(5, 7))
    return `${month || new Date().getMonth() + 1} 月萌币足迹`
  })
  const trendDateRange = computed(() => {
    const start = overview.value.trendStartDate?.slice(5).replace('-', '.')
    const end = overview.value.trendEndDate?.slice(5).replace('-', '.')
    return start && end ? `${start} — ${end}` : ''
  })
  const primaryIncomeSource = computed(() => overview.value.incomeSources?.[0] || null)
  const primaryExpenseSource = computed(() => overview.value.expenseSources?.[0] || null)
  const trendHasData = computed(() => overview.value.dailyTrend?.some((item) => (
    Number(item.inTotal) > 0 || Number(item.outTotal) > 0
  )))
  const trendIsEmpty = computed(() => overview.value.trendWeekComplete && !trendHasData.value)
  const milestoneProgress = computed(() => {
    const threshold = Number(overview.value.milestones?.at(-1)?.threshold) || 10000
    return Math.min(100, Math.round((Number(overview.value.cumulativeIncome) || 0) / threshold * 100))
  })

  async function loadOverview() {
    overviewLoading.value = true
    pageError.value = ''
    try {
      const response = await getMengCoinCenterOverview({ weekOffset: trendWeekOffset.value })
      if (response.code !== 0) {
        throw new Error(response.message || '萌币中心加载失败')
      }
      overview.value = { ...EMPTY_OVERVIEW, ...(response.data || {}) }
      trendCache.set(trendWeekOffset.value, {
        trendStartDate: overview.value.trendStartDate,
        trendEndDate: overview.value.trendEndDate,
        hasPreviousWeek: overview.value.hasPreviousWeek,
        hasNextWeek: overview.value.hasNextWeek,
        trendWeekComplete: overview.value.trendWeekComplete,
        dailyTrend: overview.value.dailyTrend,
      })
      trendOption.value = buildTrendOption(overview.value.dailyTrend || [])
    } catch (error) {
      pageError.value = error?.message || '萌币中心加载失败，请稍后重试'
    } finally {
      overviewLoading.value = false
    }
  }

  function changeTrendWeek(direction) {
    if (direction < 0 && !overview.value.hasPreviousWeek) {
      ElMessage.info('已经是注册所在的首个自然周了')
      return
    }
    if (direction > 0 && !overview.value.hasNextWeek) {
      ElMessage.info('已经是当前周了')
      return
    }
    const previousOffset = trendWeekOffset.value
    trendWeekOffset.value += direction
    return loadTrend(previousOffset)
  }

  function applyTrend(trend) {
    overview.value = { ...overview.value, ...(trend || {}) }
    trendOption.value = buildTrendOption(overview.value.dailyTrend || [])
  }

  async function loadTrend(previousOffset) {
    const cached = trendCache.get(trendWeekOffset.value)
    if (cached) {
      applyTrend(cached)
      return
    }
    try {
      const response = await getMengCoinCenterTrend({ weekOffset: trendWeekOffset.value })
      if (response.code !== 0) throw new Error(response.message || '萌币足迹加载失败')
      trendCache.set(trendWeekOffset.value, response.data || {})
      applyTrend(response.data)
    } catch (error) {
      if (Number.isInteger(previousOffset)) trendWeekOffset.value = previousOffset
      ElMessage.error(error?.message || '萌币足迹加载失败')
    }
  }

  async function loadLog() {
    logLoading.value = true
    try {
      const params = { ...logQuery.value }
      if (params.sourceType == null) delete params.sourceType
      const response = await getMengCoinCenterLog(params)
      if (response.code !== 0) {
        throw new Error(response.message || '萌币流水加载失败')
      }
      logRows.value = unwrapPageRecords(response.data)
      logTotal.value = Number(response.data?.total) || 0
    } catch (error) {
      ElMessage.error(error?.message || '萌币流水加载失败')
      logRows.value = []
      logTotal.value = 0
    } finally {
      logLoading.value = false
    }
  }

  async function loadChart() {
    if (!chartVisible.value) return
    chartLoading.value = true
    try {
      const params = { ...logQuery.value }
      delete params.pageNum
      delete params.pageSize
      if (params.sourceType == null) delete params.sourceType
      const response = await getMengCoinCenterChart(params)
      if (response.code !== 0) throw new Error(response.message || '萌币图表加载失败')
      const chartData = response.data || {}
      chartIsEmpty.value = Number(chartData.incomeTotal) <= 0 && Number(chartData.expenseTotal) <= 0
      ledgerChartOption.value = buildLedgerChartOption(chartData)
    } catch (error) {
      ElMessage.error(error?.message || '萌币图表加载失败')
    } finally {
      chartLoading.value = false
    }
  }

  function reloadLog() {
    logQuery.value.pageNum = 1
    return loadLog()
  }

  function resetLogQuery() {
    logQuery.value = { pageNum: 1, pageSize: 10, direction: 'ALL', sourceType: undefined, timeRange: 'LAST_30_DAYS' }
    return loadLog()
  }

  function toggleChart() {
    chartVisible.value = true
    // 等弹窗完成布局后再拉数+渲染，避免 echarts 宽高为 0
    requestAnimationFrame(() => {
      setTimeout(() => {
        loadChart()
      }, 50)
    })
  }

  function milestoneActionLabel(item) {
    if (item.status === 'CLAIMED') return `已领 +${item.reward}`
    if (item.status === 'CLAIMABLE') return `领取 +${item.reward}`
    return `待领 +${item.reward}`
  }

  async function claimMilestone(item) {
    if (item.status !== 'CLAIMABLE' || claimingCode.value) return
    claimingCode.value = item.code
    try {
      const response = await claimMengCoinMilestone(item.code)
      if (response.code !== 0) {
        throw new Error(response.message || '领取失败')
      }
      ElMessage.success(`已领取 ${item.reward} 萌币`)
      trendCache.clear()
      trendWeekOffset.value = 0
      await Promise.all([walletStore.refresh(), loadOverview(), reloadLog()])
    } catch (error) {
      ElMessage.error(error?.message || '领取失败，请稍后重试')
    } finally {
      claimingCode.value = ''
    }
  }

  onMounted(async () => {
    await Promise.all([loadOverview(), loadLog(), walletStore.refresh()])
  })

  return {
    ArrowLeft,
    ArrowRight,
    CaretBottom,
    CaretTop,
    MagicStick,
    Refresh,
    SOURCE_OPTIONS,
    TIME_RANGE_OPTIONS,
    Tickets,
    claimMilestone,
    claimingCode,
    changeTrendWeek,
    chartLoading,
    chartIsEmpty,
    chartVisible,
    formatCompact,
    formatNumber,
    formatTime,
    loadLog,
    loadOverview,
    ledgerChartOption,
    logLoading,
    logQuery,
    logRows,
    logTotal,
    mengCoinCenterImageUrl,
    milestoneProgress,
    milestoneActionLabel,
    overview,
    overviewLoading,
    pageError,
    primaryExpenseSource,
    primaryIncomeSource,
    reloadLog,
    resetLogQuery,
    sourceIcon,
    sourceLabel,
    sourceOptions: SOURCE_OPTIONS,
    timeRangeOptions: TIME_RANGE_OPTIONS,
    trendDateRange,
    trendHasData,
    trendIsEmpty,
    trendOption,
    trendTitle,
    toggleChart,
  }
}
