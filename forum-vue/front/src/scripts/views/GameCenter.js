import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onActivated, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, DataLine, Medal, Promotion, Trophy, VideoPlay, View } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getGameCategories,
  getGamePage,
  getGameStatisticsRecords,
  getGameStatisticsSummary,
  getGobangActiveRooms,
  getGobangRoom,
  getGobangReplay,
  getJinziRoom,
  getTetrisLeaderboard,
  getTetrisPkActiveRooms,
  getTetrisPkLeaderboard,
  getTetrisPkProfile,
  getTetrisPkRoom,
  getTetrisProfile,
  getTetrisReplay,
} from '@/api/game'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useForumPointsBalance } from '@/composables/useForumPointsBalance'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { drawBoard } from '@/scripts/games/tetris/canvas'
import { createReplayRunner, isApproximateReplay } from '@/scripts/games/tetris/replayRunner'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'
import { DEFAULT_AVATAR } from '@/utils/constants'
import {
  ELUOSI_ALONE_WEBP_URL as eluosiAloneImg,
  ELUOSI_PK_WEBP_URL as eluosiPkImg,
  GAME_CARD_WEBP_URL as gameCardImg,
  JINZI_COVER_WEBP_URL as jinziImg,
  WUZIQI_COVER_WEBP_URL as wuziqiImg,
} from '@/utils/clientOss'

const TETRIS_REPLAY_CELL = 20
let refreshTimer = null
let tetrisReplayTimer = null
let tetrisReplayRunner = null
let tetrisReplayStartedAt = 0
let statsRequestSequence = 0
let leaderboardRequestSequence = 0
let gameCenterInitialized = false

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
const leaderboardVisible = ref(false)
const gameCategory = ref('all')
const gameCategories = ref([
  { code: 'all', label: '全部' },
  { code: 'pvp', label: '双人/多人对战' },
  { code: 'solo', label: '单人休闲' },
])
const gamePageNum = ref(1)
const gamePageSize = ref(8)
const gameTotal = ref(0)
const pagedGames = ref([])
const gameListLoading = ref(false)

const GAME_PRESETS = {
  gobang: {
    name: '五子棋',
    sub: '黑白交锋 · 五子连珠',
    cover: wuziqiImg,
    btnClass: 'btn--gobang',
    idleText: '开始匹配',
  },
  jinzi: {
    name: '井字棋',
    sub: '九宫对决 · 五局三胜',
    cover: jinziImg,
    btnClass: 'btn--jinzi',
    idleText: '开始匹配',
  },
  tetris: {
    name: '俄罗斯方块单人',
    sub: '经典堆叠 · 极限下落',
    cover: eluosiAloneImg,
    btnClass: 'btn--tetris',
    idleText: '开始游戏',
  },
  tetris_pk: {
    name: '俄罗斯方块竞速',
    sub: '双人竞速 · 3 分钟内比谁消行多',
    cover: eluosiPkImg,
    btnClass: 'btn--tetris-pk',
    idleText: '开始竞速',
  },
}

const matchingGameCode = ref('')
const currentMatchRooms = reactive({
  gobang: '',
  jinzi: '',
  tetris_pk: '',
})
const pendingMatchPayloads = new Map()
const statsGameCode = ref('gobang')
const leaderboardGameCode = ref('tetris')
const watchGameCode = ref('gobang')
const watchKeyword = ref('')
const watchSearchKeyword = ref('')
const watchPageTotal = ref(1)
const watchTotalCount = ref(0)
const watchPage = ref(1)
const watchPageSize = ref(4)
const statRecords = ref([])
const statTotal = ref(0)
const statPage = ref(1)
const statPageSize = ref(5)
const leaderboardPage = ref(1)
const leaderboardPageSize = ref(5)
const leaderboardTotal = ref(0)
const tetrisReplayVisible = ref(false)
const tetrisReplayPlaying = ref(false)
const tetrisReplayProgress = ref(0)
const tetrisReplayApproximate = ref(false)
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
const statisticsSummary = reactive({
  totalCount: 0,
  winCount: 0,
  loseCount: 0,
  games: [],
})

