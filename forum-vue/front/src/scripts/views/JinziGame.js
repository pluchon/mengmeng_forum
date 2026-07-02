import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, CircleClose, Timer, VideoPlay } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getJinziProfile,
  getJinziRecords,
  getJinziReplay,
} from '@/api/game'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useForumPointsBalance } from '@/composables/useForumPointsBalance'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'

let refreshTimer = null
let replayTimer = null

function emptyBoard() {
  return Array.from({ length: 3 }, () => Array.from({ length: 3 }, () => 0))
}

function formatDateTime(value) {
  const d = parseForumDateTime(value)
  if (!d) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function endReasonText(reason) {
  if (reason === 'LINE') return '三子连线'
  if (reason === 'DRAW') return '平局'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'DISCONNECT') return '断线'
  if (reason === 'TIMEOUT') return '超时'
  return reason || '—'
}

const router = useRouter()
const { pointsBalance, refreshForumPointsBalance } = useForumPointsBalance()
const loading = ref(false)
const matching = ref(false)
const profile = reactive({
  userId: null,
  score: 1000,
  totalCount: 0,
  winCount: 0,
  loseCount: 0,
  drawCount: 0,
  winRate: 0,
  rankName: '青铜 III',
  nextRankDistance: 100,
  currentStatus: 'IDLE',
  currentRoomId: '',
})
const overview = reactive({
  games: [],
  lobbyOnlineCount: 0,
})
const records = ref([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordPageSize = ref(8)
const replayVisible = ref(false)
const replayMoves = ref([])
const replayIndex = ref(0)
const replayBoard = ref(emptyBoard())
const replayPlaying = ref(false)

const gameSocket = useGameWebSocket('games/jinzi', {
  onMessage(message) {
    if (!message.ok) {
      if (message.message) ElMessage.warning(message.message)
      if (message.type === 'match_rejected') matching.value = false
      return
    }
    if (message.type === 'game_ready' && message.data) {
      Object.assign(profile, message.data)
      matching.value = profile.currentStatus === 'MATCHING'
    }
    if (message.type === 'match_started') {
      matching.value = true
      profile.currentStatus = 'MATCHING'
    }
    if (message.type === 'match_stopped') {
      matching.value = false
      profile.currentStatus = 'IDLE'
    }
    if (message.type === 'match_success' && message.data?.roomId) {
      matching.value = false
      profile.currentStatus = 'PLAYING'
      profile.currentRoomId = message.data.roomId
      router.push(`/games/jinzi/rooms/${encodeURIComponent(message.data.roomId)}`)
    }
  },
  onClose() {
    matching.value = false
  },
})

const totalCount = computed(() => Number(profile.totalCount) || 0)
const canResumeRoom = computed(() => profile.currentStatus === 'PLAYING' && profile.currentRoomId)
const jinziGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'jinzi') || {
    gameCode: 'jinzi',
    gameName: '井字',
    onlineCount: 0,
  },
)
const gameOnlineText = computed(() => `${Number(jinziGame.value.onlineCount) || 0}人`)
const rankNextText = computed(() => {
  const distance = Number(profile.nextRankDistance) || 0
  return distance > 0 ? `差 ${distance} 分` : '已达顶段'
})
const rankProgressPercent = computed(() => {
  const percent = Number(profile.rankInfo?.progressPercent)
  if (!Number.isFinite(percent)) return 0
  return Math.max(0, Math.min(100, percent))
})
const replayCurrentText = computed(() => {
  if (!replayMoves.value.length) return '0 / 0'
  return `${Math.min(replayIndex.value, replayMoves.value.length)} / ${replayMoves.value.length}`
})

function recordResultText(row) {
  if (!row.winnerUserId) return '平局'
  return row.winnerUserId === profile.userId ? '胜利' : '失败'
}

function scoreDeltaText(row) {
  const delta = recordScoreDelta(row)
  return delta > 0 ? `+${delta}` : String(delta)
}

