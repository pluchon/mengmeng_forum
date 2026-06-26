import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Aim, DataLine, List, Promotion, Trophy, VideoPlay } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getGobangActiveRooms,
  getGobangLeaderboard,
  getGobangRecords,
  getJinziLeaderboard,
  getJinziRecords,
  getTetrisLeaderboard,
  getTetrisPkActiveRooms,
  getTetrisProfile,
  getTetrisRecords,
  getTetrisReplay,
} from '@/api/game'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useForumPointsBalance } from '@/composables/useForumPointsBalance'
import TetrisCoverBoard from '@/components/game/TetrisCoverBoard.vue'
import { drawBoard } from '@/scripts/games/tetris/canvas'
import { createReplayRunner } from '@/scripts/games/tetris/replayRunner'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'

const scoreDelta = 10
const TETRIS_REPLAY_CELL = 20
let refreshTimer = null
let tetrisReplayTimer = null
let tetrisReplayRunner = null
let tetrisReplayStartedAt = 0

function statusText(status) {
  if (status === 'MATCHING') return '匹配中'
  if (status === 'PLAYING') return '对局中'
  return '空闲'
}

function endReasonText(reason) {
  if (reason === 'FIVE') return '五子连珠'
  if (reason === 'FIVE_IN_ROW') return '五子连珠'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'LINE') return '三子连线'
  if (reason === 'DRAW') return '平局'
  if (reason === 'DISCONNECT') return '断线'
  if (reason === 'DISCONNECT_TIMEOUT') return '断线'
  if (reason === 'TIMEOUT') return '超时'
  return reason || '正常结束'
}

function formatDateTime(value) {
  const d = parseForumDateTime(value)
  if (!d) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatRecordTime(value) {
  const d = parseForumDateTime(value)
  if (!d) return '刚刚'
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const router = useRouter()
const { pointsBalance, refreshForumPointsBalance } = useForumPointsBalance()
const loading = ref(false)
const statsVisible = ref(false)
const recentVisible = ref(false)
const leaderboardVisible = ref(false)
const activeGameCode = ref('gobang')
const statRecords = ref([])
const recentRecords = ref([])
const recentTotal = ref(0)
const recentPage = ref(1)
const recentPageSize = ref(10)
const tetrisReplayVisible = ref(false)
const tetrisReplayPlaying = ref(false)
const tetrisReplayProgress = ref(0)
const tetrisReplayRecord = ref(null)
const tetrisReplayBoardRef = ref(null)
const tetrisProfile = reactive({
  userId: null,
  bestScore: 0,
  totalCount: 0,
  winCount: 0,
  loseCount: 0,
  winRate: 0,
})
const activeRooms = ref([])
const tetrisPkRooms = ref([])
const leaderboard = ref([])
const overview = reactive({
  games: [],
  gobangProfile: null,
  jinziProfile: null,
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

const jinziGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'jinzi') || {
    gameCode: 'jinzi',
    gameName: '井字',
    enabled: true,
    onlineCount: 0,
  },
)

const tetrisGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'tetris') || {
    gameCode: 'tetris',
    gameName: '俄罗斯方块',
    enabled: true,
    onlineCount: 0,
  },
)