const lobbySocket = useGameWebSocket('game-center/lobby', {
  onMessage(message) {
    // 房间开始/结束由服务端推送，不再靠 5 秒轮询把观战列表拉一遍
    if (message.type === 'active_rooms_changed') {
      const changed = message.data?.gameCode
      const watching = watchGameCode.value === 'tetris_pk' ? 'tetris_pk' : 'gobang'
      if (!changed || changed === watching) {
        void loadActiveRooms()
      }
    }
    if (message.type === 'lobby_ready' && message.data) {
      overview.lobbyOnlineCount = Number(message.data.lobbyOnlineCount ?? message.data.onlineCount) || overview.lobbyOnlineCount
    }
    // 有人进出某个游戏时服务端直接把新的人数推过来，不必再定时把整个概览拉一遍
    if (message.type === 'lobby_online_changed' && message.data) {
      const changed = message.data.gameCode
      const count = Number(message.data.onlineCount)
      if (changed && Number.isFinite(count)) {
        const target = overview.games.find((item) => item?.gameCode === changed)
        if (target) target.onlineCount = count
      }
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

const tetrisPkGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'tetris_pk') || {
    gameCode: 'tetris_pk',
    gameName: '俄罗斯方块竞速',
    enabled: true,
    onlineCount: 0,
  },
)

const statsProfile = computed(() => {
  const summary = statisticsSummary.games.find((item) => item?.gameCode === statsGameCode.value)
  if (summary) return summary
  if (statsGameCode.value === 'jinzi') return overview.jinziProfile || {}
  if (statsGameCode.value === 'tetris') return tetrisProfile
  if (statsGameCode.value === 'tetris_pk') return tetrisPkProfile
  return overview.gobangProfile || {}
})
const gobangProfile = computed(() => overview.gobangProfile || {})
const statsTotalCount = computed(() => Number(statsProfile.value.totalCount) || 0)
const statsWinRateText = computed(() => `${Number(statsProfile.value.winRate) || 0}%`)
const homeWinRateText = computed(() => `${Number(gobangProfile.value.winRate) || 0}%`)
const lobbyOnlineText = computed(() => `${Number(overview.lobbyOnlineCount) || 0} 人`)
const gameOnlineCount = computed(() => Number(gobangGame.value.onlineCount) || 0)
const gameOnlineText = computed(() => `${gameOnlineCount.value} 人在线`)
const jinziOnlineText = computed(() => `${Number(jinziGame.value.onlineCount) || 0} 人在线`)
const tetrisPkOnlineText = computed(() => `${Number(tetrisPkGame.value.onlineCount) || 0} 人在线`)
const gobangTotalText = computed(() => `共游玩 ${Number(gobangProfile.value.totalCount) || 0} 局`)
const jinziTotalText = computed(() => `共游玩 ${Number(overview.jinziProfile?.totalCount) || 0} 局`)
const tetrisTotalText = computed(() => `共游玩 ${Number(tetrisProfile.totalCount) || 0} 局`)
const tetrisPkTotalText = computed(() => `共游玩 ${Number(tetrisPkProfile.totalCount) || 0} 局`)
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
const watchRooms = computed(() => (watchGameCode.value === 'tetris_pk' ? tetrisPkRooms.value : activeRooms.value))
// 总数来自后端，不再是「当前已加载数组的长度」
const watchCountText = computed(() => `${watchTotalCount.value} 场可观战`)
// 过滤与分页都在后端做了，这里直接用返回的这一页
const pagedWatchRooms = computed(() => watchRooms.value)
const leaderboardRows = computed(() => leaderboard.value)

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

async function loadGameCategories() {
  try {
    const res = await getGameCategories()
    if (res.code !== 0 || !Array.isArray(res.data) || !res.data.length) return
    gameCategories.value = res.data
      .map((item) => ({
        code: String(item?.code || '').trim(),
        label: String(item?.label || '').trim(),
      }))
      .filter((item) => item.code && item.label)
    if (!gameCategories.value.some((item) => item.code === gameCategory.value)) {
      gameCategory.value = gameCategories.value[0]?.code || 'all'
    }
  } catch {
    // 保留本地兜底分类
  }
}

// silent：定时刷新用。加载态会给卡片区盖一层遮罩，
// 每 5 秒来一次就是用户看到的「每隔几秒闪一下」
async function loadGamePage(page = gamePageNum.value, silent = false) {
  if (!silent) gameListLoading.value = true
  try {
    const res = await getGamePage({
      pageNum: page,
      pageSize: gamePageSize.value,
      category: gameCategory.value,
    })
    if (res.code === 0 && res.data) {
      pagedGames.value = unwrapPageRecords(res.data)
      gameTotal.value = Number(res.data.total) || pagedGames.value.length
      gamePageNum.value = page
    }
  } finally {
    if (!silent) gameListLoading.value = false
  }
}

function onGamePageChange(page) {
  loadGamePage(page)
}

function setGameCategory(category) {
  gameCategory.value = category
  gamePageNum.value = 1
  loadGamePage(1)
}

function gamePreset(gameCode) {
  return GAME_PRESETS[gameCode] || {
    name: gameCode,
    sub: '',
    cover: gameCardImg,
    btnClass: 'btn--gobang',
    idleText: '开始游戏',
  }
}

function gameCardModifierClass(gameCode) {
  if (gameCode === 'tetris_pk') return 'game-card--tetris-pk'
  if (gameCode === 'tetris') return 'game-card--tetris'
  if (gameCode === 'jinzi') return 'game-card--jinzi'
  return 'game-card--gobang'
}

function gameCoverUrl(game) {
  const remote = String(game?.coverUrl || '').trim()
  if (remote) return remote
  return gamePreset(game?.gameCode).cover
}

function gameDisplayName(game) {
  return String(game?.gameName || '').trim() || gamePreset(game?.gameCode).name
}

function gameSubText(game) {
  return gamePreset(game?.gameCode).sub
}

function gameOnlineBadgeText(game) {
  const code = game?.gameCode
  if (code === 'tetris') return '单人模式'
  if (code === 'jinzi') return jinziOnlineText.value
  if (code === 'tetris_pk') return tetrisPkOnlineText.value
  if (code === 'gobang') return gameOnlineText.value
  const count = Number(game?.onlineCount) || 0
  return `${count} 人在线`
}

function gameOnlineBadgeSolo(game) {
  return game?.gameCode === 'tetris'
}

function gameMetaText(game) {
  if (game?.gameCode === 'tetris') {
    return `最高 ${formatNumber(tetrisProfile.bestScore || 0)} 分`
  }
  return gameSubText(game)
}

function gamePlayButtonClass(game) {
  return gamePreset(game?.gameCode).btnClass
}

function gamePlayIdleText(game) {
  return gamePreset(game?.gameCode).idleText
}

function enterGame(game) {
  const code = game?.gameCode
  if (code === 'gobang') return enterGobang()
  if (code === 'jinzi') return enterJinzi()
  if (code === 'tetris') return enterTetris()
  if (code === 'tetris_pk') return enterTetrisPk()
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
      syncMatchProfile('gobang', overview.gobangProfile || {})
      syncMatchProfile('jinzi', overview.jinziProfile || {})
    }
  } finally {
    if (!silent) loading.value = false
  }
}

