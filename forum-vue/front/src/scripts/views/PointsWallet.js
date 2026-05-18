import { ref, computed, onMounted, shallowRef } from 'vue'
import {
  Calendar,
  DataLine,
  Filter,
  List,
  Present,
  RefreshLeft,
  ShoppingCart,
  Trophy,
  User,
  ArrowDown,
  Medal,
} from '@element-plus/icons-vue'
import summaryCardBg from '@/assets/images/积分卡片.png'
import iconPrevUrl from '@/assets/svg/后退.svg?url'
import iconNextUrl from '@/assets/svg/前进.svg?url'
import { getPointsDaily, getPointsLog } from '@/api/points'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'

/** 与 forum-demo Constant.POINTS_SOURCE_* 一致 */
const SOURCE_OPTIONS = [
  { value: null, label: '全部来源' },
  { value: 0, label: '签到' },
  { value: 1, label: '连签奖励' },
  { value: 2, label: '商城购买' },
  { value: 3, label: '退款回补' },
  { value: 4, label: '抽奖消耗' },
  { value: 5, label: '抽奖奖励' },
  { value: 6, label: '注册赠送' },
  { value: 7, label: 'VIP订阅' },
  { value: 8, label: '抽奖页彩蛋' },
  { value: 99, label: '管理员调整' },
]

const CHART_TYPES = [
  { id: 'bar', label: '柱状图' },
  { id: 'line', label: '折线图' },
]

function pad2(n) {
  return String(n).padStart(2, '0')
}

function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

function daysFromTodayToMonthStart(year, month) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const monthStart = new Date(year, month - 1, 1)
  const diff = Math.floor((today - monthStart) / 86400000) + 1
  return Math.min(Math.max(diff, 1), 365)
}

function fillMonthSeries(rows, year, month, isLatestMonth) {
  const map = new Map((rows || []).map((r) => [r.day, r]))
  const list = []
  const today = new Date()
  const lastDay = isLatestMonth ? today.getDate() : new Date(year, month, 0).getDate()
  const ym = `${year}-${pad2(month)}`
  for (let d = 1; d <= lastDay; d += 1) {
    const key = `${ym}-${pad2(d)}`
    const hit = map.get(key)
    list.push({
      day: key,
      inTotal: hit?.inTotal ?? 0,
      outTotal: hit?.outTotal ?? 0,
    })
  }
  return list
}

function monthHasActivity(rows) {
  return rows.some((r) => (Number(r.inTotal) || 0) > 0 || (Number(r.outTotal) || 0) > 0)
}

function buildEmptyChartOption() {
  return {
    animation: false,
    title: {
      show: true,
      text: '本月暂无变动',
      left: 'center',
      top: 'center',
      textStyle: { color: '#86909c', fontSize: 14, fontWeight: 600 },
    },
    legend: { show: false },
    grid: { left: 48, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', show: false, data: [] },
    yAxis: { type: 'value', show: false },
    dataZoom: [],
    series: [],
  }
}

function baseChartConfig(rows, chartType) {
  const days = rows.map((r) => r.day.slice(5))
  const inData = rows.map((r) => r.inTotal ?? 0)
  const outData = rows.map((r) => r.outTotal ?? 0)
  const isBar = chartType === 'bar'

  return {
    animation: true,
    animationDuration: 900,
    animationEasing: 'cubicOut',
    animationDurationUpdate: 650,
    animationEasingUpdate: 'cubicOut',
    title: { show: false },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#e5e6eb',
      textStyle: { color: '#1d2129', fontSize: 12 },
      formatter(params) {
        const idx = params[0]?.dataIndex ?? 0
        const fullDay = rows[idx]?.day ?? ''
        let html = `${fullDay}<br/>`
        for (const p of params) {
          html += `${p.marker}${p.seriesName}：${p.value}<br/>`
        }
        return html
      },
    },
    legend: {
      data: ['入账', '消费'],
      bottom: 0,
      textStyle: { color: '#4e5969', fontSize: 12 },
      itemWidth: 10,
      itemHeight: 10,
    },
    grid: {
      left: 48,
      right: 20,
      top: 20,
      bottom: 40,
    },
    xAxis: {
      type: 'category',
      data: days,
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#86909c',
        fontSize: 11,
        rotate: rows.length > 16 ? 35 : 0,
      },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#86909c', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
    },
    dataZoom: [{ type: 'inside', xAxisIndex: 0 }],
    series: [
      {
        name: '入账',
        type: isBar ? 'bar' : 'line',
        data: inData,
        barMaxWidth: isBar ? 14 : undefined,
        smooth: isBar ? false : 0.35,
        symbol: isBar ? undefined : 'circle',
        symbolSize: isBar ? undefined : 6,
        lineStyle: isBar ? undefined : { width: 2.5, color: '#2ea86a' },
        itemStyle: isBar
          ? { color: '#2ea86a', borderRadius: [4, 4, 0, 0] }
          : { color: '#2ea86a' },
        animationDelay(idx) {
          return idx * 18
        },
      },
      {
        name: '消费',
        type: isBar ? 'bar' : 'line',
        data: outData,
        barMaxWidth: isBar ? 14 : undefined,
        smooth: isBar ? false : 0.35,
        symbol: isBar ? undefined : 'circle',
        symbolSize: isBar ? undefined : 6,
        lineStyle: isBar ? undefined : { width: 2.5, color: '#f54568' },
        itemStyle: isBar
          ? { color: '#f54568', borderRadius: [4, 4, 0, 0] }
          : { color: '#f54568' },
        animationDelay(idx) {
          return idx * 18 + 80
        },
      },
    ],
  }
}