const profile = computed(() => {
  if (activeGameCode.value === 'jinzi') return overview.jinziProfile || {}
  if (activeGameCode.value === 'tetris') return tetrisProfile
  return overview.gobangProfile || {}
})
const tetrisWinRateText = computed(() => {
  const total = Number(tetrisProfile.totalCount) || 0
  if (!total) return '0%'
  const best = Number(tetrisProfile.bestScore) || 0
  return best > 0 ? `最高 ${best}` : '0%'
})
const gobangProfile = computed(() => overview.gobangProfile || {})
const totalCount = computed(() => Number(profile.value.totalCount) || 0)
const winRateText = computed(() => `${Number(profile.value.winRate) || 0}%`)
const statusLabel = computed(() => statusText(profile.value.currentStatus))
const lobbyOnlineText = computed(() => `${Number(overview.lobbyOnlineCount) || 0}人`)
const gameOnlineCount = computed(() => Number(gobangGame.value.onlineCount) || 0)
const gameOnlineText = computed(() => `${gameOnlineCount.value}人在线`)
const jinziOnlineText = computed(() => `${Number(jinziGame.value.onlineCount) || 0}人在线`)
const tetrisOnlineText = computed(() => `${Number(tetrisGame.value.onlineCount) || 0}人在线`)
const activeRoomCount = computed(() => activeRooms.value.length)
const statSummaryText = computed(() => `${totalCount.value} 局 · 胜率 ${winRateText.value}`)
const activeGameName = computed(() => {
  if (activeGameCode.value === 'jinzi') return '井字'
  if (activeGameCode.value === 'tetris') return '俄罗斯方块'
  return '五子棋'
})
const rankText = computed(() => {
  const points = Number(gobangProfile.value.score) || 0
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
      overview.jinziProfile = res.data.jinziProfile || null
      overview.lobbyOnlineCount = Number(res.data.lobbyOnlineCount) || 0
    }
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadActiveRooms() {
  const [gobangRes, tetrisPkRes] = await Promise.all([getGobangActiveRooms(), getTetrisPkActiveRooms()])
  if (gobangRes.code === 0) {
    activeRooms.value = Array.isArray(gobangRes.data) ? gobangRes.data : []
  }
  if (tetrisPkRes.code === 0) {
    tetrisPkRooms.value = Array.isArray(tetrisPkRes.data) ? tetrisPkRes.data : []
  }
}

async function loadLeaderboard(gameCode = activeGameCode.value) {
  const request = gameCode === 'jinzi'
    ? getJinziLeaderboard
    : gameCode === 'tetris'
      ? getTetrisLeaderboard
      : getGobangLeaderboard
  const res = await request({ pageSize: 20 })
  if (res.code === 0) {
    leaderboard.value = Array.isArray(res.data) ? res.data : []
  }
}

async function refreshLobby(silent = false) {
  await Promise.all([loadOverview(silent), loadActiveRooms(), refreshForumPointsBalance()])
}

async function loadRecentRecords(page = recentPage.value) {
  const res = await getTetrisRecords({ pageNum: page, pageSize: recentPageSize.value })
  if (res.code === 0 && res.data) {
    recentRecords.value = unwrapPageRecords(res.data)
    recentTotal.value = Number(res.data.total) || recentRecords.value.length
    recentPage.value = page
  }
}

async function openRecentMatches() {
  recentVisible.value = true
  await loadRecentRecords(1)
}

function onRecentPageChange(page) {
  loadRecentRecords(page)
}

function stopTetrisReplay() {
  tetrisReplayPlaying.value = false
  if (tetrisReplayTimer) {
    clearInterval(tetrisReplayTimer)
    tetrisReplayTimer = null
  }
}

function paintTetrisReplayFrame() {
  const canvas = tetrisReplayBoardRef.value
  if (!canvas || !tetrisReplayRunner) return
  const state = tetrisReplayRunner.getState()
  const ctx = canvas.getContext('2d')
  drawBoard(ctx, {
    matrix: state.matrix,
    cur: state.cur,
    ghost: null,
    cellSize: TETRIS_REPLAY_CELL,
  })
}

function startTetrisReplayPlayback() {
  if (!tetrisReplayRunner) return
  stopTetrisReplay()
  tetrisReplayStartedAt = Date.now()
  tetrisReplayPlaying.value = true
  tetrisReplayTimer = setInterval(() => {
    const elapsed = Date.now() - tetrisReplayStartedAt
    tetrisReplayRunner.stepTo(elapsed)
    const total = tetrisReplayRunner.totalDuration()
    tetrisReplayProgress.value = total > 0 ? Math.min(100, Math.round((elapsed / total) * 100)) : 100
    paintTetrisReplayFrame()
    if (tetrisReplayRunner.isDone()) {
      stopTetrisReplay()
    }
  }, 50)
}

function toggleTetrisReplayAuto() {
  if (tetrisReplayPlaying.value) {
    stopTetrisReplay()
    return
  }
  startTetrisReplayPlayback()
}

async function openTetrisReplay(row) {
  if (!row?.id) return
  stopTetrisReplay()
  const res = await getTetrisReplay(row.id)
  if (res.code !== 0 || !res.data) {
    ElMessage.warning(res.message || '回放加载失败')
    return
  }
  tetrisReplayRecord.value = res.data.record
  let payload = { seed: res.data.seed, inputs: [] }
  try {
    payload = JSON.parse(res.data.replayPayload || '{}')
  } catch {
    payload = { seed: res.data.seed, inputs: [] }
  }
  tetrisReplayProgress.value = 0
  tetrisReplayVisible.value = true
  await nextTick()
  tetrisReplayRunner = createReplayRunner(
    payload.seed || res.data.seed,
    payload.inputs || [],
  )
  paintTetrisReplayFrame()
}

async function loadStatRecords(gameCode = activeGameCode.value) {
  const request = gameCode === 'jinzi'
    ? getJinziRecords
    : gameCode === 'tetris'
      ? getTetrisRecords
      : getGobangRecords
  const res = await request({ pageNum: 1, pageSize: 8 })
  if (res.code === 0 && res.data) {
    statRecords.value = unwrapPageRecords(res.data)
  }
}

async function openStats(gameCode = 'gobang') {
  activeGameCode.value = gameCode
  statsVisible.value = true
  if (gameCode === 'tetris') {
    const res = await getTetrisProfile()
    if (res.code === 0 && res.data) {
      Object.assign(tetrisProfile, res.data)
    }
  }
  await loadStatRecords(gameCode)
}

async function openLeaderboard(gameCode = 'gobang') {
  activeGameCode.value = gameCode
  leaderboardVisible.value = true
  await loadLeaderboard(gameCode)
}

function recordResultText(row) {
  if (activeGameCode.value === 'tetris') {
    return `得分 ${row.score ?? 0}`
  }
  if (!row.winnerUserId) return '平局'
  return row.winnerUserId === profile.value.userId ? '胜利' : '失败'
}

function enterGobang() {
  router.push('/games/gobang')
}

function enterJinzi() {
  router.push('/games/jinzi')
}

function enterTetris() {
  router.push('/games/tetris')
}

function enterTetrisPk() {
  router.push('/games/tetris/pk')
}

function watchRoom(row) {
  if (!row?.roomId) return
  router.push(`/games/gobang/rooms/${encodeURIComponent(row.roomId)}`)
}

function watchTetrisPkRoom(row) {
  if (!row?.roomId) return
  router.push(`/games/tetris/pk/rooms/${encodeURIComponent(row.roomId)}`)
}

function backHome() {
  router.push('/')
}

onMounted(async () => {
  await refreshLobby()
  await loadLeaderboard('gobang')
  lobbySocket.connect()
  refreshTimer = window.setInterval(() => {
    refreshLobby(true)
  }, 5000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  stopTetrisReplay()
  lobbySocket.close()
})