// 观战列表现在由后端分页，房间号也在后端精确查询。
// 快速切换观战的游戏类型时，先发的请求可能后返回，用序号丢弃过期响应。
let watchRequestSequence = 0

async function loadActiveRooms() {
  const sequence = ++watchRequestSequence
  const gameCode = watchGameCode.value
  const params = {
    pageNum: watchPage.value,
    pageSize: watchPageSize.value,
    roomId: watchSearchKeyword.value.trim() || undefined,
  }
  const request = gameCode === 'tetris_pk' ? getTetrisPkActiveRooms : getGobangActiveRooms
  try {
    const res = await request(params)
    if (sequence !== watchRequestSequence) return
    const page = res?.code === 0 ? res.data : null
    const rows = Array.isArray(page?.records) ? page.records : []
    if (gameCode === 'tetris_pk') {
      tetrisPkRooms.value = rows
    } else {
      activeRooms.value = rows
    }
    watchPage.value = Number(page?.pageNum) || watchPage.value
    watchPageTotal.value = Math.max(1, Number(page?.pages) || 1)
    watchTotalCount.value = Number(page?.total) || 0
  } catch {
    if (sequence !== watchRequestSequence) return
    if (gameCode === 'tetris_pk') tetrisPkRooms.value = []
    else activeRooms.value = []
    watchPageTotal.value = 1
    watchTotalCount.value = 0
  }
}

async function loadLeaderboard(gameCode = leaderboardGameCode.value) {
  const requestSequence = ++leaderboardRequestSequence
  const request = resolveLeaderboardRequest(gameCode)
  const res = await request({
    pageNum: leaderboardPage.value,
    pageSize: leaderboardPageSize.value,
  })
  if (requestSequence !== leaderboardRequestSequence || gameCode !== leaderboardGameCode.value) return
  if (res.code === 0 && res.data) {
    leaderboard.value = unwrapPageRecords(res.data)
    leaderboardTotal.value = Number(res.data.total) || leaderboard.value.length
  }
}

