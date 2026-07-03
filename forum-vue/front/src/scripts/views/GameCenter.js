import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, DataLine, Promotion, Trophy, VideoPlay } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getGobangActiveRooms,
  getGobangLeaderboard,
  getGobangRecords,
  getJinziLeaderboard,
  getJinziRecords,
  getTetrisLeaderboard,
  getTetrisPkActiveRooms,
  getTetrisPkLeaderboard,
  getTetrisPkProfile,
  getTetrisPkRecords,
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

const TETRIS_REPLAY_CELL = 20
let refreshTimer = null
let tetrisReplayTimer = null
let tetrisReplayRunner = null
let tetrisReplayStartedAt = 0

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
const watchGameCode = ref('gobang')
const watchKeyword = ref('')
const watchSearchKeyword = ref('')
const watchPage = ref(1)
const watchPageSize = ref(4)
const statRecords = ref([])
const statTotal = ref(0)
const statPage = ref(1)
const statPageSize = ref(5)
const recentRecords = ref([])
const recentTotal = ref(0)
const recentPage = ref(1)
const recentPageSize = ref(10)
const leaderboardPage = ref(1)
const leaderboardPageSize = ref(5)
const leaderboardHasNext = ref(false)
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
const tetrisPkProfile = reactive({
  userId: null,
  score: 1000,
  totalCount: 0,
  winCount: 0,
  loseCount: 0,
  winRate: 0,
  rankName: '青铜 III',
  nextRankDistance: 100,
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

const tetrisPkGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'tetris_pk') || {
    gameCode: 'tetris_pk',
    gameName: '俄罗斯方块PK',
    enabled: true,
    onlineCount: 0,
  },
)

