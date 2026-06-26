import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Flag, HomeFilled, MoreFilled, Timer } from '@element-plus/icons-vue'
import { getJinziRoom, surrenderJinziRoom } from '@/api/game'
import PurchasedEmojiPackPopover from '@/components/common/PurchasedEmojiPackPopover.vue'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { modelIcon } from '@/constants/aiModels'

let timer = null
let finishRedirectTimer = null

function emptyBoard() {
  return Array.from({ length: 3 }, () => Array.from({ length: 3 }, () => 0))
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
  blackRemainingMs: 120000,
  whiteRemainingMs: 120000,
  moveRemainingMs: 20000,
  aiRoom: false,
  aiThinking: false,
  winningLine: [],
  blackPlayer: null,
  whitePlayer: null,
  opponentPlayer: null,
  roomOnlineCount: 0,
})

const roomSocket = useGameWebSocket(`games/jinzi/rooms/${roomId.value}`, {
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
    }
    if (message.type === 'peer_disconnected') {
      peerStateText.value = '对手暂时离线，保留 30 秒重连窗口'
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
const isMyTurn = computed(() => !isFinished.value && room.currentTurnUserId === room.thisUserId)
const isAiThinking = computed(() => {
  if (!room.aiRoom || isFinished.value) return false
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
const blackPlayer = computed(() => room.blackPlayer || fallbackParticipant(room.blackUserId, '×方'))
const whitePlayer = computed(() => room.whitePlayer || fallbackParticipant(room.whiteUserId, room.aiRoom ? '同水平AI' : '○方'))
const opponentProfile = computed(() => room.opponentPlayer || (myChess.value === 1 ? whitePlayer.value : blackPlayer.value))
const primaryPlayerCard = computed(() => ({
  label: '我方',
  title: myChess.value === 1 ? '×' : '○',
  time: myTimeText.value,
  chess: myChess.value || 0,
  turn: isMyTurn.value,
}))
const secondaryPlayerCard = computed(() => ({
  label: '对手',
  title: myChess.value === 1 ? '○' : '×',
  time: opponentTimeText.value,
  chess: myChess.value === 1 ? 2 : 1,
  turn: !isMyTurn.value && !isFinished.value,
}))
const winnerText = computed(() => {
  if (!isFinished.value) return ''
  if (!room.winnerUserId) return '平局'
  return room.winnerUserId === room.thisUserId ? '你赢了' : '你输了'
})
const boardStatusText = computed(() => {
  if (isFinished.value) return winnerText.value
  if (isMyTurn.value) return '轮到你落子'
  if (isAiThinking.value) return 'AI 思考中…'
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
  room.aiRoom = Boolean(data.aiRoom)
  room.aiThinking = Boolean(data.aiThinking)
  room.winningLine = Array.isArray(data.winningLine) ? data.winningLine : []
  room.blackPlayer = data.blackPlayer || null
  room.whitePlayer = data.whitePlayer || null
  room.opponentPlayer = data.opponentPlayer || null
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
  if (Number.isInteger(row) && Number.isInteger(col) && row >= 0 && row < 3 && col >= 0 && col < 3) {
    const nextBoard = room.board.map((line) => [...line])
    nextBoard[row][col] = Number(move.chess) || 0
    room.board = nextBoard
  }
  room.currentTurnUserId = move.nextTurnUserId ?? null
  room.moveRemainingMs = 20000
  syncedAt.value = Date.now()
  if (move.endReason) {
    room.roomStatus = 'FINISHED'
    room.winnerUserId = move.winnerUserId ?? null
    room.endReason = move.endReason
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
      router.push('/games/jinzi')
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

function aiModelCode(player) {
  const text = String(player?.aiModelName || '')
  return text.includes('deepseek-v4-pro') ? 'deepseek-v4-pro' : 'deepseek-v4-flash'
}

function aiModelIcon(player) {
  return modelIcon(aiModelCode(player))
}

function openOpponentStats() {
  if (!opponentProfile.value) return
  opponentStatsVisible.value = true
}

async function surrender() {
  if (isFinished.value || surrendering.value) return
  try {
    await ElMessageBox.confirm('确认认输并结束本局吗？', '井字', {
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
      await ElMessageBox.confirm('确认离开当前对局吗？离开后可从匹配页回到进行中的房间。', '离开对局', {
        type: 'warning',
        confirmButtonText: '离开',
        cancelButtonText: '继续对局',
      })
    } catch {
      return
    }
  }
  router.push('/games/jinzi')
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
  roomSocket.close()
})
