import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, CircleClose, Timer, VideoPlay } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getGobangActiveRooms,
  getGobangProfile,
  getGobangRecords,
  getGobangReplay,
} from '@/api/game'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useForumPointsBalance } from '@/composables/useForumPointsBalance'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'

function formatDateTime(value) {
  const d = parseForumDateTime(value)
  if (!d) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusText(status) {
  if (status === 'MATCHING') return '匹配中'
  if (status === 'PLAYING') return '对局中'
  return '空闲'
}

function endReasonText(reason) {
  if (reason === 'FIVE_IN_ROW') return '五子连珠'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'DISCONNECT_TIMEOUT') return '断线超时'
  return reason || '—'
}

const router = useRouter()
const { pointsBalance, refreshForumPointsBalance } = useForumPointsBalance()
const loading = ref(false)
const matching = ref(false)
let refreshTimer = null
let replayTimer = null
const profile = reactive({
  score: 1000,
  totalCount: 0,
  winCount: 0,
  loseCount: 0,
  drawCount: 0,
  winRate: 0,
  currentStatus: 'IDLE',
  currentRoomId: '',
})
const overview = reactive({
  games: [],
  lobbyOnlineCount: 0,
})
const activeRooms = ref([])
const records = ref([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordPageSize = ref(8)
const replayVisible = ref(false)
const replayMoves = ref([])
const replayIndex = ref(0)
const replayBoard = ref(emptyBoard())
const replayPlaying = ref(false)

const gameSocket = useGameWebSocket('games/gobang', {
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
      router.push(`/games/gobang/rooms/${encodeURIComponent(message.data.roomId)}`)
    }
  },
  onClose() {
    matching.value = false
  },
})

const winRateText = computed(() => `${Number(profile.winRate) || 0}%`)
const statusLabel = computed(() => statusText(profile.currentStatus))
const canResumeRoom = computed(() => profile.currentStatus === 'PLAYING' && profile.currentRoomId)
const gobangGame = computed(() =>
  overview.games.find((item) => item?.gameCode === 'gobang') || {
    gameCode: 'gobang',
    gameName: '五子棋',
    onlineCount: 0,
  },
)
const gameOnlineText = computed(() => `${Number(gobangGame.value.onlineCount) || 0}人`)
const activeRoomText = computed(() => `${activeRooms.value.length}间`)
const replayCurrentText = computed(() => {
  if (!replayMoves.value.length) return '0 / 0'
  return `${Math.min(replayIndex.value, replayMoves.value.length)} / ${replayMoves.value.length}`
})

function emptyBoard() {
  return Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => 0))
}

async function loadProfile() {
  const res = await getGobangProfile()
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

async function loadActiveRooms() {
  const res = await getGobangActiveRooms()
  if (res.code === 0) {
    activeRooms.value = Array.isArray(res.data) ? res.data : []
  }
}

async function loadRecords() {
  const res = await getGobangRecords({ pageNum: recordPage.value, pageSize: recordPageSize.value })
  if (res.code === 0 && res.data) {
    records.value = unwrapPageRecords(res.data)
    recordTotal.value = Number(res.data.total) || records.value.length
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadProfile(), loadRecords(), loadOverview(), loadActiveRooms(), refreshForumPointsBalance()])
  } finally {
    loading.value = false
  }
}

async function refreshSilent() {
  await Promise.all([loadProfile(), loadOverview(), loadActiveRooms(), refreshForumPointsBalance()])
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
  router.push(`/games/gobang/rooms/${encodeURIComponent(profile.currentRoomId)}`)
}

function backCenter() {
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
    if (Number.isInteger(row) && Number.isInteger(col) && row >= 0 && row < 15 && col >= 0 && col < 15) {
      board[row][col] = Number(move.chess) || 0
    }
  })
  replayBoard.value = board
}

async function openReplay(row) {
  if (!row?.id) return
  const res = await getGobangReplay(row.id)
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

defineExpose({
  Back,
  CircleClose,
  Timer,
  VideoPlay,
  activeRoomText,
  backCenter,
  canResumeRoom,
  endReasonText,
  formatDateTime,
  gameOnlineText,
  loading,
  matching,
  onRecordPageChange,
  openReplay,
  pointsBalance,
  profile,
  recordPage,
  recordPageSize,
  recordTotal,
  records,
  replayBoard,
  replayCurrentText,
  replayIndex,
  replayMoves,
  replayNext,
  replayPlaying,
  replayPrev,
  replayVisible,
  resumeRoom,
  startMatch,
  stopMatch,
  stopReplayAuto,
  toggleReplayAuto,
})