function buildChartOption(rows, chartType = 'bar') {
  if (!rows.length || !monthHasActivity(rows)) {
    return buildEmptyChartOption()
  }
  return baseChartConfig(rows, chartType)
}

function formatLogTime(input) {
  const d = parseForumDateTime(input)
  if (!d) return '—'
  const y = d.getFullYear()
  const m = pad2(d.getMonth() + 1)
  const day = pad2(d.getDate())
  const h = pad2(d.getHours())
  const min = pad2(d.getMinutes())
  const sec = pad2(d.getSeconds())
  return `${y}-${m}-${day} ${h}:${min}:${sec}`
}

function logIconMeta(row) {
  const t = Number(row.sourceType)
  if (t === 0 || t === 1) return { icon: Calendar, tone: 'income' }
  if (t === 2) return { icon: ShoppingCart, tone: 'spend' }
  if (t === 3) return { icon: RefreshLeft, tone: 'income' }
  if (t === 4) return { icon: Trophy, tone: 'spend' }
  if (t === 5) return { icon: Present, tone: 'income' }
  if (t === 6) return { icon: User, tone: 'income' }
  if (t === 7) return { icon: Medal, tone: 'spend' }
  if (t === 99) return { icon: Filter, tone: 'neutral' }
  return row.delta >= 0 ? { icon: Present, tone: 'income' } : { icon: ShoppingCart, tone: 'spend' }
}