function resolveLeaderboardRequest(gameCode) {
  if (gameCode === 'tetris_pk') return getTetrisPkLeaderboard
  return getTetrisLeaderboard
}

async function refreshLobby(silent = false) {
  await Promise.all([
    loadOverview(silent),
    loadGamePage(gamePageNum.value, silent),
    loadStatisticsSummary(),
    loadProfileByGame('tetris'),
    loadProfileByGame('tetris_pk'),
  ])
}

function watchViewerCount(row) {
  return Number(row?.spectatorCount ?? row?.viewerCount ?? row?.watchCount ?? row?.onlineCount) || 0
}

function syncMatchProfile(gameCode, profile) {
  const status = String(profile?.currentStatus || '').toUpperCase()
  currentMatchRooms[gameCode] = status === 'PLAYING' && profile?.currentRoomId
    ? String(profile.currentRoomId)
    : ''
  if (status === 'MATCHING' && !matchingGameCode.value) {
    matchingGameCode.value = gameCode
    matchSockets[gameCode]?.connect()
  }
}

function cancelGameMatch(gameCode) {
  const socket = matchSockets[gameCode]
  if (socket?.connected.value) {
    socket.send('stop_match', {})
  }
  pendingMatchPayloads.delete(gameCode)
  if (matchingGameCode.value === gameCode) {
    matchingGameCode.value = ''
  }
  ElMessage.info('已取消匹配')
}

function handleMatchButtonClick(gameCode, mode = 'RANKED') {
  if (matchingGameCode.value === gameCode) {
    cancelGameMatch(gameCode)
    return
  }
  if (matchingGameCode.value && matchingGameCode.value !== gameCode) {
    ElMessage.warning('正在匹配中，请先取消当前匹配')
    return
  }
  void startGameMatch(gameCode, { mode })
}

const ROOM_ROUTE_NAMES = {
  gobang: 'gobangRoom',
  jinzi: 'jinziRoom',
  tetris_pk: 'tetrisPkRoom',
}

const ROOM_FETCHERS = {
  gobang: getGobangRoom,
  jinzi: getJinziRoom,
  tetris_pk: getTetrisPkRoom,
}

// 「继续对局」的房号来自轮询到的 profile，可能已经过期。
// 直接跳过去会把人送进一个已经散场的房间，跳之前先问一句服务端。
async function isRoomAlive(gameCode, roomId) {
  const fetcher = ROOM_FETCHERS[gameCode]
  if (!fetcher) return false
  try {
    const res = await fetcher(roomId)
    return res.code === 0 && Boolean(res.data) && res.data.roomStatus !== 'FINISHED'
  } catch {
    return false
  }
}

async function startGameMatch(gameCode, payload = null) {
  const existingRoomId = currentMatchRooms[gameCode]
  if (existingRoomId) {
    if (await isRoomAlive(gameCode, existingRoomId)) {
      router.push({ name: ROOM_ROUTE_NAMES[gameCode] || 'tetrisPkRoom', params: { roomId: existingRoomId } })
      return
    }
    // 房间已经不在了，清掉旧房号并按正常匹配继续，不要卡住用户
    currentMatchRooms[gameCode] = ''
  }
  if (matchingGameCode.value && matchingGameCode.value !== gameCode) {
    ElMessage.warning('一次只能匹配一个游戏')
    return
  }
  const socket = matchSockets[gameCode]
  matchingGameCode.value = gameCode
  if (!socket?.connected.value) {
    pendingMatchPayloads.set(gameCode, payload)
    if (!socket?.connect()) {
      pendingMatchPayloads.delete(gameCode)
      matchingGameCode.value = ''
    }
    return
  }
  if (!socket.send('start_match', payload)) {
    matchingGameCode.value = ''
  }
}

function matchButtonText(gameCode, idleText) {
  if (matchingGameCode.value === gameCode) return '匹配中 · 点击取消'
  if (currentMatchRooms[gameCode]) return '继续对局'
  if (matchingGameCode.value && matchingGameCode.value !== gameCode) return '其他匹配中'
  return idleText
}

function matchButtonDisabled(gameCode) {
  if (!matchingGameCode.value) return false
  return matchingGameCode.value !== gameCode
}

