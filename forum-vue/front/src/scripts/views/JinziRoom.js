import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import { ChatDotRound, Flag, HomeFilled, MoreFilled, Timer } from '@element-plus/icons-vue'
import { getJinziRoom, surrenderJinziRoom } from '@/api/game'
import PurchasedEmojiPackPopover from '@/components/common/PurchasedEmojiPackPopover.vue'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { usePointsWalletStore } from '@/stores/pointsWallet'

let timer = null
let finishRedirectTimer = null
let roundNextTimer = null
const roundNextCountdown = ref(5)

function startRoundNextCountdown() {
  if (roundNextTimer) clearInterval(roundNextTimer)
  roundNextCountdown.value = 5
  roundNextTimer = window.setInterval(() => {
    roundNextCountdown.value = Math.max(1, roundNextCountdown.value - 1)
  }, 1000)
}

function stopRoundNextCountdown() {
  if (roundNextTimer) {
    clearInterval(roundNextTimer)
    roundNextTimer = null
  }
  roundNextCountdown.value = 5
}

function emptyBoard() {
  return Array.from({ length: 3 }, () => Array.from({ length: 3 }, () => 0))
}

function normalizeBoard(board) {
  if (!Array.isArray(board) || board.length !== 3 || board.some((row) => !Array.isArray(row) || row.length !== 3)) {
    return emptyBoard()
  }
  return board.map((row) => row.map((cell) => (Number(cell) === 1 || Number(cell) === 2 ? Number(cell) : 0)))
}

function endReasonText(reason) {
  if (reason === 'LINE') return '三子连线'
  if (reason === 'DRAW') return '平局'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'DISCONNECT') return '断线'
  if (reason === 'TIMEOUT') return '超时'
  return reason || '—'
}

