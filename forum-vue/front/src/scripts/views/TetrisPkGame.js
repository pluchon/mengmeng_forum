import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, CircleClose, Timer, VideoPlay } from '@element-plus/icons-vue'
import {
  getGameCenterOverview,
  getTetrisPkActiveRooms,
  getTetrisPkLeaderboard,
  getTetrisPkProfile,
  getTetrisPkRecords,
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

function endReasonText(reason) {
  if (reason === 'LINE') return '方块堆顶'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'DISCONNECT') return '断线'
  return reason || '—'
}

function formatScoreDelta(delta) {
  const value = Number(delta ?? 0)
  if (value > 0) return `+${value}`
  return String(value)
}

function useTetrisPkGame() {
  const router = useRouter()
  const { pointsBalance, refreshForumPointsBalance } = useForumPointsBalance()
  const loading = ref(false)
  const matching = ref(false)
  let refreshTimer = null
  const profile = reactive({
    score: 1000,
    totalCount: 0,
    winCount: 0,
    loseCount: 0,
    winRate: 0,
    rankName: '青铜 III',
    nextRankDistance: 100,
    currentStatus: 'IDLE',
    currentRoomId: '',
  })
  const overview = reactive({ games: [], lobbyOnlineCount: 0 })
  const activeRooms = ref([])
  const records = ref([])
  const recordTotal = ref(0)
  const recordPage = ref(1)
  const recordPageSize = ref(8)
  const leaderboard = ref([])

  const gameSocket = useGameWebSocket('games/tetris', {
    onMessage(message) {
      if (!message.ok) {
        if (message.message) ElMessage.warning(message.message)
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
        router.push(`/games/tetris/pk/rooms/${encodeURIComponent(message.data.roomId)}`)
      }
    },
    onClose() {
      matching.value = false
    },
  })

  const canResumeRoom = computed(() => profile.currentStatus === 'PLAYING' && profile.currentRoomId)
  const tetrisPkGame = computed(() =>
    overview.games.find((item) => item?.gameCode === 'tetris_pk') || {
      gameCode: 'tetris_pk',
      gameName: '俄罗斯方块PK',
      onlineCount: 0,
    },
  )
  const gameOnlineText = computed(() => `${Number(tetrisPkGame.value.onlineCount) || 0}人`)
  const activeRoomText = computed(() => `${activeRooms.value.length}间`)
  const rankNextText = computed(() => {
    const distance = Number(profile.nextRankDistance) || 0
    return distance > 0 ? `差 ${distance} 分` : '已达顶段'
  })
  const rankProgressPercent = computed(() => {
    const percent = Number(profile.rankInfo?.progressPercent)
    if (!Number.isFinite(percent)) return 0
    return Math.max(0, Math.min(100, percent))
  })

  async function loadProfile() {
    const res = await getTetrisPkProfile()
    if (res.code === 0 && res.data) {
      Object.assign(profile, res.data)
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
    const res = await getTetrisPkActiveRooms()
    if (res.code === 0) {
      activeRooms.value = Array.isArray(res.data) ? res.data : []
    }
  }

  async function loadRecords() {
    const res = await getTetrisPkRecords({ pageNum: recordPage.value, pageSize: recordPageSize.value })
    if (res.code === 0 && res.data) {
      records.value = unwrapPageRecords(res.data)
      recordTotal.value = Number(res.data.total) || records.value.length
    }
  }

  async function loadLeaderboard() {
    const res = await getTetrisPkLeaderboard({ pageSize: 20 })
    if (res.code === 0) {
      leaderboard.value = Array.isArray(res.data) ? res.data : []
    }
  }

  async function refreshAll() {
    loading.value = true
    try {
      await Promise.all([
        loadProfile(),
        loadRecords(),
        loadLeaderboard(),
        loadOverview(),
        loadActiveRooms(),
        refreshForumPointsBalance(),
      ])
    } finally {
      loading.value = false
    }
  }

  async function refreshSilent() {
    await Promise.all([loadProfile(), loadOverview(), loadActiveRooms(), refreshForumPointsBalance()])
  }

  function startMatch() {
    if (matching.value) return
    gameSocket.send('start_match')
  }

  function stopMatch() {
    if (!matching.value) return
    gameSocket.send('stop_match')
  }

  function resumeRoom() {
    if (!canResumeRoom.value) return
    router.push(`/games/tetris/pk/rooms/${encodeURIComponent(profile.currentRoomId)}`)
  }

  function watchRoom(row) {
    if (!row?.roomId) return
    router.push(`/games/tetris/pk/rooms/${encodeURIComponent(row.roomId)}`)
  }

  function backCenter() {
    router.push('/games')
  }

  function onRecordPageChange(page) {
    recordPage.value = page
    loadRecords()
  }

  onMounted(async () => {
    await refreshAll()
    gameSocket.connect()
    refreshTimer = window.setInterval(refreshSilent, 5000)
  })

  onUnmounted(() => {
    if (refreshTimer) window.clearInterval(refreshTimer)
    gameSocket.close()
  })

  return {
    Back,
    CircleClose,
    Timer,
    VideoPlay,
    activeRoomText,
    activeRooms,
    backCenter,
    canResumeRoom,
    endReasonText,
    formatDateTime,
    formatScoreDelta,
    gameOnlineText,
    leaderboard,
    loading,
    matching,
    onRecordPageChange,
    pointsBalance,
    profile,
    rankProgressPercent,
    rankNextText,
    recordPage,
    recordPageSize,
    recordTotal,
    records,
    resumeRoom,
    startMatch,
    stopMatch,
    watchRoom,
  }
}

import TetrisCoverBoard from '@/components/game/TetrisCoverBoard.vue'

const {
  activeRoomText,
  activeRooms,
  backCenter,
  canResumeRoom,
  gameOnlineText,
  loading,
  matching,
  onRecordPageChange,
  pointsBalance,
  profile,
  rankProgressPercent,
  rankNextText,
  recordPage,
  recordPageSize,
  recordTotal,
  records,
  resumeRoom,
  startMatch,
  stopMatch,
  watchRoom,
} = useTetrisPkGame()