function recordScoreDelta(row) {
  if (row?.viewerScoreDelta !== null && row?.viewerScoreDelta !== undefined) {
    return Number(row.viewerScoreDelta) || 0
  }
  if (!row?.winnerUserId) return 0
  const delta = Number(row.scoreDelta) || 0
  return row.winnerUserId === profile.userId ? delta : -delta
}

async function loadProfile() {
  const res = await getJinziProfile()
  if (res.code === 0 && res.data) {
    Object.assign(profile, res.data)
    matching.value = profile.currentStatus === 'MATCHING'
  }
}

async function loadOverview() {
  const res = await getGameCenterOverview()
  if (res.code === 0 && res.data) {
    overview.games = Array.isArray(res.data.games) ? res.data.games : []
    overview.lobbyOnlineCount = Number(res.data.lobbyOnlineCount) || 0
  }
}

async function loadRecords() {
  const res = await getJinziRecords({ pageNum: recordPage.value, pageSize: recordPageSize.value })
  if (res.code === 0 && res.data) {
    records.value = unwrapPageRecords(res.data)
    recordTotal.value = Number(res.data.total) || records.value.length
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadProfile(), loadRecords(), loadOverview(), refreshForumPointsBalance()])
  } finally {
    loading.value = false
  }
}

async function refreshSilent() {
  await Promise.all([loadProfile(), loadOverview(), refreshForumPointsBalance()])
}

function startMatch() {
  if (matching.value) return
  gameSocket.send('start_match', { mode: 'RANKED' })
}

function stopMatch() {
  if (!matching.value) return
  gameSocket.send('stop_match')
}

function resumeRoom() {
  if (!canResumeRoom.value) return
  router.push(`/games/jinzi/rooms/${encodeURIComponent(profile.currentRoomId)}`)
}

function backCenter() {
  if (matching.value) {
    gameSocket.send('stop_match')
    matching.value = false
    profile.currentStatus = 'IDLE'
  }
  router.push('/games')
}

function onRecordPageChange(page) {
  recordPage.value = page
  loadRecords()
}

function rebuildReplayBoard(index) {
  const board = emptyBoard()
  replayMoves.value.slice(0, index).forEach((move) => {
    const row = Number(move.row ?? move.rowIndex)
    const col = Number(move.col ?? move.colIndex)
    if (Number.isInteger(row) && Number.isInteger(col) && row >= 0 && row < 3 && col >= 0 && col < 3) {
      board[row][col] = Number(move.chess) || 0
    }
  })
  replayBoard.value = board
}

async function openReplay(row) {
  if (!row?.id) return
  const res = await getJinziReplay(row.id)
  if (res.code === 0 && res.data) {
    replayMoves.value = Array.isArray(res.data.moves) ? res.data.moves : []
    replayIndex.value = 0
    rebuildReplayBoard(0)
    stopReplayAuto()
    replayVisible.value = true
    await nextTick()
    toggleReplayAuto()
  }
}

function replayPrev() {
  stopReplayAuto()
  replayIndex.value = Math.max(0, replayIndex.value - 1)
  rebuildReplayBoard(replayIndex.value)
}

function replayNext() {
  replayIndex.value = Math.min(replayMoves.value.length, replayIndex.value + 1)
  rebuildReplayBoard(replayIndex.value)
  if (replayIndex.value >= replayMoves.value.length) {
    stopReplayAuto()
  }
}

function stopReplayAuto() {
  replayPlaying.value = false
  if (replayTimer) {
    window.clearInterval(replayTimer)
    replayTimer = null
  }
}

function toggleReplayAuto() {
  if (!replayMoves.value.length) return
  if (replayPlaying.value) {
    stopReplayAuto()
    return
  }
  if (replayIndex.value >= replayMoves.value.length) {
    replayIndex.value = 0
    rebuildReplayBoard(0)
  }
  replayPlaying.value = true
  replayTimer = window.setInterval(() => {
    replayNext()
  }, 700)
}

onMounted(async () => {
  await refreshAll()
  gameSocket.connect()
  refreshTimer = window.setInterval(refreshSilent, 5000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  stopReplayAuto()
  gameSocket.close()
})