async function loadStatisticsSummary() {
  const res = await getGameStatisticsSummary()
  if (res.code === 0 && res.data) {
    statisticsSummary.totalCount = Number(res.data.totalCount) || 0
    statisticsSummary.winCount = Number(res.data.winCount) || 0
    statisticsSummary.loseCount = Number(res.data.loseCount) || 0
    statisticsSummary.games = Array.isArray(res.data.games) ? res.data.games : []
  }
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
  tetrisReplayApproximate.value = isApproximateReplay(payload)
  tetrisReplayProgress.value = 0
  tetrisReplayVisible.value = true
  await nextTick()
  tetrisReplayRunner = createReplayRunner(payload.seed || res.data.seed, payload)
  paintTetrisReplayFrame()
}

const statsLoading = ref(false)

async function loadStatRecords(gameCode = statsGameCode.value, page = statPage.value) {
  const requestSequence = ++statsRequestSequence
  statsLoading.value = true
  try {
    const res = await getGameStatisticsRecords({
      gameCode,
      pageNum: page,
      pageSize: statPageSize.value,
    })
    if (requestSequence !== statsRequestSequence || gameCode !== statsGameCode.value) return
    if (res.code === 0 && res.data) {
      statRecords.value = unwrapPageRecords(res.data)
      statTotal.value = Number(res.data.total) || statRecords.value.length
      statPage.value = page
    }
  } finally {
    if (requestSequence === statsRequestSequence) {
      statsLoading.value = false
    }
  }
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
      syncMatchProfile('tetris_pk', res.data)
    }
  }
}

async function openStats(gameCode = 'gobang') {
  const targetCode = gameCode === 'tetris' ? 'gobang' : gameCode
  statsGameCode.value = targetCode
  statPage.value = 1
  statsVisible.value = true
  await loadProfileByGame(targetCode)
  await loadStatRecords(targetCode, 1)
}

async function openLeaderboard(gameCode = 'tetris') {
  leaderboardGameCode.value = gameCode
  leaderboardPage.value = 1
  leaderboardVisible.value = true
  await loadLeaderboard(gameCode)
}

async function onStatsGameChange(gameCode) {
  statsGameCode.value = gameCode
  statRecords.value = []
  statPage.value = 1
  await loadProfileByGame(gameCode)
  await loadStatRecords(gameCode, 1)
}

async function onLeaderboardGameChange(gameCode) {
  leaderboardGameCode.value = gameCode
  leaderboardPage.value = 1
  await loadLeaderboard(gameCode)
}

function onStatPageChange(page) {
  loadStatRecords(statsGameCode.value, page)
}

function onLeaderboardPageChange(page) {
  leaderboardPage.value = page
  loadLeaderboard(leaderboardGameCode.value)
}

function setWatchGame(gameCode) {
  if (watchGameCode.value === gameCode) return
  watchGameCode.value = gameCode
  watchKeyword.value = ''
  watchSearchKeyword.value = ''
  watchPage.value = 1
  void loadActiveRooms()
}

function searchWatchRooms() {
  watchSearchKeyword.value = watchKeyword.value.trim()
  watchPage.value = 1
  void loadActiveRooms()
}

function handleWatchSearchKeyup(event) {
  if (event?.key === 'Enter') {
    searchWatchRooms()
  }
}

function onWatchPageChange(page) {
  watchPage.value = page
  void loadActiveRooms()
}

function recordResultText(row) {
  if (statsGameCode.value === 'tetris') {
    return `得分 ${row.score ?? 0}`
  }
  if (row?.resultCode === 'WIN') return '胜利'
  if (row?.resultCode === 'LOSE') return '失败'
  return '平局'
}

function recordEndReasonText(row) {
  if (statsGameCode.value === 'tetris') return '自然结束'
  return endReasonText(row?.endReason)
}

function recordScoreDelta(row) {
  return Number(row?.scoreDelta) || 0
}

function formatScoreDelta(row) {
  if (statsGameCode.value === 'tetris') {
    return `${Number(row?.score) || 0} 分`
  }
  const delta = recordScoreDelta(row)
  return delta > 0 ? `+${delta}` : String(delta)
}

