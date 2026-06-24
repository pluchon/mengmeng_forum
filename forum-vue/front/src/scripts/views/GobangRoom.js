import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Flag, HomeFilled, MoreFilled, Timer, UserFilled } from '@element-plus/icons-vue'
import { getGobangRoom, surrenderGobangRoom } from '@/api/game'
import { getShopMyPacks } from '@/api/shop'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { modelIcon } from '@/constants/aiModels'

let timer = null
let finishRedirectTimer = null

function emptyBoard() {
  return Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => 0))
}

function endReasonText(reason) {
  if (reason === 'FIVE') return '五子连珠'
  if (reason === 'FIVE_IN_ROW') return '五子连珠'
  if (reason === 'SURRENDER') return '认输'
  if (reason === 'DISCONNECT') return '断线'
  if (reason === 'DISCONNECT_TIMEOUT') return '断线'
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
const clock = ref(Date.now())
const syncedAt = ref(Date.now())
const finishCountdown = ref(60)
const spectatorDialogVisible = ref(false)
const spectatorPage = ref(1)
const spectatorPageSize = 10
const emojiDialogVisible = ref(false)
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
  blackRemainingMs: 600000,
  whiteRemainingMs: 600000,
  moveRemainingMs: 60000,
  spectator: false,
  aiRoom: false,
  aiThinking: false,
  winningLine: [],
  blackPlayer: null,
  whitePlayer: null,
  opponentPlayer: null,
  spectators: [],
  spectatorCount: 0,
  roomOnlineCount: 0,
})

const roomSocket = useGameWebSocket(`games/gobang/rooms/${roomId.value}`, {
  onMessage(message) {
    if (!message.ok) {
      if (message.message) ElMessage.warning(message.message)
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
      scrollChatToBottom()
    }
    if (message.type === 'peer_disconnected') {
      peerStateText.value = '对手暂时离线，保留 60 秒重连窗口'
    }
    if (message.type === 'peer_reconnected') {
      peerStateText.value = '对手已重连'
      window.setTimeout(() => {
        if (peerStateText.value === '对手已重连') peerStateText.value = ''
      }, 2400)
    }
  },
})

