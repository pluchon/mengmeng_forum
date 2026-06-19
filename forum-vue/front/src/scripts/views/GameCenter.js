import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, DataLine, Promotion, Trophy } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getGobangActiveRooms,
  getGobangLeaderboard,
  getGobangRecords,
} from '@/api/game'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'

const scoreDelta = 10
let refreshTimer = null

function statusText(status) {
  if (status === 'MATCHING') return '匹配中'
  if (status === 'PLAYING') return '对局中'
  return '空闲'
}

function endReasonText(reason) {
  if (reason === 'FIVE') return '五子连珠'
  if (reason === 'FIVE_IN_ROW') return '五子连珠'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'DISCONNECT') return '断线'
  if (reason === 'DISCONNECT_TIMEOUT') return '断线'
  if (reason === 'TIMEOUT') return '超时'
  return reason || '正常结束'
}

function formatRecordTime(value) {
  const d = parseForumDateTime(value)
  if (!d) return '刚刚'
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const router = useRouter()
const loading = ref(false)
const statsVisible = ref(false)
const leaderboardVisible = ref(false)
const statRecords = ref([])
const activeRooms = ref([])
const leaderboard = ref([])
const overview = reactive({
  games: [],
  gobangProfile: null,
  lobbyOnlineCount: 0,
})

const lobbySocket = useGameWebSocket('game-center/lobby', {
  onMessage(message) {
    if (message.type === 'lobby_ready' && message.data) {
      overview.lobbyOnlineCount = Number(message.data.lobbyOnlineCount ?? message.data.onlineCount) || overview.lobbyOnlineCount
    }
    if (!message.ok && message.message) {
      ElMessage.warning(message.message)
    }
  },
})

const gobangGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'gobang') || {
    gameCode: 'gobang',
    gameName: '五子棋',
    enabled: true,
    onlineCount: 0,
  },
)

const profile = computed(() => overview.gobangProfile || {})
const totalCount = computed(() => Number(profile.value.totalCount) || 0)
const winRateText = computed(() => `${Number(profile.value.winRate) || 0}%`)
const statusLabel = computed(() => statusText(profile.value.currentStatus))
const pointsBalanceText = computed(() => `${Number(profile.value.forumPoints ?? profile.value.points ?? profile.value.score) || 0} 积分`)
const lobbyOnlineText = computed(() => `${Number(overview.lobbyOnlineCount) || 0}人`)
const gameOnlineCount = computed(() => Number(gobangGame.value.onlineCount) || 0)
const gameOnlineText = computed(() => `${gameOnlineCount.value}人在线`)
const activeRoomCount = computed(() => activeRooms.value.length)
const statSummaryText = computed(() => `${totalCount.value} 局 · 胜率 ${winRateText.value}`)
const rankText = computed(() => {
  const points = Number(profile.value.score) || 0
  if (points >= 2000) return '大师'
  if (points >= 1200) return '熟手'
  if (points >= 300) return '棋友'
  return '新手'
})

async function loadOverview(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await getGameCenterOverview()
    if (res.code === 0 && res.data) {
      overview.games = Array.isArray(res.data.games) ? res.data.games : []
      overview.gobangProfile = res.data.gobangProfile || null
      overview.lobbyOnlineCount = Number(res.data.lobbyOnlineCount) || 0
    }
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadActiveRooms() {
  const res = await getGobangActiveRooms()
  if (res.code === 0) {
    activeRooms.value = Array.isArray(res.data) ? res.data : []
  }
}

async function loadLeaderboard() {
  const res = await getGobangLeaderboard({ pageSize: 20 })
  if (res.code === 0) {
    leaderboard.value = Array.isArray(res.data) ? res.data : []
  }
}

async function refreshLobby(silent = false) {
  await Promise.all([loadOverview(silent), loadActiveRooms()])
}

async function loadStatRecords() {
  const res = await getGobangRecords({ pageNum: 1, pageSize: 8 })
  if (res.code === 0 && res.data) {
    statRecords.value = unwrapPageRecords(res.data)
  }
}

async function openStats() {
  statsVisible.value = true
  await loadStatRecords()
}

async function openLeaderboard() {
  leaderboardVisible.value = true
  await loadLeaderboard()
}

function recordResultText(row) {
  return row.winnerUserId === profile.value.userId ? '胜利' : '失败'
}

function enterGobang() {
  router.push('/games/gobang')
}

function watchRoom(row) {
  if (!row?.roomId) return
  router.push(`/games/gobang/rooms/${encodeURIComponent(row.roomId)}`)
}

function backHome() {
  router.push('/')
}

onMounted(async () => {
  await refreshLobby()
  await loadLeaderboard()
  lobbySocket.connect()
  refreshTimer = window.setInterval(() => {
    refreshLobby(true)
  }, 5000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  lobbySocket.close()
})

defineExpose({
  ArrowLeft,
  DataLine,
  Promotion,
  Trophy,
  activeRoomCount,
  activeRooms,
  backHome,
  endReasonText,
  enterGobang,
  formatRecordTime,
  gameOnlineCount,
  gameOnlineText,
  gobangGame,
  leaderboard,
  leaderboardVisible,
  loadOverview,
  loading,
  lobbyOnlineText,
  lobbySocket,
  openLeaderboard,
  openStats,
  overview,
  pointsBalanceText,
  profile,
  rankText,
  recordResultText,
  refreshLobby,
  scoreDelta,
  statSummaryText,
  statRecords,
  statsVisible,
  statusLabel,
  totalCount,
  watchRoom,
  winRateText,
})
