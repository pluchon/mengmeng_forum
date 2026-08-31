import { ElMessage } from 'element-plus'
import {
  Present,
  Coin,
  Grid,
  Star,
  ChatDotRound,
  Pointer,
  Calendar,
  CircleCheckFilled,
  EditPen,
  ChatLineRound,
  Share,
  User,
  View,
  CollectionTag,
  Message,
  Monitor,
  Clock,
  VideoCamera,
  Promotion,
  Bell,
} from '@element-plus/icons-vue'
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  getLotteryActivities,
  getLotteryInfo,
  getLotteryRecords,
  getLotteryRecentPublic,
  claimLotteryTask,
  claimLotteryCollectMilestone,
  lotteryDraw,
} from '@/api/lottery'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { useUserStore } from '@/stores/user'
import EChart from '@/components/common/EChart.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import MengXinghuiShop from '@/components/lottery/MengXinghuiShop/MengXinghuiShop.vue'
import MyBagDialog from '@/components/lottery/MyBagDialog/MyBagDialog.vue'
import recordIconUrl from '@/assets/svg/抽奖记录.svg?url'
import mengXinghuiIconUrl from '@/assets/svg/meng_xinghui.svg?url'
import gachaVideoUrl from '@/assets/vedio/gacha-green.mp4'
import {
  LOTTERY_BACKGROUND_WEBP_URL as lotteryBackgroundUrl,
  LOTTERY_PRIZE_WEBP_URL as lotteryPrizeUrl,
  PRIZE_ANWEI_WEBP_URL as prizeAnweiUrl,
  PRIZE_JIFEN_WEBP_URL as prizeJifenUrl,
  PRIZE_SHENMI_WEBP_URL as prizeShenmiUrl,
  PRIZE_THANKS_WEBP_URL as prizeThanksUrl,
  PRO_TIME_TO_TEST_WEBP_URL as prizeVipUrl,
  PRIZE_ZHOUBIAN_WEBP_URL as prizeZhoubianUrl,
} from '@/utils/clientOss'
import {
  COLLECT_TOTAL,
  COLLECT_PAGE_SIZE,
  COLLECT_ICONS,
} from '@/constants/lotteryCollect'

const HISTORY_PAGE_SIZE = 10
const PUBLIC_FEED_PAGE_SIZE = 5
const PUBLIC_FEED_MAX_PAGES = 5
const ACTIVITY_PAGE_SIZE = 5
// 本池任务：每行 2 个，一页 3 行
const TASK_PAGE_SIZE = 6
const PUBLIC_FEED_POLL_MS = 15000

const PRIZE_CHART_COLORS = [
  '#F37CB1', '#5B8DEF', '#F5C16C', '#2BBBAD', '#9B5DE5', '#FF6B6B',
  '#4ECDC4', '#FF9F1C', '#7B68EE', '#00A896', '#EF476F', '#118AB2',
]

const PRIZE_LOCAL_COVERS = [
  { match: /谢谢/, url: prizeThanksUrl },
  { match: /神秘/, url: prizeShenmiUrl },
  { match: /周边/, url: prizeZhoubianUrl },
  { match: /安慰/, url: prizeAnweiUrl },
  { match: /积分/, url: prizeJifenUrl },
  { match: /VIP|体验/, url: prizeVipUrl },
]

const TASK_ICON_MAP = {
  COMMENT_1: ChatDotRound,
  LIKE_3: Pointer,
  CHECKIN_TODAY: Calendar,
  DEMO_POST_1: EditPen,
  DEMO_REPLY_2: ChatLineRound,
  DEMO_SHARE_1: Share,
  DEMO_FOLLOW_1: User,
  DEMO_BROWSE_5: View,
  DEMO_FAV_1: CollectionTag,
  DEMO_MSG_1: Message,
  DEMO_GAME_1: Monitor,
  DEMO_LOGIN_1: Clock,
  DEMO_LIKE_5: Pointer,
  DEMO_COMMENT_3: ChatDotRound,
  DEMO_STAY_10: Clock,
  DEMO_INVITE_1: Promotion,
  DEMO_WATCH_1: VideoCamera,
  DEMO_DAILY_ACTIVE: Star,
}

const router = useRouter()
const loading = ref(true)
const busy = ref(false)
const claimingTaskCode = ref('')