function recordRoleText(row) {
  if (statsGameCode.value === 'gobang') {
    const isWhite = row?.playerRole === 'WHITE' || row?.pieceColor === 'WHITE' || row?.chess === 2
    return isWhite ? '白子 (后手)' : '黑子 (先手)'
  }
  if (statsGameCode.value === 'jinzi') {
    const isO = row?.playerRole === 'WHITE' || row?.pieceColor === 'WHITE' || row?.chess === 2
    return isO ? '○ 棋 (后手)' : '× 棋 (先手)'
  }
  return '-'
}

function recordOpponentText(row) {
  if (row?.opponentNickname) return row.opponentNickname
  if (row?.opponentName) return row.opponentName
  if (row?.aiRoom || row?.isAi) return 'AI 对手'
  if (row?.opponentUserId) return `用户 ${row.opponentUserId}`
  return '对局玩家'
}

function recordScoreRatioText(row) {
  if (row?.scoreText) return row.scoreText
  if (row?.myWins != null && row?.opponentWins != null) {
    return `${row.myWins} : ${row.opponentWins}`
  }
  if (row?.blackWins != null && row?.whiteWins != null) {
    return `${row.blackWins} : ${row.whiteWins}`
  }
  return '-'
}

function recordLinesText(row) {
  const lines = row?.linesCleared ?? row?.lines ?? row?.clearedLines
  return lines != null ? `${lines} 行` : '-'
}

function recordLevelText(row) {
  const level = row?.level ?? row?.maxLevel
  return level != null ? `Lv.${level}` : '-'
}

function onLeaderboardAvatarError(event) {
  const image = event?.currentTarget
  if (!image || image.src === defaultAvatar) return
  image.src = defaultAvatar
}

function enterGobang() {
  handleMatchButtonClick('gobang', 'RANKED')
}

function enterJinzi() {
  handleMatchButtonClick('jinzi', 'RANKED')
}

function enterTetris() {
  if (matchingGameCode.value) {
    ElMessage.warning('正在匹配中，请先取消当前匹配')
    return
  }
  router.push('/games/tetris')
}

function enterTetrisPk() {
  handleMatchButtonClick('tetris_pk', 'RANKED')
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
  router.push('/community')
}

function openPointsCenter() {
  router.push('/points')
}

const defaultAvatar = DEFAULT_AVATAR

onMounted(async () => {
  await loadGameCategories()
  await Promise.all([refreshLobby(), refreshForumPointsBalance()])
  gameCenterInitialized = true
  lobbySocket.connect()
  startRefreshTimer()
})

// 只作为兜底：在线人数已经走推送，但心跳过期这种「无事件的变化」推不出来。
// 原来是 5 秒一轮，既没必要也是卡片区闪烁的来源
function startRefreshTimer() {
  if (refreshTimer) return
  refreshTimer = window.setInterval(() => {
    refreshLobby(true)
  }, 30000)
}

function stopRefreshTimer() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onActivated(() => {
  if (!gameCenterInitialized) return
  void refreshLobby(true)
  void refreshForumPointsBalance()
  lobbySocket.connect()
  startRefreshTimer()
})

// 这个页面被 keep-alive 缓存着，进房间时走的是 deactivate 而不是 unmount。
// 不在这里收尾的话，人在房间里，大厅的定时刷新和大厅长连接还在后台跑，
// 推送过来的房间变更还会触发一次观战列表请求
onDeactivated(() => {
  stopRefreshTimer()
  lobbySocket.close()
})

function createMatchSocket(gameCode, socketPath, roomRouteName) {
  return useGameWebSocket(socketPath, {
    onOpen() {
      if (!pendingMatchPayloads.has(gameCode)) return
      const payload = pendingMatchPayloads.get(gameCode)
      pendingMatchPayloads.delete(gameCode)
      if (!matchSockets[gameCode].send('start_match', payload)
        && matchingGameCode.value === gameCode) {
        matchingGameCode.value = ''
      }
    },
    onMessage(message) {
      if (!message?.ok) {
        if (message?.type === 'match_failed' || message?.type === 'match_rejected') {
          if (matchingGameCode.value === gameCode) matchingGameCode.value = ''
        }
        if (message?.message) ElMessage.warning(message.message)
        return
      }
      if (message.type === 'game_ready') {
        syncMatchProfile(gameCode, message.data || {})
        return
      }
      if (message.type === 'match_started') {
        matchingGameCode.value = gameCode
        return
      }
      if (message.type === 'match_stopped') {
        if (matchingGameCode.value === gameCode) matchingGameCode.value = ''
        return
      }
      if (message.type === 'match_success' && message.data?.roomId) {
        currentMatchRooms[gameCode] = String(message.data.roomId)
        matchingGameCode.value = ''
        router.push({ name: roomRouteName, params: { roomId: String(message.data.roomId) } })
      }
    },
    onClose() {
      pendingMatchPayloads.delete(gameCode)
      if (matchingGameCode.value === gameCode) matchingGameCode.value = ''
    },
    onError() {
      pendingMatchPayloads.delete(gameCode)
      if (matchingGameCode.value === gameCode) matchingGameCode.value = ''
    },
  })
}