const boardRows = computed(() => room.board || emptyBoard())
const isFinished = computed(() => room.roomStatus === 'FINISHED')
const isSpectator = computed(() => Boolean(room.spectator))
const isMyTurn = computed(() => !isFinished.value && !isSpectator.value && room.currentTurnUserId === room.thisUserId)
const isAiThinking = computed(() => {
  if (!room.aiRoom || isFinished.value || isSpectator.value) return false
  if (room.aiThinking) return true
  return !isMyTurn.value && room.roomStatus === 'PLAYING'
})
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
const mySideText = computed(() => {
  if (isSpectator.value) return '观战中'
  if (myChess.value === 1) return '黑棋'
  if (myChess.value === 2) return '白棋'
  return '—'
})
const opponentSideText = computed(() => {
  if (room.aiRoom) return '虚拟对手'
  if (isSpectator.value) return '对局双方'
  return myChess.value === 1 ? '白棋' : '黑棋'
})
const blackPlayer = computed(() => room.blackPlayer || fallbackParticipant(room.blackUserId, '黑方'))
const whitePlayer = computed(() => room.whitePlayer || fallbackParticipant(room.whiteUserId, room.aiRoom ? '同水平AI' : '白方'))
const primaryPlayerCard = computed(() => {
  if (isSpectator.value) {
    return {
      label: '黑方',
      title: participantDisplayName(blackPlayer.value),
      time: formatMs(blackLiveMs.value),
      chess: 1,
      turn: !isFinished.value && room.currentTurnUserId === room.blackUserId,
    }
  }
  return {
    label: '我方',
    title: mySideText.value,
    time: myTimeText.value,
    chess: myChess.value || 0,
    turn: isMyTurn.value,
  }
})
const secondaryPlayerCard = computed(() => {
  if (isSpectator.value) {
    return {
      label: '白方',
      title: participantDisplayName(whitePlayer.value),
      time: formatMs(whiteLiveMs.value),
      chess: 2,
      turn: !isFinished.value && room.currentTurnUserId === room.whiteUserId,
    }
  }
  return {
    label: '对手',
    title: opponentSideText.value,
    time: opponentTimeText.value,
    chess: myChess.value === 1 ? 2 : 1,
    turn: !isMyTurn.value && !isFinished.value,
  }
})
const opponentProfile = computed(() => {
  if (isSpectator.value) return null
  if (room.opponentPlayer) return room.opponentPlayer
  return myChess.value === 1 ? whitePlayer.value : blackPlayer.value
})
const visibleSpectators = computed(() => spectators.value.slice(0, 6))
const hiddenSpectatorCount = computed(() => Math.max(0, spectators.value.length - visibleSpectators.value.length))
const spectatorRows = computed(() => {
  const start = (spectatorPage.value - 1) * spectatorPageSize
  return spectators.value.slice(start, start + spectatorPageSize)
})
const spectators = computed(() => Array.isArray(room.spectators) ? room.spectators : [])
const winnerText = computed(() => {
  if (!isFinished.value) return ''
  if (!room.winnerUserId) return '本局结束'
  if (isSpectator.value) return room.winnerUserId === room.blackUserId ? '黑方获胜' : '白方获胜'
  return room.winnerUserId === room.thisUserId ? '你赢了' : '你输了'
})
const boardStatusText = computed(() => {
  if (isFinished.value) return `${winnerText.value} · ${endReasonText(room.endReason)}`
  if (isSpectator.value) return room.currentTurnUserId === room.blackUserId ? '黑方落子' : '白方落子'
  if (isMyTurn.value) return '轮到你落子'
  if (isAiThinking.value) return 'AI 思考中…'
  return '等待对手落子'
})
const finishCountdownText = computed(() => `${finishCountdown.value} 秒后返回五子棋`)
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
const myTimeText = computed(() => {
  if (myChess.value === 1) return formatMs(blackLiveMs.value)
  if (myChess.value === 2) return formatMs(whiteLiveMs.value)
  return '—'
})
const opponentTimeText = computed(() => {
  if (myChess.value === 1) return formatMs(whiteLiveMs.value)
  if (myChess.value === 2) return formatMs(blackLiveMs.value)
  return `${formatMs(blackLiveMs.value)} / ${formatMs(whiteLiveMs.value)}`
})
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

watch(() => chatMessages.value.length, () => {
  scrollChatToBottom()
})