const activityList = ref([])
const selectedActivityId = ref(null)
const activityPage = ref(1)
const activityTotal = ref(0)
const publicFeed = ref([])
const publicFeedPage = ref(1)
const publicFeedTotal = ref(0)
const taskPage = ref(1)
let publicFeedTimer = null

const info = reactive({
  activityId: null,
  title: '',
  description: '',
  costPointsPerDraw: 30,
  balance: 0,
  prizes: [],
  pityDrawsSinceJackpot: 0,
  hardPityThreshold: 50,
  recentDraws: [],
  startTime: null,
  endTime: null,
  voucherBalance: 0,
  voucherOffsetPoints: 30,
  starlightBalance: 0,
  tasks: [],
})

const shopDialogVisible = ref(false)
const bagDialogVisible = ref(false)
const historyDialogVisible = ref(false)
const historyLoading = ref(false)
const historyRecords = ref([])
const historyPage = ref(1)
const historyPageSize = ref(HISTORY_PAGE_SIZE)
const historyTotal = ref(0)
const historyRareOnly = ref(false)
const historyTableRows = computed(() =>
  historyRecords.value.map((r) => ({
    id: r.drawRecordId,
    prizeName: (r.prizeSummary != null && String(r.prizeSummary).trim() !== '' ? String(r.prizeSummary).trim() : null) || '—',
    prizeType: r.prizeType,
    rewardDetail: (r.rewardSummary != null && String(r.rewardSummary).trim() !== '' ? String(r.rewardSummary).trim() : null) || '—',
    costMethod: r.costMethod || '历史消耗未记录',
    createTime: formatDrawTime(r.createTime),
  })),
)

const collectPage = ref(1)

// idle | single_shuffle | single_result | ten_shuffle | ten_result
const phase = ref('idle')
const gachaVideoRef = ref(null)
const gachaCanvasRef = ref(null)
const resultDialogVisible = ref(false)
let gachaVideoDoneResolve = null
let gachaPaintRaf = 0
const GACHA_VIDEO_FALLBACK_MS = 9000
// 源片绿幕约 #308B43；整帧绘制，勿裁切（裁切会切掉猫耳和底座）
const GACHA_KEY = { r: 0x30, g: 0x8b, b: 0x43 }
const GACHA_KEY_THRESH2 = 70 * 70
const GACHA_KEY_SOFT2 = 100 * 100
const GACHA_PAINT_MAX_W = 720
let gachaCanvasSized = false

function stopGachaPaintLoop() {
  if (gachaPaintRaf) {
    cancelAnimationFrame(gachaPaintRaf)
    gachaPaintRaf = 0
  }
}

function ensureGachaCanvasSize(video, canvas) {
  const srcW = video.videoWidth
  const srcH = video.videoHeight
  if (!srcW || !srcH) return false
  const scale = Math.min(1, GACHA_PAINT_MAX_W / srcW)
  const w = Math.max(2, Math.round(srcW * scale))
  const h = Math.max(2, Math.round(srcH * scale))
  if (!gachaCanvasSized || canvas.width !== w || canvas.height !== h) {
    canvas.width = w
    canvas.height = h
    gachaCanvasSized = true
  }
  return true
}

function paintGachaFrame() {
  const video = gachaVideoRef.value
  const canvas = gachaCanvasRef.value
  if (!video || !canvas || !video.videoWidth) return
  if (!ensureGachaCanvasSize(video, canvas)) return
  const w = canvas.width
  const h = canvas.height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  // 整帧绘制（源片已垫边）；勿再裁切，否则底座会被切平
  ctx.drawImage(video, 0, 0, video.videoWidth, video.videoHeight, 0, 0, w, h)
  let frame
  try {
    frame = ctx.getImageData(0, 0, w, h)
  } catch {
    return
  }
  const data = frame.data
  const kr = GACHA_KEY.r
  const kg = GACHA_KEY.g
  const kb = GACHA_KEY.b
  for (let i = 0; i < data.length; i += 4) {
    const dr = data[i] - kr
    const dg = data[i + 1] - kg
    const db = data[i + 2] - kb
    const dist2 = dr * dr + dg * dg + db * db
    if (dist2 <= GACHA_KEY_THRESH2) {
      data[i + 3] = 0
    } else if (dist2 < GACHA_KEY_SOFT2) {
      const t = (dist2 - GACHA_KEY_THRESH2) / (GACHA_KEY_SOFT2 - GACHA_KEY_THRESH2)
      data[i + 3] = Math.round(data[i + 3] * t)
    }
  }
  ctx.putImageData(frame, 0, 0)
}