const matchSockets = {
  gobang: createMatchSocket('gobang', 'games/gobang', 'gobangRoom'),
  jinzi: createMatchSocket('jinzi', 'games/jinzi', 'jinziRoom'),
  tetris_pk: createMatchSocket('tetris_pk', 'games/tetris', 'tetrisPkRoom'),
}

const gobangReplayVisible = ref(false)
const gobangReplayMoves = ref([])
const gobangReplayStep = ref(0)
const gobangReplayPlaying = ref(false)
const currentGobangReplayRow = ref(null)
let gobangReplayTimer = null

const gobangReplayRoleLines = computed(() => {
  const row = currentGobangReplayRow.value
  const isWhite = row?.playerRole === 'WHITE' || row?.pieceColor === 'WHITE' || row?.chess === 2
  if (isWhite) {
    return {
      mine: '我方执白棋 ○ (后手)',
      opponent: '对手执黑棋 ● (先手)',
    }
  }
  return {
    mine: '我方执黑棋 ● (先手)',
    opponent: '对手执白棋 ○ (后手)',
  }
})

async function openGobangReplay(row) {
  const recordId = row?.sourceRecordId || row?.id
  if (!recordId) return
  stopGobangReplay()
  currentGobangReplayRow.value = row
  try {
    const res = await getGobangReplay(recordId)
    if (res.code !== 0 || !res.data) {
      ElMessage.warning(res?.message || '五子棋对局回放数据不可用')
      return
    }
    const moves = Array.isArray(res.data) ? res.data : (res.data.moves || [])
    if (!moves.length) {
      ElMessage.info('暂无落子记录可回放')
      return
    }
    gobangReplayMoves.value = moves
    gobangReplayStep.value = moves.length
    gobangReplayVisible.value = true
  } catch (err) {
    ElMessage.warning('加载回放失败，请稍后重试')
  }
}

function stopGobangReplay() {
  if (gobangReplayTimer) {
    clearInterval(gobangReplayTimer)
    gobangReplayTimer = null
  }
  gobangReplayPlaying.value = false
}

function stepGobangReplay(delta) {
  stopGobangReplay()
  const next = gobangReplayStep.value + delta
  gobangReplayStep.value = Math.max(0, Math.min(gobangReplayMoves.value.length, next))
}

function toggleGobangReplayAuto() {
  if (gobangReplayPlaying.value) {
    stopGobangReplay()
    return
  }
  if (gobangReplayStep.value >= gobangReplayMoves.value.length) {
    gobangReplayStep.value = 0
  }
  gobangReplayPlaying.value = true
  gobangReplayTimer = setInterval(() => {
    if (gobangReplayStep.value < gobangReplayMoves.value.length) {
      gobangReplayStep.value++
    } else {
      stopGobangReplay()
    }
  }, 700)
}

const gobangReplayBoard = computed(() => {
  const board = Array.from({ length: 15 }, () => Array(15).fill(null))
  const steps = gobangReplayMoves.value.slice(0, gobangReplayStep.value)
  steps.forEach((m, idx) => {
    const r = m.row ?? m.x
    const c = m.col ?? m.y
    if (r >= 0 && r < 15 && c >= 0 && c < 15) {
      board[r][c] = {
        chess: m.chess || (idx % 2 === 0 ? 1 : 2),
        step: idx + 1,
      }
    }
  })
  return board
})

function formatNumber(num) {
  if (num == null) return '0'
  return Number(num).toLocaleString()
}

function avatarText(row) {
  const name = row?.opponentNickname || row?.opponentName || row?.nickname || row?.username || '玩'
  return name.slice(0, 1).toUpperCase()
}

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  stopTetrisReplay()
  stopGobangReplay()
  lobbySocket.close()
  Object.values(matchSockets).forEach((socket) => socket.close())
})