function fallbackParticipant(userId, label) {
  return {
    userId,
    nickname: label,
    username: label,
    avatarUrl: '',
    vip: false,
    vipTier: 0,
    ai: userId === -1,
    aiModelName: userId === -1 ? 'deepseek-v4-flash · DeepSeek' : '',
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
  room.board = Array.isArray(data.board) ? data.board : emptyBoard()
  room.winnerUserId = data.winnerUserId ?? null
  room.endReason = data.endReason || ''
  room.blackRemainingMs = data.blackRemainingMs == null ? room.blackRemainingMs : Number(data.blackRemainingMs)
  room.whiteRemainingMs = data.whiteRemainingMs == null ? room.whiteRemainingMs : Number(data.whiteRemainingMs)
  room.moveRemainingMs = data.moveRemainingMs == null ? room.moveRemainingMs : Number(data.moveRemainingMs)
  room.spectator = Boolean(data.spectator)
  room.aiRoom = Boolean(data.aiRoom)
  room.aiThinking = Boolean(data.aiThinking)
  room.winningLine = Array.isArray(data.winningLine) ? data.winningLine : []
  room.blackPlayer = data.blackPlayer || null
  room.whitePlayer = data.whitePlayer || null
  room.opponentPlayer = data.opponentPlayer || null
  room.spectators = Array.isArray(data.spectators) ? data.spectators : []
  room.spectatorCount = Number(data.spectatorCount) || room.spectators.length
  room.roomOnlineCount = Number(data.roomOnlineCount) || 0
  syncedAt.value = Date.now()
  if (room.roomStatus === 'FINISHED') {
    void pointsWalletStore.refresh()
    startFinishRedirect()
  }
}

function applyMove(move) {
  settleLocalTurnTime()
  const row = Number(move.row)
  const col = Number(move.col)
  if (Number.isInteger(row) && Number.isInteger(col) && row >= 0 && row < 15 && col >= 0 && col < 15) {
    const nextBoard = room.board.map((line) => [...line])
    nextBoard[row][col] = Number(move.chess) || 0
    room.board = nextBoard
  }
  room.currentTurnUserId = move.nextTurnUserId ?? null
  room.moveRemainingMs = 60000
  syncedAt.value = Date.now()
  if (move.winnerUserId) {
    room.roomStatus = 'FINISHED'
    room.winnerUserId = move.winnerUserId
    room.endReason = move.endReason || 'FIVE'
    room.winningLine = Array.isArray(move.winningLine) ? move.winningLine : []
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
      router.push('/games/gobang')
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
    const res = await getGobangRoom(roomId.value)
    if (res.code === 0 && res.data) applyRoomState(res.data)
  } finally {
    loading.value = false
  }
}

async function loadEmojiPacks() {
  const res = await getShopMyPacks()
  if (res.code === 0) {
    emojiPacks.value = Array.isArray(res.data) ? res.data : []
  }
}

function play(row, col) {
  if (!isMyTurn.value || isFinished.value || isSpectator.value) return
  if (Number(room.board?.[row]?.[col]) !== 0) return
  if (roomSocket.socket.value?.readyState === WebSocket.OPEN) {
    roomSocket.send('move', { row, col })
    return
  }
  roomSocket.connect()
  ElMessage.info('正在连接实时对局，请稍候')
  window.setTimeout(() => {
    if (!isMyTurn.value || isFinished.value || isSpectator.value) return
    if (Number(room.board?.[row]?.[col]) !== 0) return
    roomSocket.send('move', { row, col })
  }, 600)
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
  const sent = roomSocket.send('chat', {
    messageType: 'EMOJI',
    content: url,
    emojiUrl: url,
  })
  if (sent) emojiDialogVisible.value = false
}

function participantName(userId) {
  const candidates = [
    blackPlayer.value,
    whitePlayer.value,
    ...spectators.value,
  ]
  const found = candidates.find((item) => item?.userId === userId)
  return found?.nickname || found?.username || `用户 ${userId}`
}

function participantDisplayName(player) {
  return player?.nickname || player?.username || (player?.ai ? '同水平AI' : '等待玩家')
}

function avatarText(player) {
  const name = player?.nickname || player?.username || '棋'
  return name.slice(0, 1).toUpperCase()
}

function aiModelCode(player) {
  const text = String(player?.aiModelName || '')
  return text.includes('deepseek-v4-pro') ? 'deepseek-v4-pro' : 'deepseek-v4-flash'
}

function aiModelIcon(player) {
  return modelIcon(aiModelCode(player))
}

function openOpponentStats() {
  if (isSpectator.value || !opponentProfile.value) return
  opponentStatsVisible.value = true
}

async function surrender() {
  if (isFinished.value || surrendering.value || isSpectator.value) return
  try {
    await ElMessageBox.confirm('确认认输并结束本局吗？', '五子棋', {
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
      await surrenderGobangRoom(roomId.value)
      await loadRoom()
    }
  } finally {
    surrendering.value = false
  }
}

async function backGame() {
  if (!isFinished.value) {
    try {
      await ElMessageBox.confirm('确认离开当前对局吗？离开后可从匹配页回到进行中的房间。', '离开对局', {
        type: 'warning',
        confirmButtonText: '离开',
        cancelButtonText: '继续对局',
      })
    } catch {
      return
    }
  }
  router.push('/games')
}

onMounted(async () => {
  await Promise.all([loadRoom(), loadEmojiPacks()])
  roomSocket.connect()
  timer = window.setInterval(() => {
    clock.value = Date.now()
  }, 500)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (finishRedirectTimer) window.clearInterval(finishRedirectTimer)
  roomSocket.close()
})