function startGachaPaintLoop() {
  stopGachaPaintLoop()
  const tick = () => {
    paintGachaFrame()
    const video = gachaVideoRef.value
    if (video && !video.paused && !video.ended) {
      gachaPaintRaf = requestAnimationFrame(tick)
    } else {
      gachaPaintRaf = 0
      paintGachaFrame()
    }
  }
  gachaPaintRaf = requestAnimationFrame(tick)
}

function onGachaVideoLoaded() {
  gachaCanvasSized = false
  paintGachaFrame()
}

function rewindGachaVideo() {
  stopGachaPaintLoop()
  const el = gachaVideoRef.value
  if (!el) return
  try {
    el.pause()
    if (Number.isFinite(el.duration) && el.duration > 0) {
      el.currentTime = 0
    }
  } catch {
    /* ignore */
  }
  nextTick(() => paintGachaFrame())
}

function onGachaVideoEnded() {
  stopGachaPaintLoop()
  paintGachaFrame()
  if (gachaVideoDoneResolve) {
    const done = gachaVideoDoneResolve
    gachaVideoDoneResolve = null
    done()
  }
}

// 抽奖时展示在背景右上角。动画和请求是 Promise.all 并行的，
// 跳过只是提前 resolve 动画那一半，不会打断或重复发请求
const gachaSkippable = computed(() => phase.value === 'single_shuffle' || phase.value === 'ten_shuffle')

function skipGachaAnimation() {
  if (!gachaVideoDoneResolve) return
  const el = gachaVideoRef.value
  if (el) {
    try {
      el.pause()
    } catch {
      // 忽略
    }
  }
  const done = gachaVideoDoneResolve
  gachaVideoDoneResolve = null
  done()
}

function onGachaVideoError() {
  stopGachaPaintLoop()
  if (gachaVideoDoneResolve) {
    const done = gachaVideoDoneResolve
    gachaVideoDoneResolve = null
    done()
  }
}

function playGachaVideo() {
  const el = gachaVideoRef.value
  if (!el) {
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      gachaVideoDoneResolve = null
      clearTimeout(timer)
      stopGachaPaintLoop()
      resolve()
    }
    const timer = setTimeout(finish, GACHA_VIDEO_FALLBACK_MS)
    gachaVideoDoneResolve = finish

    const startPlayback = () => {
      const p = el.play()
      startGachaPaintLoop()
      if (p && typeof p.catch === 'function') {
        p.catch(() => {
          try {
            el.load()
          } catch {
            /* ignore */
          }
          const retry = el.play()
          startGachaPaintLoop()
          if (retry && typeof retry.catch === 'function') {
            retry.catch(() => finish())
          }
        })
      }
    }

    const seekToStartThenPlay = () => {
      const onSeeked = () => {
        el.removeEventListener('seeked', onSeeked)
        startPlayback()
      }
      try {
        el.pause()
        if (el.currentTime > 0.05) {
          el.addEventListener('seeked', onSeeked)
          el.currentTime = 0
        } else {
          startPlayback()
        }
      } catch {
        startPlayback()
      }
    }

    if (el.error) {
      try {
        el.load()
      } catch {
        /* ignore */
      }
    }

    if (el.readyState >= 2) {
      seekToStartThenPlay()
    } else {
      el.addEventListener('loadeddata', seekToStartThenPlay, { once: true })
      try {
        el.load()
      } catch {
        /* ignore */
      }
    }
  })
}

function closeResultDialog() {
  resultDialogVisible.value = false
}

function onResultDialogClosed() {
  resetRound()
}

const costPer = computed(() => info.costPointsPerDraw ?? 30)
const voucherBalance = computed(() => Number(info.voucherBalance ?? 0))
const starlightBalance = computed(() => Number(info.starlightBalance ?? 0))
const voucherOffset = computed(() => Number(info.voucherOffsetPoints ?? costPer.value))

