import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Flag, HomeFilled, MoreFilled, UserFilled } from '@element-plus/icons-vue'
import { getTetrisPkRoom, surrenderTetrisPkRoom } from '@/api/game'
import PurchasedEmojiPackPopover from '@/components/common/PurchasedEmojiPackPopover.vue'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { drawBoard, drawPreview } from '@/scripts/games/tetris/canvas'

const CELL_SIZE = 20
const BOARD_WIDTH = 10 * CELL_SIZE
const BOARD_HEIGHT = 20 * CELL_SIZE
const KEY_MAP = {
  ArrowLeft: 'left',
  ArrowRight: 'right',
  ArrowDown: 'down',
  ArrowUp: 'rotate',
  ' ': 'space',
  s: 'hold',
  S: 'hold',
}

function emptyRoom() {
  return {
    roomId: '',
    thisUserId: null,
    opponentUserId: null,
    player1UserId: null,
    player2UserId: null,
    redUserId: null,
    blueUserId: null,
    roomStatus: 'WAITING',
    winnerUserId: null,
    endReason: '',
    spectator: false,
    myBoard: null,
    opponentBoard: null,
    redScore: 0,
    blueScore: 0,
    pkBarLeftPercent: 50,
    opponentPlayer: null,
    player1: null,
    player2: null,
    spectators: [],
    spectatorCount: 0,
  }
}

function normalizeMatrix(matrix) {
  if (!Array.isArray(matrix)) return Array.from({ length: 20 }, () => Array(10).fill(''))
  return matrix.map((row) => (Array.isArray(row) ? row.map((cell) => cell || '') : Array(10).fill('')))
}

function normalizePiece(piece) {
  if (!piece) return null
  return {
    type: piece.type,
    xy: piece.xy || [0, 0],
    shape: piece.shape || [],
  }
}