export function usePointsWallet() {
  const wallet = usePointsWalletStore()

  const dailyLoading = ref(false)
  const dailyRows = ref([])
  const chartMonth = ref(currentYearMonth())
  const chartType = ref('bar')
  const chartOption = shallowRef(null)

  const logLoading = ref(false)
  const logRows = ref([])
  const logPage = ref(1)
  const logPageSize = ref(10)
  const logTotal = ref(0)
  const filterSourceType = ref(null)
  const filterVisible = ref(false)

  const isLatestMonth = computed(() => {
    const now = currentYearMonth()
    return chartMonth.value.year === now.year && chartMonth.value.month === now.month
  })

  const canGoNextMonth = computed(() => !isLatestMonth.value)
  const canGoPrevMonth = computed(() => true)

  const chartMonthKey = computed(() => `${chartMonth.value.year}-${chartMonth.value.month}-${chartType.value}`)

  const chartMonthTitle = computed(() => {
    const { year, month } = chartMonth.value
    return `${year}年${month}月积分变动`
  })

  const filledMonthRows = computed(() =>
    fillMonthSeries(dailyRows.value, chartMonth.value.year, chartMonth.value.month, isLatestMonth.value),
  )

  const periodInTotal = computed(() =>
    filledMonthRows.value.reduce((sum, r) => sum + (Number(r.inTotal) || 0), 0),
  )

  const periodOutTotal = computed(() =>
    filledMonthRows.value.reduce((sum, r) => sum + (Number(r.outTotal) || 0), 0),
  )

  const spendDisplay = computed(() => {
    const n = Number(wallet.totalSpendPoints) || 0
    if (n === 0) return '0'
    return `-${Math.abs(n)}`
  })

  const hasMoreLogs = computed(() => logRows.value.length < logTotal.value)

  function getFilledRowsForChart() {
    const { year, month } = chartMonth.value
    const now = currentYearMonth()
    const isLatest = year === now.year && month === now.month
    return fillMonthSeries(dailyRows.value, year, month, isLatest)
  }

  function rebuildChart() {
    const filled = getFilledRowsForChart()
    const option = buildChartOption(filled, chartType.value)
    chartOption.value = option
  }

  function setChartType(type) {
    if (chartType.value === type || dailyLoading.value) return
    chartType.value = type
    rebuildChart()
  }

  function shiftChartMonth(delta) {
    let { year, month } = chartMonth.value
    month += delta
    if (month > 12) {
      month = 1
      year += 1
    } else if (month < 1) {
      month = 12
      year -= 1
    }
    const now = currentYearMonth()
    if (year > now.year || (year === now.year && month > now.month)) return
    chartMonth.value = { year, month }
    loadDaily()
  }

  function prevChartMonth() {
    if (!canGoPrevMonth.value || dailyLoading.value) return
    shiftChartMonth(-1)
  }

  function nextChartMonth() {
    if (!canGoNextMonth.value || dailyLoading.value) return
    shiftChartMonth(1)
  }

  async function loadDaily() {
    dailyLoading.value = true
    try {
      const { year, month } = chartMonth.value
      const days = daysFromTodayToMonthStart(year, month)
      const ym = `${year}-${pad2(month)}`
      const res = await getPointsDaily({ days })
      if (res.code === 0 && Array.isArray(res.data)) {
        dailyRows.value = res.data.filter((r) => r?.day?.startsWith(ym))
      } else {
        dailyRows.value = []
      }
      rebuildChart()
    } catch {
      dailyRows.value = []
      chartOption.value = buildChartOption([], chartType.value)
    } finally {
      dailyLoading.value = false
    }
  }

  async function loadLog({ reset = false } = {}) {
    if (reset) {
      logPage.value = 1
      logRows.value = []
    }
    logLoading.value = true
    try {
      const params = { pageNum: logPage.value, pageSize: logPageSize.value }
      if (filterSourceType.value != null) params.sourceType = filterSourceType.value
      const res = await getPointsLog(params)
      if (res.code === 0 && res.data) {
        const rows = unwrapPageRecords(res.data)
        logTotal.value = Number(res.data.total) || 0
        if (reset) logRows.value = rows
        else logRows.value = [...logRows.value, ...rows]
      }
    } finally {
      logLoading.value = false
    }
  }

  function loadMoreLogs() {
    if (!hasMoreLogs.value || logLoading.value) return
    logPage.value += 1
    loadLog({ reset: false })
  }

  function applyFilter(sourceType) {
    filterSourceType.value = sourceType
    filterVisible.value = false
    loadLog({ reset: true })
  }

  function logRowClass(row) {
    return row.delta >= 0 ? 'is-income' : 'is-spend'
  }

  onMounted(async () => {
    await wallet.refresh()
    await loadDaily()
    await loadLog({ reset: true })
  })

  return {
    ArrowDown,
    CHART_TYPES,
    DataLine,
    Filter,
    List,
    Medal,
    Present,
    ShoppingCart,
    Trophy,
    User,
    SOURCE_OPTIONS,
    applyFilter,
    canGoNextMonth,
    canGoPrevMonth,
    chartMonthKey,
    chartMonthTitle,
    chartOption,
    chartType,
    dailyLoading,
    filterSourceType,
    filterVisible,
    formatLogTime,
    hasMoreLogs,
    iconNextUrl,
    iconPrevUrl,
    loadMoreLogs,
    logIconMeta,
    logLoading,
    logRowClass,
    logRows,
    nextChartMonth,
    periodInTotal,
    periodOutTotal,
    prevChartMonth,
    setChartType,
    spendDisplay,
    summaryCardBgUrl: summaryCardBg,
    wallet,
  }
}
