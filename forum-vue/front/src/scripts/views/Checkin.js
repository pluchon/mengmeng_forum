import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  doCheckin,
  getCheckinInfo,
  getCheckinLog,
  getCheckinMonth,
  makeupCheckin,
} from '@/api/checkin'
import { useCheckinSnapshotStore } from '@/stores/checkinSnapshot'
import { apiErrorCode, unwrapPageRecords } from '@/utils/apiData'
import { clientOssUrl } from '@/utils/clientOss'
import iconPrevUrl from '@/assets/svg/后退.svg?url'
import iconNextUrl from '@/assets/svg/前进.svg?url'
import iconLogUrl from '@/assets/svg/查看签到.svg?url'

const WEEKDAY_LABELS = ['日', '一', '二', '三', '四', '五', '六']

function pad2(n) {
  return String(n).padStart(2, '0')
}

function toDateKey(d) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

export function useCheckin() {
  const checkinSnapshotStore = useCheckinSnapshotStore()
  const heroImageUrl = clientOssUrl('checkin.webp')

  const loading = ref(true)
  const submitting = ref(false)
  const makeupSubmitting = ref(false)
  const makeupConfirmVisible = ref(false)
  const status = ref(null)
  const monthData = ref(null)
  // 兜底数据和真实数据长得一样，画出来是一排 0，用户会以为自己整月没签到
  const monthLoadFailed = ref(false)
  const logLoadFailed = ref(false)
  const viewYear = ref(new Date().getFullYear())
  const viewMonth = ref(new Date().getMonth() + 1)

  const logDrawer = ref(false)
  const logLoading = ref(false)
  const logRows = ref([])
  const logPage = ref(1)
  const logTotal = ref(0)
  const logPageSize = 10

  const calendarTitle = computed(() => `${viewYear.value} 年 ${viewMonth.value} 月`)

  const monthSignedDays = computed(() => monthData.value?.monthSignedDays ?? 0)

  function buildLocalMonthDays(year, month) {
    const daysInMonth = new Date(year, month, 0).getDate()
    const todayKey = toDateKey(new Date())
    const list = []
    for (let day = 1; day <= daysInMonth; day += 1) {
      const date = `${year}-${pad2(month)}-${pad2(day)}`
      list.push({
        date,
        dayNumber: day,
        points: 50,
        signed: false,
        makeup: false,
        surpriseDay: day === 10 || day === 20 || day === 30,
        today: date === todayKey,
      })
    }
    return list
  }

  const calendarCells = computed(() => {
    const apiDays = monthData.value?.days
    const days =
      Array.isArray(apiDays) && apiDays.length
        ? apiDays
        : buildLocalMonthDays(viewYear.value, viewMonth.value)
    const first = new Date(viewYear.value, viewMonth.value - 1, 1)
    const lead = first.getDay()
    const cells = []
    for (let i = 0; i < lead; i += 1) {
      cells.push({ key: `pad-${i}`, empty: true })
    }
    for (const day of days) {
      cells.push({
        key: day.date,
        empty: false,
        ...day,
      })
    }
    return cells
  })

  const weekChartOption = computed(() => {
    let stats = monthData.value?.weeklyStats || []
    if (!stats.length) {
      // 接口未就绪时用本地格子兜出 5 周空柱，避免图表区完全空白
      const weeks = Math.ceil(new Date(viewYear.value, viewMonth.value, 0).getDate() / 7)
      stats = Array.from({ length: weeks }, (_, i) => ({ weekIndex: i + 1, days: 0 }))
    }
    const labels = stats.map((w) => `第${w.weekIndex}周 · ${w.days}天`)
    const values = stats.map((w) => Number(w.days) || 0)
    const maxVal = Math.max(7, ...values, 1)
    return {
      grid: { left: 8, right: 8, top: 8, bottom: 28, containLabel: false },
      xAxis: {
        type: 'category',
        data: labels,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: {
          color: '#987E89',
          fontSize: 10,
          interval: 0,
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: maxVal,
        splitNumber: 4,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        splitLine: {
          lineStyle: { color: '#F2E4EA', type: 'dashed' },
        },
      },
      series: [
        {
          type: 'bar',
          data: values,
          barWidth: 22,
          itemStyle: {
            borderRadius: [7, 7, 2, 2],
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#EF78A1' },
                { offset: 1, color: '#F4B6CA' },
              ],
            },
          },
        },
      ],
      tooltip: {
        trigger: 'item',
        confine: true,
        appendToBody: false,
        formatter: (params) => {
          const p = Array.isArray(params) ? params[0] : params
          if (!p) return ''
          return `${p.name}<br/>已签 ${p.value} 天`
        },
      },
      // 禁止轴线指示器穿出周图区域
      axisPointer: { show: false },
    }
  })

  // 统一两行结构：标题「连续签到 xx 天」+ 下一行奖励说明 与 15/30 天卡片一致
  const STREAK_CARD_COPY = {
    3: { title: '连续签到 3 天', subtitle: '萌星辉 + 100' },
    7: { title: '连续签到 7 天', subtitle: '补签卡 ×3' },
    15: { title: '连续签到 15 天', subtitle: '萌星辉 + 300' },
    30: { title: '连续签到 30 天', subtitle: '萌星辉 + 500' },
  }

  function normalizeStreakCard(item) {
    const days = Number(item?.streakDays) || 0
    const copy = STREAK_CARD_COPY[days]
    if (!copy) return item
    const title = String(item.title || '')
    const needsTitleFix = !title.includes('连续签到')
    const subtitle = item.subtitle || copy.subtitle
    return {
      ...item,
      title: needsTitleFix ? copy.title : title,
      subtitle,
    }
  }

  const streakRewards = computed(() => {
    const list = status.value?.streakRewards
    if (Array.isArray(list) && list.length) {
      return list.map(normalizeStreakCard)
    }
    const streak = Number(status.value?.streakDays) || 0
    return Object.entries(STREAK_CARD_COPY).map(([days, copy]) => {
      const streakDays = Number(days)
      const achieved = streak >= streakDays
      return {
        streakDays,
        title: copy.title,
        subtitle: copy.subtitle,
        rewardType: 'MIXED',
        achieved,
        daysLeft: achieved ? 0 : streakDays - streak,
      }
    })
  })

  async function loadMonth() {
    try {
      const res = await getCheckinMonth({ year: viewYear.value, month: viewMonth.value })
      if (res.code === 0 && res.data) {
        monthData.value = res.data
        monthLoadFailed.value = false
        return
      }
    } catch {
      // 后端未重启或接口失败时走本地月历兜底
    }
    monthLoadFailed.value = true
    monthData.value = {
      year: viewYear.value,
      month: viewMonth.value,
      monthSignedDays: 0,
      days: buildLocalMonthDays(viewYear.value, viewMonth.value),
      weeklyStats: [],
    }
  }

  async function loadStatus() {
    try {
      const res = await getCheckinInfo()
      if (res.code === 0) {
        status.value = res.data
        checkinSnapshotStore.applyFromInfo(res.data)
      }
    } catch {
      // 失败原因由响应拦截器统一提示；这里不能抛出去，
      // 否则会连累 loadEntry / 签到成功后的刷新一起中断
    }
  }

  async function loadEntry() {
    loading.value = true
    try {
      await Promise.all([loadStatus(), loadMonth()])
    } finally {
      loading.value = false
    }
  }

  watch([viewYear, viewMonth], () => {
    loadMonth()
  })

  onMounted(() => {
    loadEntry()
  })

  function prevMonth() {
    if (viewMonth.value <= 1) {
      viewYear.value -= 1
      viewMonth.value = 12
    } else {
      viewMonth.value -= 1
    }
  }

  function nextMonth() {
    if (viewMonth.value >= 12) {
      viewYear.value += 1
      viewMonth.value = 1
    } else {
      viewMonth.value += 1
    }
  }

  async function handleCheckin() {
    if (status.value?.todaySigned) {
      ElMessage.warning('今日已签到，请明天再来')
      return
    }
    if (submitting.value) return
    submitting.value = true
    try {
      const res = await doCheckin()
      if (res.code === 0) {
        const d = res.data
        ElMessage.success(`签到成功！获得 ${d.todayPoints ?? 0} 萌币`)
        if (d.bonusDescription) {
          ElMessage.success(d.bonusDescription)
        }
        if (d.surpriseLabel) {
          ElMessage.success(`惊喜奖励：${d.surpriseLabel}`)
        }
        await Promise.all([loadStatus(), loadMonth()])
      }
    } catch (e) {
      if (apiErrorCode(e) === 1129) {
        ElMessage.warning(e.message || '今日已签到, 请明天再来')
        if (status.value) status.value = { ...status.value, todaySigned: true }
      }
      // 网络中断时响应可能丢了但服务端已经签上，回读一次真实状态，
      // 否则积分和连签天数会一直停在旧值
      await Promise.all([loadStatus(), loadMonth()])
    } finally {
      submitting.value = false
    }
  }

  function openMakeupConfirm() {
    const cards = status.value?.makeupCardCount ?? 0
    if (cards <= 0) {
      ElMessage.warning('补签卡不足')
      return
    }
    if (makeupSubmitting.value) return
    makeupConfirmVisible.value = true
  }

  async function confirmMakeup() {
    if (makeupSubmitting.value) return
    makeupSubmitting.value = true
    try {
      const res = await makeupCheckin()
      if (res.code === 0) {
        const d = res.data
        const dateText = formatMakeupDate(d?.lastCheckin)
        ElMessage.success(
          dateText
            ? `已补签 ${dateText}，当前连续 ${d.streakDays ?? 0} 天`
            : `补签成功，当前连续 ${d.streakDays ?? 0} 天`,
        )
        if (d.bonusDescription) ElMessage.success(d.bonusDescription)
        makeupConfirmVisible.value = false
        await Promise.all([loadStatus(), loadMonth()])
      }
    } catch {
      // 失败时服务端可能已经扣了卡并补签成功，只是响应没回来。
      // 不刷新的话用户看到的还是旧卡数，再点一次会补掉下一个漏签日、又扣一张
      await Promise.all([loadStatus(), loadMonth()])
    } finally {
      makeupSubmitting.value = false
    }
  }

  function formatMakeupDate(value) {
    if (!value) return ''
    if (typeof value === 'string') {
      return value.slice(0, 10)
    }
    try {
      const d = new Date(value)
      if (Number.isNaN(d.getTime())) return ''
      return toDateKey(d)
    } catch {
      return ''
    }
  }

  async function openLogDrawer() {
    logDrawer.value = true
    logPage.value = 1
    await fetchLogPage(1)
  }

  async function fetchLogPage(page) {
    logLoading.value = true
    try {
      const res = await getCheckinLog({ pageNum: page, pageSize: logPageSize })
      if (res.code === 0) {
        const data = res.data
        logRows.value = unwrapPageRecords(data)
        logTotal.value = data?.total ?? logRows.value.length
        logPage.value = page
        logLoadFailed.value = false
      }
    } catch {
      // 失败要清空：留着上一页的数据配新页码，比空列表更容易误导
      logRows.value = []
      logTotal.value = 0
      logLoadFailed.value = true
    } finally {
      logLoading.value = false
    }
  }

  function onLogPageChange(p) {
    fetchLogPage(p)
  }

  function formatPoints(n) {
    const v = Number(n) || 0
    return v.toLocaleString('zh-CN')
  }

  function isFutureDay(dateStr) {
    if (!dateStr) return false
    const today = new Date()
    return dateStr > toDateKey(today)
  }

  return {
    WEEKDAY_LABELS,
    calendarCells,
    calendarTitle,
    formatPoints,
    handleCheckin,
    openMakeupConfirm,
    confirmMakeup,
    heroImageUrl,
    iconLogUrl,
    iconNextUrl,
    iconPrevUrl,
    isFutureDay,
    loading,
    logDrawer,
    logLoading,
    logPage,
    logPageSize,
    logRows,
    logTotal,
    makeupConfirmVisible,
    makeupSubmitting,
    monthSignedDays,
    nextMonth,
    onLogPageChange,
    openLogDrawer,
    prevMonth,
    status,
    streakRewards,
    submitting,
    weekChartOption,
    monthLoadFailed,
    logLoadFailed,
  }
}
