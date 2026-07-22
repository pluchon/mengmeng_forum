import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { doCheckin, getCheckinInfo, getCheckinLog, getCheckinRule } from '@/api/checkin'
import { useCheckinSnapshotStore } from '@/stores/checkinSnapshot'
import { unwrapPageRecords } from '@/utils/apiData'
import { clientOssUrl } from '@/utils/clientOss'
import iconPrevUrl from '@/assets/svg/后退.svg?url'
import iconNextUrl from '@/assets/svg/前进.svg?url'
import iconLogUrl from '@/assets/svg/查看签到.svg?url'
import signedTodayIconUrl from '@/assets/svg/日历中的已签到.svg?url'

const REWARD_TIP_DISMISS_KEY = 'checkin_reward_tip_dismissed'

export function useCheckin() {
  const checkinSnapshotStore = useCheckinSnapshotStore()
  const heroImageUrl = clientOssUrl('checkin.webp')

  const loading = ref(true)
  const submitting = ref(false)
  const status = ref(null)
  const ruleMonth = ref(null)
  const calendarDate = ref(new Date())
  const signedDates = ref(new Set())
  const logDrawer = ref(false)
  const logLoading = ref(false)
  const logRows = ref([])
  const logPage = ref(1)
  const logTotal = ref(0)
  const logPageSize = 10
  const rewardTipVisible = ref(
    typeof localStorage !== 'undefined' && localStorage.getItem(REWARD_TIP_DISMISS_KEY) === '1'
      ? false
      : true,
  )

  const pointsByDay = computed(() => {
    const map = new Map()
    const days = ruleMonth.value?.days
    if (!Array.isArray(days)) return map
    for (const d of days) {
      if (d?.dayNumber != null) map.set(Number(d.dayNumber), Number(d.points) || 0)
    }
    return map
  })

  async function loadAllSignedDates() {
    const set = new Set()
    let page = 1
    const pageSize = 100
    for (;;) {
      const res = await getCheckinLog({ pageNum: page, pageSize })
      if (res.code !== 0) break
      const data = res.data
      const rows = unwrapPageRecords(data)
      for (const r of rows) {
        const d = (r.checkinDate || '').slice(0, 10)
        if (d) set.add(d)
      }
      if (!data?.hasNextPage || rows.length === 0) break
      page += 1
      if (page > 40) break
    }
    signedDates.value = set
  }

  async function loadRuleForPanel(date) {
    const m = date.getMonth() + 1
    const res = await getCheckinRule({ month: m })
    if (res.code === 0) ruleMonth.value = res.data
  }

  async function loadEntry() {
    loading.value = true
    try {
      const [infoRes] = await Promise.all([
        getCheckinInfo(),
        loadRuleForPanel(calendarDate.value),
        loadAllSignedDates(),
      ])
      if (infoRes.code === 0) {
        status.value = infoRes.data
        checkinSnapshotStore.applyFromInfo(infoRes.data)
      }
    } finally {
      loading.value = false
    }
  }

  /** Element Plus Calendar 不 emit panel-change；按面板年月拉取当月规则（避免与 initial load 抢时用 year-month 作 watch 源） */
  watch(
    () => {
      const d = calendarDate.value
      return d ? `${d.getFullYear()}-${d.getMonth()}` : ''
    },
    () => {
      const d = calendarDate.value
      if (d) loadRuleForPanel(d)
    },
  )

  onMounted(() => {
    loadEntry()
  })

  const calendarTitle = computed(() => {
    const d = calendarDate.value
    if (!d) return ''
    return `${d.getFullYear()}年${d.getMonth() + 1}月`
  })

  function prevMonth() {
    const cur = calendarDate.value
    calendarDate.value = new Date(cur.getFullYear(), cur.getMonth() - 1, 1)
  }

  function nextMonth() {
    const cur = calendarDate.value
    calendarDate.value = new Date(cur.getFullYear(), cur.getMonth() + 1, 1)
  }

  function cellPointsForDay(dayStr) {
    if (!dayStr || !ruleMonth.value?.month) return null
    const parts = dayStr.split('-').map(Number)
    if (parts.length < 3) return null
    const [, m, dom] = parts
    if (Number(m) !== Number(ruleMonth.value.month)) return null
    const pts = pointsByDay.value.get(dom)
    return pts == null ? null : pts
  }

  function isSignedDay(dayStr) {
    return dayStr && signedDates.value.has(dayStr)
  }

  function isTodayCell(dayStr) {
    if (!dayStr) return false
    const t = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const key = `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}`
    return dayStr === key
  }

  function showSignedOverlay(data) {
    if (!data?.day) return false
    return isSignedDay(data.day)
  }

  function dismissRewardTip() {
    rewardTipVisible.value = false
    try {
      localStorage.setItem(REWARD_TIP_DISMISS_KEY, '1')
    } catch {
      /* ignore */
    }
  }

  async function handleCheckin() {
    if (status.value?.todaySigned) {
      ElMessage.warning('今日已签到，请明天再来')
      return
    }
    submitting.value = true
    try {
      const res = await doCheckin()
      if (res.code === 0) {
        const d = res.data
        ElMessage.success(`签到成功！获得 ${d.todayPoints ?? 0} 萌币`)
        if (d.bonusPoints > 0) {
          ElMessage.success(`连签奖励 +${d.bonusPoints} 萌币${d.bonusDescription ? `（${d.bonusDescription}）` : ''}`)
        }
        const dayKey = (d.lastCheckin || '').slice(0, 10)
        if (dayKey) {
          const next = new Set(signedDates.value)
          next.add(dayKey)
          signedDates.value = next
        }
        await refreshInfoOnly()
        if (status.value) checkinSnapshotStore.applyFromInfo(status.value)
      }
    } catch (e) {
      if (e?.code === 1129) {
        ElMessage.warning(e.message || '今日已签到, 请明天再来')
        if (status.value) status.value = { ...status.value, todaySigned: true }
      }
    } finally {
      submitting.value = false
    }
  }

  async function refreshInfoOnly() {
    const res = await getCheckinInfo()
    if (res.code === 0) {
      status.value = res.data
      checkinSnapshotStore.applyFromInfo(res.data)
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
      }
    } finally {
      logLoading.value = false
    }
  }

  function onLogPageChange(p) {
    fetchLogPage(p)
  }

  const nextRewardText = computed(() => {
    const s = status.value
    if (!s || s.todaySigned === undefined) return ''
    if (s.nextThreshold == null || s.nextThresholdLeft == null) return '已达成当前连签奖励档位，继续保持哦'
    return `再连续签到 ${s.nextThresholdLeft} 天可领 ${s.nextThresholdBonus ?? 0} 萌币（${s.nextThreshold} 天档）`
  })

  return {
    calendarDate,
    calendarTitle,
    cellPointsForDay,
    dismissRewardTip,
    handleCheckin,
    heroImageUrl,
    iconLogUrl,
    iconNextUrl,
    iconPrevUrl,
    isSignedDay,
    isTodayCell,
    loading,
    logDrawer,
    logLoading,
    logPage,
    logPageSize,
    logRows,
    logTotal,
    nextMonth,
    nextRewardText,
    onLogPageChange,
    openLogDrawer,
    prevMonth,
    rewardTipVisible,
    showSignedOverlay,
    signedTodayIconUrl,
    status,
    submitting,
  }
}