const profile = computed(() => {
  if (activeGameCode.value === 'jinzi') return overview.jinziProfile || {}
  if (activeGameCode.value === 'tetris') return tetrisProfile
  if (activeGameCode.value === 'tetris_pk') return tetrisPkProfile
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
const lobbyOnlineText = computed(() => `${Number(overview.lobbyOnlineCount) || 0}人`)
const gameOnlineCount = computed(() => Number(gobangGame.value.onlineCount) || 0)
const gameOnlineText = computed(() => `${gameOnlineCount.value}人在线`)
const jinziOnlineText = computed(() => `${Number(jinziGame.value.onlineCount) || 0}人在线`)
const tetrisOnlineText = computed(() => `${Number(tetrisGame.value.onlineCount) || 0}人在线`)
const tetrisPkOnlineText = computed(() => `${Number(tetrisPkGame.value.onlineCount) || 0}人在线`)
const gobangTotalText = computed(() => `共游玩了 ${Number(gobangProfile.value.totalCount) || 0} 局`)
const jinziTotalText = computed(() => `共游玩了 ${Number(overview.jinziProfile?.totalCount) || 0} 局`)
const tetrisTotalText = computed(() => `共游玩了 ${Number(tetrisProfile.totalCount) || 0} 局`)
const tetrisPkTotalText = computed(() => `共游玩了 ${Number(tetrisPkProfile.totalCount) || 0} 局`)
const activeGameName = computed(() => {
  if (activeGameCode.value === 'jinzi') return '井字'
  if (activeGameCode.value === 'tetris') return '俄罗斯方块单人版'
  if (activeGameCode.value === 'tetris_pk') return '俄罗斯方块PK版'
  return '五子棋'
})
const rankText = computed(() => {
  return gobangProfile.value.rankName || '青铜 III'
})
const rankProgressPercent = computed(() => {
  const percent = Number(gobangProfile.value.rankInfo?.progressPercent)
  if (!Number.isFinite(percent)) return 0
  return Math.max(0, Math.min(100, percent))
})
const rankProgressText = computed(() => {
  const rankInfo = gobangProfile.value.rankInfo || {}
  if (!rankInfo.nextRankScore) return '大师段'
  const score = Number(gobangProfile.value.score) || 1000
  const minScore = Number(rankInfo.rankMinScore) || 1000
  const nextScore = Number(rankInfo.nextRankScore) || minScore + 100
  const current = Math.max(0, score - minScore)
  const total = Math.max(1, nextScore - minScore)
  return `${current} / ${total}`
})
const rankNextText = computed(() => {
  const distance = Number(gobangProfile.value.nextRankDistance) || 0
  return distance > 0 ? `距离下一段还差 ${distance} 分` : '已达顶段'
})
const winRateTrendText = computed(() => {
  const total = Number(gobangProfile.value.totalCount) || 0
  return total > 0 ? `${total} 局` : '暂无对局'
})
const watchRooms = computed(() => (watchGameCode.value === 'tetris_pk' ? tetrisPkRooms.value : activeRooms.value))
const watchCountText = computed(() => `${watchRooms.value.length} 间可观战`)
const filteredWatchRooms = computed(() => {
  const keyword = normalizeWatchKeyword(watchSearchKeyword.value)
  if (!keyword) return watchRooms.value
  return watchRooms.value.filter((row) => {
    return [watchRoomTitle(row), watchRoomMeta(row), row?.roomId]
      .some((text) => normalizeWatchKeyword(text).includes(keyword))
  })
})
const watchTotal = computed(() => filteredWatchRooms.value.length)
const pagedWatchRooms = computed(() => {
  const start = (watchPage.value - 1) * watchPageSize.value
  return filteredWatchRooms.value.slice(start, start + watchPageSize.value)
})
const leaderboardRows = computed(() => leaderboard.value.slice(0, leaderboardPageSize.value))
const leaderboardTotal = computed(() => {
  const viewed = (leaderboardPage.value - 1) * leaderboardPageSize.value + leaderboardRows.value.length
  return leaderboardHasNext.value ? viewed + 1 : viewed
})

function normalizeWatchKeyword(value) {
  return String(value ?? '').trim().toLowerCase()
}

function pickFirstText(row, keys) {
  for (const key of keys) {
    const value = row?.[key]
    if (value !== null && value !== undefined && String(value).trim()) {
      return String(value).trim()
    }
  }
  return ''
}

function userNameFallback(userId, fallbackText) {
  if (userId === null || userId === undefined || userId === '') return fallbackText
  if (Number(userId) < 0) return 'AI'
  return `用户 ${userId}`
}

function watchPlayerName(row, side) {
  if (watchGameCode.value === 'tetris_pk') {
    if (side === 'left') {
      return pickFirstText(row, ['redNickname', 'player1Nickname', 'leftNickname', 'hostNickname'])
        || userNameFallback(row?.redUserId ?? row?.player1UserId, '红方')
    }
    return pickFirstText(row, ['blueNickname', 'player2Nickname', 'rightNickname', 'guestNickname'])
      || userNameFallback(row?.blueUserId ?? row?.player2UserId, '蓝方')
  }
  if (side === 'left') {
    return pickFirstText(row, ['blackNickname', 'player1Nickname', 'leftNickname', 'hostNickname'])
      || userNameFallback(row?.blackUserId, '黑方')
  }
  if (row?.aiRoom) return 'AI'
  return pickFirstText(row, ['whiteNickname', 'player2Nickname', 'rightNickname', 'guestNickname'])
    || userNameFallback(row?.whiteUserId, '白方')
}

function watchRoomTitle(row) {
  const title = pickFirstText(row, ['matchTitle', 'title'])
  if (title && title !== '玩家对局') return title
  return `${watchPlayerName(row, 'left')} VS ${watchPlayerName(row, 'right')}`
}

function watchRoomMeta(row) {
  const roomId = String(row?.roomId ?? '').trim()
  if (!roomId) return '等待同步房间信息'
  return roomId.length > 18 ? `${roomId.slice(0, 18)}...` : roomId
}

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
  clampWatchPage()
}

async function loadLeaderboard(gameCode = activeGameCode.value) {
  const request = resolveLeaderboardRequest(gameCode)
  const requestSize = leaderboardPage.value * leaderboardPageSize.value + 1
  const res = await request({ pageSize: requestSize })
  if (res.code === 0) {
    const rows = Array.isArray(res.data) ? res.data : []
    const start = (leaderboardPage.value - 1) * leaderboardPageSize.value
    const end = start + leaderboardPageSize.value + 1
    leaderboard.value = rows.slice(start, end)
    leaderboardHasNext.value = leaderboard.value.length > leaderboardPageSize.value
  }
}

function resolveLeaderboardRequest(gameCode) {
  if (gameCode === 'jinzi') return getJinziLeaderboard
  if (gameCode === 'tetris') return getTetrisLeaderboard
  if (gameCode === 'tetris_pk') return getTetrisPkLeaderboard
  return getGobangLeaderboard
}

async function refreshLobby(silent = false) {
  await Promise.all([
    loadOverview(silent),
    loadActiveRooms(),
    loadProfileByGame('tetris'),
    loadProfileByGame('tetris_pk'),
    refreshForumPointsBalance(),
  ])
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

async function loadStatRecords(gameCode = activeGameCode.value, page = statPage.value) {
  const request = resolveRecordsRequest(gameCode)
  const res = await request({ pageNum: page, pageSize: statPageSize.value })
  if (res.code === 0 && res.data) {
    statRecords.value = unwrapPageRecords(res.data)
    statTotal.value = Number(res.data.total) || statRecords.value.length
    statPage.value = page
  }
}

function resolveRecordsRequest(gameCode) {
  if (gameCode === 'jinzi') return getJinziRecords
  if (gameCode === 'tetris') return getTetrisRecords
  if (gameCode === 'tetris_pk') return getTetrisPkRecords
  return getGobangRecords
}

async function loadProfileByGame(gameCode) {
  if (gameCode === 'tetris') {
    const res = await getTetrisProfile()
    if (res.code === 0 && res.data) {
      Object.assign(tetrisProfile, res.data)
    }
    return
  }
  if (gameCode === 'tetris_pk') {
    const res = await getTetrisPkProfile()
    if (res.code === 0 && res.data) {
      Object.assign(tetrisPkProfile, res.data)
    }
  }
}

async function openStats(gameCode = 'gobang') {
  activeGameCode.value = gameCode
  statPage.value = 1
  statsVisible.value = true
  await loadProfileByGame(gameCode)
  await loadStatRecords(gameCode, 1)
}

async function openLeaderboard(gameCode = 'gobang') {
  activeGameCode.value = gameCode
  leaderboardPage.value = 1
  leaderboardVisible.value = true
  await loadLeaderboard(gameCode)
}

async function onStatsGameChange(gameCode) {
  activeGameCode.value = gameCode
  statPage.value = 1
  await loadProfileByGame(gameCode)
  await loadStatRecords(gameCode, 1)
}

async function onLeaderboardGameChange(gameCode) {
  activeGameCode.value = gameCode
  leaderboardPage.value = 1
  await loadLeaderboard(gameCode)
}

function onStatPageChange(page) {
  loadStatRecords(activeGameCode.value, page)
}

function onLeaderboardPageChange(page) {
  leaderboardPage.value = page
  loadLeaderboard(activeGameCode.value)
}

function setWatchGame(gameCode) {
  watchGameCode.value = gameCode
  watchKeyword.value = ''
  watchSearchKeyword.value = ''
  watchPage.value = 1
}

function searchWatchRooms() {
  watchSearchKeyword.value = watchKeyword.value.trim()
  watchPage.value = 1
}

function handleWatchSearchKeyup(event) {
  if (event?.key === 'Enter') {
    searchWatchRooms()
  }
}

function onWatchPageChange(page) {
  watchPage.value = page
}

function clampWatchPage() {
  const maxPage = Math.max(1, Math.ceil(watchTotal.value / watchPageSize.value))
  if (watchPage.value > maxPage) {
    watchPage.value = maxPage
  }
}

function recordResultText(row) {
  if (activeGameCode.value === 'tetris') {
    return `得分 ${row.score ?? 0}`
  }
  if (!row.winnerUserId) return '平局'
  return row.winnerUserId === profile.value.userId ? '胜利' : '失败'
}

function recordScoreDelta(row) {
  if (row?.viewerScoreDelta !== null && row?.viewerScoreDelta !== undefined) {
    return Number(row.viewerScoreDelta) || 0
  }
  if (!row?.winnerUserId) return 0
  const delta = Number(row.scoreDelta) || 0
  return row.winnerUserId === profile.value.userId ? delta : -delta
}

function formatScoreDelta(row) {
  if (activeGameCode.value === 'tetris') {
    const points = Number(row?.forumPointsAwarded) || 0
    return points > 0 ? `+${points}` : String(points)
  }
  const delta = recordScoreDelta(row)
  return delta > 0 ? `+${delta}` : String(delta)
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