function useTetrisPkRoom() {
  const route = useRoute()
  const router = useRouter()
  const pointsWalletStore = usePointsWalletStore()
  const roomId = computed(() => String(route.params.roomId || ''))
  const loading = ref(false)
  const surrendering = ref(false)
  const playerStatsVisible = ref(false)
  const selectedPlayer = ref(null)
  const spectatorDialogVisible = ref(false)
  const spectatorPage = ref(1)
  const spectatorPageSize = 10
  const chatMessages = ref([])
  const chatText = ref('')
  const chatListRef = ref(null)
  const elapsedTick = ref(0)
  const room = reactive(emptyRoom())
  let elapsedTimer = null
  let finishRedirectTimer = null
  const finishCountdown = ref(60)

  const myBoardRef = ref(null)
  const opponentBoardRef = ref(null)
  const holdRef = ref(null)
  const nextRef = ref(null)

  const roomSocket = useGameWebSocket(`games/tetris/rooms/${roomId.value}`, {
    onMessage(message) {
      if (!message.ok) {
        if (message.message) ElMessage.warning(message.message)
        return
      }
      if ((message.type === 'room_ready' || message.type === 'room_state_updated' || message.type === 'game_finished') && message.data) {
        applyRoomState(message.data)
        if (message.type === 'room_ready' && Array.isArray(message.data.recentChats)) {
          chatMessages.value = message.data.recentChats
        }
      }
      if (message.type === 'room_chat' && message.data) {
        chatMessages.value.push(message.data)
      }
      if (message.type === 'peer_disconnected') {
        ElMessage.info('对手暂时离线')
      }
      if (message.type === 'peer_reconnected') {
        ElMessage.success('对手已重连')
      }
    },
  })

  const isFinished = computed(() => room.roomStatus === 'FINISHED')
  const isSpectator = computed(() => Boolean(room.spectator))
  const isPlayer = computed(() => !isSpectator.value)
  const spectators = computed(() => Array.isArray(room.spectators) ? room.spectators : [])
  const visibleSpectators = computed(() => spectators.value.slice(0, 6))
  const hiddenSpectatorCount = computed(() => Math.max(0, spectators.value.length - visibleSpectators.value.length))
  const spectatorRows = computed(() => {
    const start = (spectatorPage.value - 1) * spectatorPageSize
    return spectators.value.slice(start, start + spectatorPageSize)
  })
  const canChat = computed(() => room.roomStatus === 'PLAYING' || room.roomStatus === 'FINISHED')

  function scrollChatToBottom() {
    nextTick(() => {
      const el = chatListRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  watch(() => chatMessages.value.length, () => {
    scrollChatToBottom()
  })
  const canControl = computed(() => isPlayer.value && room.roomStatus === 'PLAYING' && !room.myBoard?.gameOver)
  const opponentProfile = computed(() => {
    if (room.opponentPlayer) return room.opponentPlayer
    const opponentId = room.opponentUserId
    if (!opponentId) return null
    if (room.player1?.userId === opponentId) return room.player1
    if (room.player2?.userId === opponentId) return room.player2
    return null
  })
  const redPlayer = computed(() => {
    if (room.redUserId === room.player1UserId) return room.player1
    if (room.redUserId === room.player2UserId) return room.player2
    return null
  })
  const bluePlayer = computed(() => {
    if (room.blueUserId === room.player1UserId) return room.player1
    if (room.blueUserId === room.player2UserId) return room.player2
    return null
  })
  const playerStatsTitle = computed(() => {
    if (!selectedPlayer.value) return '选手资料'
    if (selectedPlayer.value.userId === room.redUserId) return '红方俄罗斯方块 PK 资料'
    if (selectedPlayer.value.userId === room.blueUserId) return '蓝方俄罗斯方块 PK 资料'
    return '俄罗斯方块 PK 资料'
  })
  const myScore = computed(() => room.myBoard?.points ?? 0)
  const opponentScore = computed(() => room.opponentBoard?.points ?? 0)
  const elapsedText = computed(() => {
    elapsedTick.value
    const min = Math.floor(elapsedTick.value / 60)
    const sec = elapsedTick.value % 60
    return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  })
  const pkBarLeftPercent = computed(() => room.pkBarLeftPercent ?? 50)
  const amRed = computed(() => room.thisUserId === room.redUserId)
  const leftBoard = computed(() => {
    if (isSpectator.value) {
      return room.redUserId === room.player1UserId ? room.myBoard : room.opponentBoard
    }
    return room.myBoard
  })
  const rightBoard = computed(() => {
    if (isSpectator.value) {
      return room.blueUserId === room.player2UserId ? room.opponentBoard : room.myBoard
    }
    return room.opponentBoard
  })
  const leftLabel = computed(() => (isSpectator.value ? '红方' : '我方'))
  const rightLabel = computed(() => (isSpectator.value ? '蓝方' : '对手'))
  const winnerText = computed(() => {
    if (!isFinished.value) return ''
    if (!room.winnerUserId) return '本局结束'
    if (isSpectator.value) return room.winnerUserId === room.redUserId ? '红方获胜' : '蓝方获胜'
    return room.winnerUserId === room.thisUserId ? '你赢了' : '你输了'
  })

  function applyRoomState(data) {
    Object.assign(room, {
      roomId: data.roomId || roomId.value,
      thisUserId: data.thisUserId ?? room.thisUserId,
      opponentUserId: data.opponentUserId ?? room.opponentUserId,
      player1UserId: data.player1UserId ?? room.player1UserId,
      player2UserId: data.player2UserId ?? room.player2UserId,
      redUserId: data.redUserId ?? room.redUserId,
      blueUserId: data.blueUserId ?? room.blueUserId,
      roomStatus: data.roomStatus || room.roomStatus,
      winnerUserId: data.winnerUserId ?? null,
      endReason: data.endReason || '',
      spectator: Boolean(data.spectator),
      myBoard: data.myBoard || null,
      opponentBoard: data.opponentBoard || null,
      redScore: data.redScore ?? 0,
      blueScore: data.blueScore ?? 0,
      pkBarLeftPercent: data.pkBarLeftPercent ?? 50,
      opponentPlayer: data.opponentPlayer || null,
      player1: data.player1 || null,
      player2: data.player2 || null,
      spectators: Array.isArray(data.spectators) ? data.spectators : [],
      spectatorCount: Number(data.spectatorCount) || 0,
    })
    if (room.roomStatus === 'FINISHED') {
      void pointsWalletStore.refresh()
      startFinishRedirect()
    }
    requestAnimationFrame(paintAll)
  }

  function paintBoardCanvas(canvas, boardView) {
    if (!canvas || !boardView) return
    const ctx = canvas.getContext('2d')
    drawBoard(ctx, {
      matrix: normalizeMatrix(boardView.matrix),
      cur: normalizePiece(boardView.cur),
      ghost: normalizePiece(boardView.ghost),
      cellSize: CELL_SIZE,
    })
  }

  function paintPreview(canvas, type) {
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    drawPreview(ctx, type, 18)
  }

  function paintAll() {
    paintBoardCanvas(myBoardRef.value, leftBoard.value)
    paintBoardCanvas(opponentBoardRef.value, rightBoard.value)
    if (isPlayer.value && room.myBoard?.revealHoldNext) {
      paintPreview(holdRef.value, room.myBoard.holdType)
      paintPreview(nextRef.value, room.myBoard.nextType)
    }
  }

  watch(
    () => [room.myBoard, room.opponentBoard],
    () => requestAnimationFrame(paintAll),
    { deep: true },
  )

  function sendInput(action) {
    if (!canControl.value) return
    roomSocket.send('input', { action })
  }

  function onKeyDown(event) {
    if (!canControl.value) return
    if (event.metaKey || event.ctrlKey || event.altKey) return
    const action = KEY_MAP[event.key]
    if (!action) return
    event.preventDefault()
    sendInput(action)
  }

  function sendChatText(content) {
    roomSocket.send('chat', { messageType: 'TEXT', content })
  }

  function sendChat() {
    const content = chatText.value.trim()
    if (!content || !canChat.value) return
    if (roomSocket.send('chat', { messageType: 'TEXT', content })) {
      chatText.value = ''
    }
  }

  function sendChatEmoji(url) {
    if (!url || !canChat.value) return
    roomSocket.send('chat', {
      messageType: 'EMOJI',
      content: url,
      emojiUrl: url,
    })
  }

  function participantName(userId) {
    if (userId === room.thisUserId && isPlayer.value) return '我'
    if (userId === room.redUserId) {
      const player = redPlayer.value
      return `红方 ${player?.nickname || player?.username || ''}`.trim()
    }
    if (userId === room.blueUserId) {
      const player = bluePlayer.value
      return `蓝方 ${player?.nickname || player?.username || ''}`.trim()
    }
    if (userId === room.opponentUserId) {
      return room.opponentPlayer?.nickname || room.opponentPlayer?.username || '对手'
    }
    const p1 = room.player1
    const p2 = room.player2
    if (p1?.userId === userId) return p1.nickname || p1.username || `用户 ${userId}`
    if (p2?.userId === userId) return p2.nickname || p2.username || `用户 ${userId}`
    return `用户 ${userId}`
  }

  function avatarText(player) {
    const name = player?.nickname || player?.username || '方'
    return name.slice(0, 1).toUpperCase()
  }

  function openOpponentStats() {
    if (isSpectator.value || !opponentProfile.value) return
    selectedPlayer.value = opponentProfile.value
    playerStatsVisible.value = true
  }

  function openPlayerStats(player) {
    if (!player) return
    selectedPlayer.value = player
    playerStatsVisible.value = true
  }

  async function surrender() {
    if (isFinished.value || surrendering.value || isSpectator.value) return
    try {
      await ElMessageBox.confirm('确认认输并结束本局吗？', '俄罗斯方块 PK', {
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
        await surrenderTetrisPkRoom(roomId.value)
        await loadRoom()
      }
    } finally {
      surrendering.value = false
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
        router.push('/games/tetris/pk')
      }
    }, 1000)
  }

  async function loadRoom() {
    loading.value = true
    try {
      const res = await getTetrisPkRoom(roomId.value)
      if (res.code === 0 && res.data) {
        applyRoomState(res.data)
        if (Array.isArray(res.data.recentChats)) {
          chatMessages.value = res.data.recentChats
        }
        return true
      }
      ElMessage.warning(res.message || '房间不存在或已结束')
      router.push('/games/tetris/pk')
      return false
    } catch {
      ElMessage.warning('房间加载失败，请返回匹配页重试')
      router.push('/games/tetris/pk')
      return false
    } finally {
      loading.value = false
    }
  }

  async function backGame() {
    if (!isFinished.value && isPlayer.value) {
      try {
        await ElMessageBox.confirm('确认离开当前对局吗？', '离开对局', {
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
    const ok = await loadRoom()
    if (!ok) return
    roomSocket.connect()
    window.addEventListener('keydown', onKeyDown, true)
    elapsedTimer = window.setInterval(() => {
      if (room.roomStatus === 'PLAYING') elapsedTick.value += 1
    }, 1000)
    await nextTick()
    paintAll()
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', onKeyDown, true)
    if (elapsedTimer) window.clearInterval(elapsedTimer)
    if (finishRedirectTimer) window.clearInterval(finishRedirectTimer)
    roomSocket.close()
  })

  return {
    bluePlayer,
    backGame,
    canChat,
    chatListRef,
    chatMessages,
    chatText,
    elapsedText,
    finishCountdown,
    holdRef,
    isFinished,
    isPlayer,
    isSpectator,
    leftLabel,
    loading,
    myBoardRef,
    myScore,
    nextRef,
    openOpponentStats,
    openPlayerStats,
    opponentBoardRef,
    opponentProfile,
    opponentScore,
    participantName,
    avatarText,
    pkBarLeftPercent,
    playerStatsTitle,
    playerStatsVisible,
    redPlayer,
    rightLabel,
    room,
    roomSocket,
    selectedPlayer,
    spectatorDialogVisible,
    spectatorPage,
    spectatorPageSize,
    spectatorRows,
    spectators,
    sendChat,
    sendChatEmoji,
    sendChatText,
    surrender,
    surrendering,
    visibleSpectators,
    hiddenSpectatorCount,
    winnerText,
  }
}

const {
  avatarText,
  backGame,
  bluePlayer,
  canChat,
  chatListRef,
  chatMessages,
  chatText,
  elapsedText,
  finishCountdown,
  holdRef,
  isFinished,
  isPlayer,
  isSpectator,
  leftLabel,
  loading,
  myBoardRef,
  myScore,
  nextRef,
  openOpponentStats,
  openPlayerStats,
  opponentBoardRef,
  opponentProfile,
  opponentScore,
  participantName,
  pkBarLeftPercent,
  playerStatsTitle,
  playerStatsVisible,
  redPlayer,
  rightLabel,
  room,
  roomSocket,
  selectedPlayer,
  spectatorDialogVisible,
  spectatorPage,
  spectatorPageSize,
  spectatorRows,
  spectators,
  sendChat,
  sendChatEmoji,
  sendChatText,
  surrender,
  surrendering,
  visibleSpectators,
  hiddenSpectatorCount,
  winnerText,
} = useTetrisPkRoom()