const singleVoucherUsed = computed(() => Math.min(voucherBalance.value, 1))
const tenVoucherUsed = computed(() => Math.min(voucherBalance.value, 10))
const singlePayPoints = computed(() => Math.max(0, costPer.value - singleVoucherUsed.value * costPer.value))
const tenPayPoints = computed(() => Math.max(0, costPer.value * 10 - tenVoucherUsed.value * costPer.value))

const pityCurrent = computed(() => Number(info.pityDrawsSinceJackpot ?? 0))
const pityThreshold = computed(() => Number(info.hardPityThreshold ?? 50))
const pityPercent = computed(() => {
  const th = pityThreshold.value
  if (th <= 0) return 0
  return Math.min(100, Math.round((pityCurrent.value / th) * 100))
})

const currentPoolTag = computed(() => (info.endTime ? '限时' : '常驻'))

const poolScheduleText = computed(() => {
  if (!info.endTime && !info.startTime) return ''
  if (!info.endTime) return ''
  const start = formatPoolTime(info.startTime) || '即日起'
  const end = formatPoolTime(info.endTime) || '—'
  return `${start} ~ ${end}`
})

function formatPoolTime(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const poolDurationLabel = (act) => (act?.endTime ? '限时' : '常驻')

const isHotPool = (act) => {
  const title = String(act?.title || '').trim()
  return title.includes('积分幸运抽')
}

const canSwitchActivity = computed(() => phase.value === 'idle' && !busy.value)

const showPublicFeedPager = computed(() => publicFeedTotal.value > PUBLIC_FEED_PAGE_SIZE)

const allTasks = computed(() => (Array.isArray(info.tasks) ? info.tasks : []))
const taskTotal = computed(() => allTasks.value.length)
const showTaskPager = computed(() => taskTotal.value > TASK_PAGE_SIZE)
const pagedTasks = computed(() => {
  const start = (taskPage.value - 1) * TASK_PAGE_SIZE
  return allTasks.value.slice(start, start + TASK_PAGE_SIZE)
})

const displayPrizes = computed(() =>
  (info.prizes || []).map((p) => ({
    ...p,
    rarity: mapRarity(p),
  })),
)

const collectOwnedIds = computed(() => {
  const ids = info.collect?.ownedIconIds
  return Array.isArray(ids) ? ids.map((n) => Number(n)).filter((n) => n >= 1 && n <= COLLECT_TOTAL) : []
})
const collectOwnedCount = computed(() => {
  const count = Number(info.collect?.ownedCount)
  if (Number.isFinite(count) && count >= 0) return count
  return collectOwnedIds.value.length
})
const collectProgressPercent = computed(() => {
  if (COLLECT_TOTAL <= 0) return 0
  return Math.min(100, Math.round((collectOwnedCount.value / COLLECT_TOTAL) * 100))
})
const showCollectPager = computed(() => COLLECT_TOTAL > COLLECT_PAGE_SIZE)
const pagedCollectIcons = computed(() => {
  const start = (collectPage.value - 1) * COLLECT_PAGE_SIZE
  return COLLECT_ICONS.slice(start, start + COLLECT_PAGE_SIZE)
})
const COLLECT_MILESTONES = computed(() => {
  const rows = info.collect?.milestones
  if (Array.isArray(rows) && rows.length) {
    return rows.map((row) => ({
      at: Number(row.thresholdCount) || 0,
      label: row.label || `${row.thresholdCount}`,
      kind: String(row.rewardType || '').toLowerCase(),
      claimed: !!row.claimed,
      reachable: !!row.reachable,
    }))
  }
  return [
    { at: 10, label: '抵扣券×1', kind: 'voucher', claimed: false, reachable: false },
    { at: 25, label: '积分×50', kind: 'points', claimed: false, reachable: false },
    { at: 50, label: '抵扣券×3', kind: 'voucher', claimed: false, reachable: false },
    { at: 80, label: 'VIP·1天', kind: 'vip', claimed: false, reachable: false },
  ]
})

function mapRarity(prize) {
  if (prize?.jackpot || Number(prize?.prizeType) === 1) return 'SSR'
  if (Number(prize?.prizeType) === 2 || Number(prize?.prizeType) === 5) return 'SR'
  if (Number(prize?.prizeType) === 4 || Number(prize?.prizeType) === 3) return 'R'
  return '普通'
}

function rarityClass(rarity) {
  return `is-${String(rarity || '普通').toLowerCase()}`
}

// prize_type：0谢谢/3安慰=consolation；4积分=normal；2小奖/5VIP=rare；1大奖=grand
function prizeTierClass(prizeType) {
  const t = Number(prizeType)
  if (t === 1) return 'is-grand'
  if (t === 2 || t === 5) return 'is-rare'
  if (t === 4) return 'is-normal'
  return 'is-consolation'
}

function maskFeedNickname(nickname) {
  const raw = String(nickname || '').trim()
  if (!raw) return '用...'
  if (raw.includes('...')) return raw
  return `${raw.charAt(0)}...`
}

function resolvePrizeCover(prize) {
  const name = String(prize?.name || '')
  for (const item of PRIZE_LOCAL_COVERS) {
    if (item.match.test(name)) return item.url
  }
  const path = String(prize?.imagePath || '').trim()
  if (!path) return prizeThanksUrl
  if (/^https?:\/\//i.test(path) || path.startsWith('data:') || path.startsWith('/')) return path
  return path
}

function isPrizeSoldOut(prize) {
  const stock = Number(prize?.stockRemaining)
  return Number.isFinite(stock) && stock === 0
}

function formatProbPercent(value, total) {
  if (!(total > 0)) return '0.00'
  return ((Number(value) || 0) / total * 100).toFixed(2)
}

async function loadActivities() {
  try {
    const res = await getLotteryActivities({
      pageNum: activityPage.value,
      pageSize: ACTIVITY_PAGE_SIZE,
    })
    if (res.code === 0 && res.data) {
      activityList.value = Array.isArray(res.data.records) ? res.data.records : []
      activityTotal.value = Number(res.data.total) || 0
      activityPage.value = Number(res.data.pageNum) || activityPage.value
    }
  } catch {
    // request 已提示
  }
}

async function onActivityPageChange(page) {
  if (!canSwitchActivity.value) return
  activityPage.value = page
  await loadActivities()
}

async function onSelectActivity(id) {
  if (!canSwitchActivity.value) return
  if (selectedActivityId.value === id) return
  selectedActivityId.value = id
  publicFeedPage.value = 1
  taskPage.value = 1
  await loadInfo({ activityId: id, silent: true })
  await loadPublicFeed()
  if (historyDialogVisible.value) {
    historyPage.value = 1
    await loadHistoryRecords()
  }
}

const pieOption = computed(() => {
  // 与实际抽奖一致：售罄档不计入概率，按权重重算；图例按奖品逐项展示
  const prizes = displayPrizes.value.filter((p) => (p.weight ?? 0) > 0 && !isPrizeSoldOut(p))
  const total = prizes.reduce((s, p) => s + (p.weight ?? 0), 0)
  const data = prizes.map((p, index) => ({
    name: p.name || '未命名奖品',
    value: p.weight ?? 0,
    itemStyle: { color: PRIZE_CHART_COLORS[index % PRIZE_CHART_COLORS.length] },
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        return `${params.name}<br/>概率: ${formatProbPercent(params.value, total)}%`
      },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      textStyle: { color: '#4e5969', fontSize: 12 },
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: '2%',
      top: 'middle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 10,
      icon: 'circle',
      textStyle: { color: '#626572', fontSize: 11 },
      formatter: (name) => {
        const row = data.find((d) => d.name === name)
        if (!row) return name
        const short = name.length > 8 ? `${name.slice(0, 8)}…` : name
        return `${short}  ${formatProbPercent(row.value, total)}%`
      },
    },
    series: [
      {
        type: 'pie',
        radius: '52%',
        center: ['30%', '50%'],
        minAngle: 8,
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2,
        },
        data,
      },
    ],
  }
})

const singleOutcome = ref(null)
const tenResults = ref([])

const pointsWallet = usePointsWalletStore()
const userStore = useUserStore()

function isGrandPrizeHit(row) {
  if (!row) return false
  if (row.jackpot) return true
  if (Number(row.prizeType) === 1) return true
  const name = String(row.prizeName || '')
  return name.includes('神秘大奖') || name.includes('头奖')
}

function formatStockText(sr) {
  if (sr === -1) return '∞'
  if (sr == null) return '—'
  return String(sr)
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

// 结果弹窗文案：积分类只显示「积分 xxx」
function formatResultLabel(row) {
  if (!row) return ''
  const type = Number(row.prizeType)
  const name = row.prizeName ? String(row.prizeName).trim() : ''
  const detail = row.rewardDetail ? String(row.rewardDetail).trim() : ''
  const grant = Math.max(0, Number(row.grantPoints) || 0)
  const isPoints = type === 4
    || /随机.*积分|积分/.test(name)
    || /积分\s*[:：]?\s*\d+/.test(detail)
    || /^积分/.test(detail)
  if (isPoints) {
    let pts = grant
    if (!pts) {
      const hit = detail.match(/积分\s*[:：]?\s*(\d+)/)
        || detail.match(/(\d+)\s*积分/)
        || detail.match(/(\d+)/)
      if (hit) pts = Number(hit[1])
    }
    return pts > 0 ? `积分 ${pts}` : '积分'
  }
  return name || '谢谢参与'
}

function notifyActivityPoints(results) {
  const totalPoints = (results || []).reduce(
    (sum, item) => sum + Math.max(0, Number(item?.grantPoints) || 0),
    0,
  )
  if (totalPoints > 0) {
    ElMessage.success(`本次活动额外获得 ${totalPoints} 积分`)
  }
}

function formatDrawTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function taskActionLabel(task) {
  if (task?.status === 'CLAIMED') return '已领取'
  if (task?.status === 'CLAIMABLE') return '领取'
  return '去完成'
}

function taskIcon(taskCode) {
  return TASK_ICON_MAP[taskCode] || Present
}

function onTaskPageChange(page) {
  taskPage.value = page
}

function onCollectPageChange(page) {
  collectPage.value = page
}

function isCollectOwned(id) {
  return collectOwnedIds.value.includes(Number(id))
}

function isMilestoneClaimed(at) {
  const row = COLLECT_MILESTONES.value.find((ms) => ms.at === at)
  if (row) return !!row.claimed
  const claimed = info.collect?.claimedThresholds
  return Array.isArray(claimed) && claimed.includes(at)
}

function milestoneTitle(ms) {
  if (!ms) return ''
  if (ms.claimed || isMilestoneClaimed(ms.at)) return `${ms.at}：${ms.label}（已发放）`
  if (collectOwnedCount.value >= ms.at || ms.reachable) return `${ms.at}：${ms.label}（可达）`
  return `${ms.at}：${ms.label}`
}

function goExchangeShop() {
  shopDialogVisible.value = true
}

function openMyBag() {
  bagDialogVisible.value = true
}

// 背包里用掉的东西会动抵扣券余额，用掉体验卡还会改会员档位，两边都刷一次
async function onBagItemUsed(item) {
  const promises = [loadInfo({ silent: true })]
  if (String(item?.rewardType || '').toUpperCase() === 'VIP_DAYS') {
    promises.push(userStore.fetchUserInfo())
  }
  await Promise.all(promises)
}

function onStarlightBalanceChange(balance) {
  info.starlightBalance = Number(balance) || 0
}

async function onStarlightExchanged() {
  await loadInfo({ silent: true })
}

async function claimReachableCollectMilestones(activityId) {
  const rows = info.collect?.milestones
  if (!Array.isArray(rows) || rows.length === 0) return []
  const grantedLabels = []
  for (const row of rows) {
    if (!row?.reachable || row?.claimed) continue
    const thresholdCount = Number(row.thresholdCount)
    if (!Number.isFinite(thresholdCount) || thresholdCount <= 0) continue
    try {
      const res = await claimLotteryCollectMilestone({
        activityId,
        thresholdCount,
      })
      if (res.code === 0) {
        grantedLabels.push(row.label || `${thresholdCount}`)
      }
    } catch {
      // request.js 已提示；继续尝试其余可达里程
    }
  }
  return grantedLabels
}

async function loadInfo(opts = {}) {
  const silent = opts.silent === true
  const skipMilestoneClaim = opts.skipMilestoneClaim === true
  const activityId = opts.activityId ?? selectedActivityId.value
  if (!silent) loading.value = true
  try {
    const res = await getLotteryInfo(activityId)
    if (res.code === 0 && res.data) {
      Object.assign(info, {
        tasks: [],
        voucherBalance: 0,
        starlightBalance: 0,
        collect: null,
        ...res.data,
      })
      if (res.data.activityId != null) {
        selectedActivityId.value = res.data.activityId
      }
      const totalPages = Math.max(1, Math.ceil((info.tasks?.length || 0) / TASK_PAGE_SIZE))
      if (taskPage.value > totalPages) {
        taskPage.value = totalPages
      }
      // GET /lottery/info 不再自动发奖；进入页面对可达未领里程显式领取
      if (!skipMilestoneClaim) {
        const granted = await claimReachableCollectMilestones(selectedActivityId.value)
        if (granted.length > 0) {
          ElMessage.success(`收集册里程已发放：${granted.join('、')}`)
          await loadInfo({
            silent: true,
            skipMilestoneClaim: true,
            activityId: selectedActivityId.value,
          })
        }
      }
    }
  } catch {
    // request.js 已对 HTTP 错误弹出提示
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadPublicFeed() {
  try {
    const params = {
      pageNum: publicFeedPage.value,
      pageSize: PUBLIC_FEED_PAGE_SIZE,
    }
    if (selectedActivityId.value != null) {
      params.activityId = selectedActivityId.value
    }
    const res = await getLotteryRecentPublic(params)
    if (res.code === 0 && res.data) {
      publicFeed.value = Array.isArray(res.data.records) ? res.data.records : []
      publicFeedTotal.value = Number(res.data.total) || 0
      publicFeedPage.value = Number(res.data.pageNum) || publicFeedPage.value
    }
  } catch {
    // 忽略 poll errors
  }
}

async function onPublicFeedPageChange(page) {
  publicFeedPage.value = page
  await loadPublicFeed()
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
    if (historyRareOnly.value) {
      params.rareOnly = true
    }
    const res = await getLotteryRecords(params)
    if (res.code === 0 && res.data) {
      historyRecords.value = Array.isArray(res.data.records) ? res.data.records : []
      historyTotal.value = Number(res.data.total) || 0
      historyPage.value = Number(res.data.pageNum) || historyPage.value
      historyPageSize.value = Number(res.data.pageSize) || HISTORY_PAGE_SIZE
    }
  } catch {
    // request 已提示
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

// 切筛选要回第一页：留在第 5 页很可能直接是空的
async function onHistoryRareOnlyChange() {
  historyPage.value = 1
  await loadHistoryRecords()
}

function resetRound() {
  phase.value = 'idle'
  singleOutcome.value = null
  tenResults.value = []
  resultDialogVisible.value = false
  nextTick(() => rewindGachaVideo())
  loadInfo({ silent: true })
  loadPublicFeed()
}

async function syncAfterDraw(res) {
  if (typeof res?.data?.balanceAfter === 'number') {
    info.balance = res.data.balanceAfter
  }
  if (typeof res?.data?.pityDrawsSinceJackpot === 'number') {
    info.pityDrawsSinceJackpot = res.data.pityDrawsSinceJackpot
  }
  if (typeof res?.data?.voucherBalanceAfter === 'number') {
    info.voucherBalance = res.data.voucherBalanceAfter
  }
  if (typeof res?.data?.starlightBalanceAfter === 'number') {
    info.starlightBalance = res.data.starlightBalanceAfter
  }
  if (typeof res?.data?.starlightGranted === 'number' && res.data.starlightGranted > 0) {
    ElMessage.success(`获得 ${res.data.starlightGranted} 萌星辉`)
  }
  if (typeof res?.data?.vouchersUsed === 'number' && res.data.vouchersUsed > 0) {
    ElMessage.success(`已使用 ${res.data.vouchersUsed} 张抵扣券`)
  }
  const unlocked = Array.isArray(res?.data?.collectUnlockedIconIds)
    ? res.data.collectUnlockedIconIds.length
    : 0
  if (unlocked > 0) {
    ElMessage.success(`收集册新解锁 ${unlocked} 枚`)
  }
  const milestoneGranted = Array.isArray(res?.data?.collectMilestoneGranted)
    ? res.data.collectMilestoneGranted.filter(Boolean)
    : []
  if (milestoneGranted.length > 0) {
    ElMessage.success(`收集册里程已自动发放：${milestoneGranted.join('、')}`)
  }
  await pointsWallet.refresh()
  await loadInfo({ silent: true })
  await loadPublicFeed()
  if (historyDialogVisible.value) {
    historyPage.value = 1
    await loadHistoryRecords()
  }
}

function estimatedNeed(times) {
  const used = Math.min(voucherBalance.value, times)
  return Math.max(0, costPer.value * times - used * costPer.value)
}

async function onSingle() {
  const need = estimatedNeed(1)
  const bal = Number(info.balance ?? 0)
  if (bal < need) {
    ElMessage.warning(`积分不足，单抽还需 ${need} 积分`)
    return
  }
  busy.value = true
  phase.value = 'single_shuffle'
  singleOutcome.value = null
  try {
    const [res] = await Promise.all([
      lotteryDraw(1, selectedActivityId.value),
      playGachaVideo(),
    ])
    if (res.code !== 0 || !res.data?.results?.length) {
      phase.value = 'idle'
      nextTick(() => rewindGachaVideo())
      return
    }
    singleOutcome.value = res.data.results[0]
    await syncAfterDraw(res)
    phase.value = 'single_result'
    resultDialogVisible.value = true
    const text = formatOutcome(singleOutcome.value)
    if (singleOutcome.value?.rewardDetail || singleOutcome.value?.grantPoints > 0) {
      ElMessage.success(`恭喜获得：${text}`)
    }
    notifyActivityPoints([singleOutcome.value])
  } finally {
    busy.value = false
  }
}

async function onTen() {
  const need = estimatedNeed(10)
  const bal = Number(info.balance ?? 0)
  if (bal < need) {
    ElMessage.warning(`积分不足，十连还需 ${need} 积分`)
    return
  }
  busy.value = true
  phase.value = 'ten_shuffle'
  tenResults.value = []
  try {
    const [res] = await Promise.all([
      lotteryDraw(10, selectedActivityId.value),
      playGachaVideo(),
    ])
    if (res.code !== 0 || !res.data?.results?.length) {
      phase.value = 'idle'
      nextTick(() => rewindGachaVideo())
      return
    }
    tenResults.value = res.data.results
    await syncAfterDraw(res)
    phase.value = 'ten_result'
    resultDialogVisible.value = true
    notifyActivityPoints(tenResults.value)
  } finally {
    busy.value = false
  }
}

async function onTaskAction(task) {
  if (!task || busy.value) return
  if (task.status === 'CLAIMED') return
  if (task.status === 'LOCKED') {
    if (task.taskCode === 'CHECKIN_TODAY') {
      router.push('/checkin')
      return
    }
    router.push('/')
    return
  }
  if (task.status !== 'CLAIMABLE') return
  claimingTaskCode.value = task.taskCode
  try {
    const res = await claimLotteryTask({
      activityId: selectedActivityId.value,
      taskCode: task.taskCode,
    })
    if (res.code === 0) {
      ElMessage.success(`已领取 ${task.voucherReward || 0} 张抵扣券`)
      await loadInfo({ silent: true })
    }
  } catch {
    // request 已提示
  } finally {
    claimingTaskCode.value = ''
  }
}

let lastTaskRefreshAt = 0

function refreshTasksWhenVisible() {
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
  if (busy.value || loading.value || claimingTaskCode.value) return
  const now = Date.now()
  if (now - lastTaskRefreshAt < 2000) return
  lastTaskRefreshAt = now
  loadInfo({ silent: true })
}

function onDocumentVisibilityChange() {
  if (document.visibilityState === 'visible') {
    refreshTasksWhenVisible()
  }
}

function onWindowFocus() {
  refreshTasksWhenVisible()
}

onMounted(async () => {
  loading.value = true
  try {
    await loadActivities()
    await loadInfo()
    await loadPublicFeed()
  } finally {
    loading.value = false
  }
  nextTick(() => rewindGachaVideo())
  publicFeedTimer = setInterval(() => {
    loadPublicFeed()
  }, PUBLIC_FEED_POLL_MS)
  document.addEventListener('visibilitychange', onDocumentVisibilityChange)
  window.addEventListener('focus', onWindowFocus)
  window.addEventListener('pageshow', onWindowFocus)
})

onUnmounted(() => {
  stopGachaPaintLoop()
  if (publicFeedTimer) clearInterval(publicFeedTimer)
  document.removeEventListener('visibilitychange', onDocumentVisibilityChange)
  window.removeEventListener('focus', onWindowFocus)
  window.removeEventListener('pageshow', onWindowFocus)
})