function formatMs(value) {
  const safe = Math.max(0, Number(value) || 0)
  const totalSeconds = Math.ceil(safe / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

const route = useRoute()
const router = useRouter()
const pointsWalletStore = usePointsWalletStore()
const roomId = computed(() => String(route.params.roomId || ''))
const loading = ref(false)
const surrendering = ref(false)
const peerStateText = ref('')
const chatText = ref('')
const chatMessages = ref([])
const chatListRef = ref(null)
const emojiPacks = ref([])
const emojiDialogVisible = ref(false)
const clock = ref(Date.now())
const syncedAt = ref(Date.now())
const finishCountdown = ref(60)
const opponentStatsVisible = ref(false)
const room = reactive({
  roomId: '',
  thisUserId: null,
  opponentUserId: null,
  blackUserId: null,
  whiteUserId: null,
  currentTurnUserId: null,
  roomStatus: 'WAITING',
  board: emptyBoard(),
  winnerUserId: null,
  endReason: '',
  blackWins: 0,
  whiteWins: 0,
  drawRounds: 0,
  currentRound: 1,
  roundStartingChess: 1,
  roundFinished: false,
  roundWinnerUserId: null,
  roundEndReason: '',
  blackRemainingMs: 120000,
  whiteRemainingMs: 120000,
  moveRemainingMs: 20000,
  winningLine: [],
  blackPlayer: null,
  whitePlayer: null,
  opponentPlayer: null,
  roomOnlineCount: 0,
})

const roundToastText = ref('')

const roomSocket = useGameWebSocket(`games/jinzi/rooms/${roomId.value}`, {
  onMessage(message) {
    if (!message.ok) {
      if (message.message) ElMessage.warning(message.message)
      return
    }
    if (message.type === 'round_started' && message.data) {
      stopRoundNextCountdown()
      applyRoomState(message.data)
      roundToastText.value = `第 ${room.currentRound} 小局开始！`
      window.setTimeout(() => {
        if (roundToastText.value.startsWith('第')) roundToastText.value = ''
      }, 2000)
      return
    }
    if ((message.type === 'room_ready' || message.type === 'room_state_updated' || message.type === 'game_finished') && message.data) {
      applyRoomState(message.data)
    }
    if (message.type === 'move_accepted' && message.data) {
      applyMove(message.data)
    }
    if (message.type === 'room_chat' && message.data) {
      chatMessages.value.push(message.data)
    }
    // 通知只带 userId，得分清是自己还是对手
    if (message.type === 'peer_disconnected') {
      if (Number(message.data?.userId) !== Number(room.thisUserId)) {
        peerStateText.value = '对手暂时离线，保留 30 秒重连窗口'
      }
    }
    if (message.type === 'peer_reconnected') {
      const isMe = Number(message.data?.userId) === Number(room.thisUserId)
      const text = isMe ? '已重新连上' : '对手已重连'
      peerStateText.value = text
      window.setTimeout(() => {
        if (peerStateText.value === text) peerStateText.value = ''
      }, 2400)
    }
  },
  // 断线期间棋钟没有停，回来得按服务端的状态重画，而不是接着用断线前的棋盘
  onReconnect() {
    void loadRoom()
  },
  onReconnectFailed() {
    peerStateText.value = '连接已断开'
    ElMessage.error('实时连接断开且重连失败，请刷新页面')
  },
})

const boardRows = computed(() => room.board || emptyBoard())
const isFinished = computed(() => room.roomStatus === 'FINISHED')
const isMyTurn = computed(() => !isFinished.value && !room.roundFinished && room.currentTurnUserId === room.thisUserId)
const currentTurnChess = computed(() => {
  if (room.currentTurnUserId === room.blackUserId) return 1
  if (room.currentTurnUserId === room.whiteUserId) return 2
  return 0
})
const myChess = computed(() => {
  if (room.thisUserId === room.blackUserId) return 1
  if (room.thisUserId === room.whiteUserId) return 2
  return 0
})
const blackPlayer = computed(() => room.blackPlayer || fallbackParticipant(room.blackUserId, '×方'))
const whitePlayer = computed(() => room.whitePlayer || fallbackParticipant(room.whiteUserId, '○方'))
const opponentProfile = computed(() => room.opponentPlayer || (myChess.value === 1 ? whitePlayer.value : blackPlayer.value))
const myWins = computed(() => myChess.value === 1 ? room.blackWins : room.whiteWins)
const opponentWins = computed(() => myChess.value === 1 ? room.whiteWins : room.blackWins)
const primaryPlayerCard = computed(() => ({
  label: '我方',
  title: myChess.value === 1 ? '×' : '○',
  time: myTimeText.value,
  chess: myChess.value || 0,
  wins: myWins.value,
  turn: isMyTurn.value,
}))
const secondaryPlayerCard = computed(() => ({
  label: '对手',
  title: myChess.value === 1 ? '○' : '×',
  time: opponentTimeText.value,
  chess: myChess.value === 1 ? 2 : 1,
  wins: opponentWins.value,
  turn: !isMyTurn.value && !isFinished.value && !room.roundFinished,
}))
const winnerText = computed(() => {
  if (!isFinished.value) return ''
  if (room.winnerUserId) {
    return room.winnerUserId === room.thisUserId ? '恭喜获胜！' : '惜败对手'
  }
  if (myWins.value > opponentWins.value) return '恭喜获胜！'
  if (myWins.value < opponentWins.value) return '惜败对手'
  // 胜局数相同就是平局。原来这里按「我是不是黑方」来判，黑方会看到「恭喜获胜」
  return '战成平局'
})
const roundStatusText = computed(() => {
  if (isFinished.value) return winnerText.value
  if (room.roundFinished) {
    if (!room.roundWinnerUserId) return '本小局平局，即将开始下一局'
    const mine = room.roundWinnerUserId === room.thisUserId
    // 超时输掉的小局要说清楚是超时，不然玩家会以为自己被连成一线了
    if (room.roundEndReason === 'TIMEOUT') {
      return mine ? '对手超时，本小局胜利！' : '你超时了，本小局失利！'
    }
    return mine ? '本小局胜利！' : '本小局失利！'
  }
  if (isMyTurn.value) return '轮到你落子'
  return '等待对手落子'
})
const finishCountdownText = computed(() => `${finishCountdown.value} 秒后返回井字`)
const turnElapsed = computed(() => Math.max(0, clock.value - syncedAt.value))
const blackLiveMs = computed(() =>
  room.currentTurnUserId === room.blackUserId
    ? Math.max(0, Number(room.blackRemainingMs) - turnElapsed.value)
    : Number(room.blackRemainingMs) || 0,
)
const whiteLiveMs = computed(() =>
  room.currentTurnUserId === room.whiteUserId
    ? Math.max(0, Number(room.whiteRemainingMs) - turnElapsed.value)
    : Number(room.whiteRemainingMs) || 0,
)
const moveLiveMs = computed(() => Math.max(0, Number(room.moveRemainingMs) - turnElapsed.value))
const myTimeText = computed(() => myChess.value === 1 ? formatMs(blackLiveMs.value) : formatMs(whiteLiveMs.value))
const opponentTimeText = computed(() => myChess.value === 1 ? formatMs(whiteLiveMs.value) : formatMs(blackLiveMs.value))
const moveTimeText = computed(() => formatMs(moveLiveMs.value))
const canChat = computed(() => room.roomStatus === 'PLAYING' || room.roomStatus === 'FINISHED')
const emojiImages = computed(() => {
  const rows = []
  emojiPacks.value.forEach((pack) => {
    const urls = Array.isArray(pack.imageUrls) && pack.imageUrls.length ? pack.imageUrls : [pack.coverUrl]
    urls.filter(Boolean).forEach((url, index) => {
      rows.push({
        key: `${pack.userEmojiId}-${index}`,
        name: pack.name,
        url,
      })
    })
  })
  return rows
})
const visibleEmojiImages = computed(() => emojiImages.value.slice(0, 6))
const hasMoreEmoji = computed(() => emojiImages.value.length > visibleEmojiImages.value.length)

function scrollChatToBottom() {
  nextTick(() => {
    const el = chatListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(
  () => chatMessages.value.length,
  () => scrollChatToBottom(),
)

function fallbackParticipant(userId, label) {
  return {
    userId,
    nickname: label,
    username: label,
    avatarUrl: '',
    vip: false,
  }
}

function applyRoomState(data) {
  room.roomId = data.roomId || roomId.value
  room.thisUserId = data.thisUserId ?? room.thisUserId
  room.opponentUserId = data.opponentUserId ?? room.opponentUserId
  room.blackUserId = data.blackUserId ?? room.blackUserId
  room.whiteUserId = data.whiteUserId ?? room.whiteUserId
  room.currentTurnUserId = data.currentTurnUserId ?? null
  room.roomStatus = data.roomStatus || room.roomStatus
  room.board = normalizeBoard(data.board)
  room.winnerUserId = data.winnerUserId ?? null
  room.endReason = data.endReason || ''
  room.blackWins = Number(data.blackWins) || 0
  room.whiteWins = Number(data.whiteWins) || 0
  room.drawRounds = Number(data.drawRounds) || 0
  room.currentRound = Number(data.currentRound) || 1
  room.roundStartingChess = Number(data.roundStartingChess) || 1
  room.roundFinished = Boolean(data.roundFinished)
  room.roundWinnerUserId = data.roundWinnerUserId ?? null
  room.roundEndReason = data.roundEndReason || ''
  room.blackRemainingMs = data.blackRemainingMs == null ? room.blackRemainingMs : Number(data.blackRemainingMs)
  room.whiteRemainingMs = data.whiteRemainingMs == null ? room.whiteRemainingMs : Number(data.whiteRemainingMs)
  room.moveRemainingMs = data.moveRemainingMs == null ? room.moveRemainingMs : Number(data.moveRemainingMs)
  room.winningLine = Array.isArray(data.winningLine) ? data.winningLine : []
  room.blackPlayer = data.blackPlayer || null
  room.whitePlayer = data.whitePlayer || null
  room.opponentPlayer = data.opponentPlayer || null
  room.roomOnlineCount = Number(data.roomOnlineCount) || 0
  syncedAt.value = Date.now()

  if (room.roundFinished && room.roomStatus !== 'FINISHED') {
    startRoundNextCountdown()
  } else if (!room.roundFinished) {
    stopRoundNextCountdown()
  }

  if (room.roomStatus === 'FINISHED') {
    stopRoundNextCountdown()
    void pointsWalletStore.refresh()
    startFinishRedirect()
  }
}

function applyMove(move) {
  settleLocalTurnTime()
  const row = Number(move.row)
  const col = Number(move.col)
  if (Number.isInteger(row) && Number.isInteger(col) && row >= 0 && row < 3 && col >= 0 && col < 3) {
    const nextBoard = room.board.map((line) => [...line])
    nextBoard[row][col] = Number(move.chess) || 0
    room.board = nextBoard
  }
  room.currentTurnUserId = move.nextTurnUserId ?? null
  room.moveRemainingMs = 20000
  syncedAt.value = Date.now()

  if (move.blackWins != null) room.blackWins = Number(move.blackWins)
  if (move.whiteWins != null) room.whiteWins = Number(move.whiteWins)
  if (move.drawRounds != null) room.drawRounds = Number(move.drawRounds)
  if (move.currentRound != null) room.currentRound = Number(move.currentRound)
  if (move.winningLine) room.winningLine = Array.isArray(move.winningLine) ? move.winningLine : []

  if (move.roundFinished && !move.matchFinished) {
    room.roundFinished = true
    room.roundWinnerUserId = move.roundWinnerUserId ?? null
    room.roundEndReason = move.roundEndReason || ''
    startRoundNextCountdown()
  }

  if (move.matchFinished) {
    stopRoundNextCountdown()
    room.roomStatus = 'FINISHED'
    room.winnerUserId = move.matchWinnerUserId ?? null
    room.endReason = move.matchEndReason || ''
    void pointsWalletStore.refresh()
    startFinishRedirect()
  }
}

function settleLocalTurnTime() {
  const elapsed = Math.max(0, Date.now() - syncedAt.value)
  if (room.currentTurnUserId === room.blackUserId) {
    room.blackRemainingMs = Math.max(0, Number(room.blackRemainingMs) - elapsed)
  } else if (room.currentTurnUserId === room.whiteUserId) {
    room.whiteRemainingMs = Math.max(0, Number(room.whiteRemainingMs) - elapsed)
  }
}

function startFinishRedirect() {
  if (finishRedirectTimer) return
  finishCountdown.value = 60
  finishRedirectTimer = window.setInterval(() => {
    finishCountdown.value = Math.max(0, finishCountdown.value - 1)
    if (finishCountdown.value <= 0) {
      window.clearInterval(finishRedirectTimer)
      finishRedirectTimer = null
      router.push({ name: 'jinziGame' })
    }
  }, 1000)
}

function isWinningCell(row, col) {
  return Array.isArray(room.winningLine)
    && room.winningLine.some((point) => Number(point.row) === row && Number(point.col) === col)
}

async function loadRoom() {
  loading.value = true
  try {
    const res = await getJinziRoom(roomId.value)
    if (res.code === 0 && res.data) applyRoomState(res.data)
  } finally {
    loading.value = false
  }
}

function play(row, col) {
  if (!isMyTurn.value || isFinished.value) return
  if (Number(room.board?.[row]?.[col]) !== 0) return
  if (roomSocket.socket.value?.readyState === WebSocket.OPEN) {
    roomSocket.send('move', { row, col })
    return
  }
  roomSocket.connect()
  ElMessage.info('正在连接实时对局，请稍候')
}

function sendChat() {
  const content = chatText.value.trim()
  if (!content || !canChat.value) return
  if (roomSocket.send('chat', { messageType: 'TEXT', content })) {
    chatText.value = ''
  }
}

function sendEmoji(url) {
  if (!url || !canChat.value) return
  roomSocket.send('chat', {
    messageType: 'EMOJI',
    content: url,
    emojiUrl: url,
  })
}

function participantName(userId) {
  const found = [blackPlayer.value, whitePlayer.value].find((item) => item?.userId === userId)
  return found?.nickname || found?.username || `用户 ${userId}`
}

function avatarText(player) {
  const name = player?.nickname || player?.username || '棋'
  return name.slice(0, 1).toUpperCase()
}

function openOpponentStats() {
  if (!opponentProfile.value) return
  opponentStatsVisible.value = true
}

async function surrender() {
  if (isFinished.value || surrendering.value) return
  try {
    await confirmDialog('确认认输并结束本局吗？', '井字', {
      type: 'warning',
      confirmButtonText: '认输',
      cancelButtonText: '继续对局',
    })
  } catch {
    return
  }
  surrendering.value = true
  try {
    if (!roomSocket.send('surrender')) {
      await surrenderJinziRoom(roomId.value)
      await loadRoom()
    }
  } finally {
    surrendering.value = false
  }
}

async function backGame() {
  if (!isFinished.value) {
    try {
      await confirmDialog('确认离开当前对局吗？离开后本局仍会继续进行。', '离开对局', {
        type: 'warning',
        confirmButtonText: '离开',
        cancelButtonText: '继续对局',
      })
    } catch {
      return
    }
  }
  router.push({ name: 'jinziGame' })
}

onMounted(async () => {
  await loadRoom()
  roomSocket.connect()
  timer = window.setInterval(() => {
    clock.value = Date.now()
  }, 500)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (finishRedirectTimer) window.clearInterval(finishRedirectTimer)
  stopRoundNextCountdown()
  roomSocket.close()
})
