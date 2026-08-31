import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import { ChatDotRound, Flag, HomeFilled, UserFilled } from '@element-plus/icons-vue'
import { getTetrisPkRoom, surrenderTetrisPkRoom } from '@/api/game'
import PurchasedEmojiPackPopover from '@/components/common/PurchasedEmojiPackPopover.vue'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { drawBoard, drawPreview } from '@/scripts/games/tetris/canvas'

const CELL_SIZE = 24
// 结算后自动返回匹配页的等待秒数。原来是 60 秒，看完结果只能干等
const FINISH_REDIRECT_SECONDS = 12
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
    redLines: 0,
    blueLines: 0,
    remainingMs: 0,
    pkBarLeftPercent: 50,
    opponentPlayer: null,
    player1: null,
    player2: null,
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

  const chatMessages = ref([])
  const chatText = ref('')
  const chatListRef = ref(null)
  const remainSeconds = ref(0)
  const room = reactive(emptyRoom())
  let elapsedTimer = null
  let finishRedirectTimer = null
  const finishCountdown = ref(FINISH_REDIRECT_SECONDS)

  const myBoardRef = ref(null)
  const opponentBoardRef = ref(null)
  const holdRef = ref(null)
  const nextRef = ref(null)
  const comboFlash = ref(0)

  const peerStateText = ref('')
  let comboFlashTimer = null
  let lastCombo = 0
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
      if (message.type === 'garbage_received' && message.data) {
        const lines = Number(message.data.lines) || 0
        if (lines > 0) {
          const hitOpponent = Number(message.data.targetUserId) === Number(room.opponentUserId)
          const text = hitOpponent ? `攻击对手 ${lines} 行` : `被对手攻击 ${lines} 行`
          peerStateText.value = text
          window.setTimeout(() => {
            if (peerStateText.value === text) peerStateText.value = ''
          }, 2000)
        }
      }
      // 进出通知只带 userId，得分清是自己还是对手——以前自己重连也会看到「对手已重连」
      if (message.type === 'peer_disconnected') {
        if (Number(message.data?.userId) !== Number(room.thisUserId)) {
          peerStateText.value = '对手暂时离线'
        }
      }
      if (message.type === 'peer_reconnected') {
        const isMe = Number(message.data?.userId) === Number(room.thisUserId)
        const text = isMe ? '已重新连上' : '对手已重连'
        peerStateText.value = text
        window.setTimeout(() => {
          if (peerStateText.value === text) peerStateText.value = ''
        }, 3000)
      }
    },
    // 断线期间服务端的重力没停，棋盘早就不是断线前那样了，必须重新拉一次
    onReconnect() {
      void loadRoom()
    },
    onReconnectFailed() {
      peerStateText.value = '连接已断开'
      ElMessage.error('实时连接断开且重连失败，请刷新页面')
    },
  })

  const isFinished = computed(() => room.roomStatus === 'FINISHED')
  const isSpectator = computed(() => Boolean(room.spectator))
  const isPlayer = computed(() => !isSpectator.value)
  const spectatorCount = computed(() => Number(room.spectatorCount) || 0)
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
  // 倒计时。以前是本地从 0 自增的正计时，刷新页面就归零，跟服务端毫无关系
  const elapsedText = computed(() => {
    const total = Math.max(0, remainSeconds.value)
    const min = Math.floor(total / 60)
    const sec = total % 60
    return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  })
  // 最后 30 秒给个视觉提醒
  const timeUrgent = computed(() => room.roomStatus === 'PLAYING' && remainSeconds.value <= 30)
  // 竞速比的是消行数，进度条就按消行数分配左右宽度
  const pkBarLeftPercent = computed(() => {
    const red = Math.max(0, Number(room.redLines) || 0)
    const blue = Math.max(0, Number(room.blueLines) || 0)
    const total = red + blue
    if (total <= 0 || red === blue) return 50
    const percent = Math.round((red / total) * 100)
    return Math.max(15, Math.min(85, percent))
  })
  const myLines = computed(() => room.myBoard?.linesCleared ?? 0)
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
    if (!room.winnerUserId) return '平局'
    if (isSpectator.value) return room.winnerUserId === room.redUserId ? '红方获胜' : '蓝方获胜'
    return room.winnerUserId === room.thisUserId ? '你赢了' : '你输了'
  })
  // 胜负是怎么定的要说清楚，而且要分得清是谁——「有人认输了」等于没说
  const finishReasonText = computed(() => {
    if (!isFinished.value) return ''
    const redLines = room.redLines ?? 0
    const blueLines = room.blueLines ?? 0
    if (room.endReason === 'RACE') {
      if (!room.winnerUserId) return `时间到 · 消行与分数都是 ${redLines} 行、${room.redScore ?? 0} 分`
      if (redLines !== blueLines) return `时间到 · 消行 ${redLines} : ${blueLines}，多的一方获胜`
      return `时间到 · 消行同为 ${redLines}，按分数 ${room.redScore ?? 0} : ${room.blueScore ?? 0} 决胜`
    }
    if (!room.winnerUserId) return ''
    // 观战按红蓝称呼，对局中人按你我称呼
    const loserSide = room.winnerUserId === room.redUserId ? '蓝方' : '红方'
    const iWon = room.winnerUserId === room.thisUserId
    switch (room.endReason) {
      case 'SURRENDER':
        if (isSpectator.value) return `${loserSide}主动认输`
        return iWon ? '对手主动认输' : '你主动认输了'
      case 'LINE':
        if (isSpectator.value) return `${loserSide}先堆到顶`
        return iWon ? '对手先堆到顶了' : '你先堆到顶了'
      case 'DISCONNECT':
        if (isSpectator.value) return `${loserSide}掉线太久`
        return iWon ? '对手掉线太久' : '你掉线太久了'
      default:
        return ''
    }
  })
  function triggerComboFlash(comboCount) {
    if (comboCount < 2) return
    comboFlash.value = comboCount >= 3 ? 3 : 2
    if (comboFlashTimer) clearTimeout(comboFlashTimer)
    comboFlashTimer = window.setTimeout(() => {
      comboFlash.value = 0
      comboFlashTimer = null
    }, 600)
  }

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
      redLines: data.redLines ?? 0,
      blueLines: data.blueLines ?? 0,
      remainingMs: Number(data.remainingMs) || 0,
      pkBarLeftPercent: data.pkBarLeftPercent ?? 50,
      opponentPlayer: data.opponentPlayer || null,
      player1: data.player1 || null,
      player2: data.player2 || null,
      spectatorCount: Number(data.spectatorCount) || 0,
    })
    // 服务端每帧都带剩余时长，收到就校正一次，本地只负责两帧之间的递减
    if (typeof data.remainingMs === 'number') {
      remainSeconds.value = Math.max(0, Math.ceil(data.remainingMs / 1000))
    }
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
    () => room.myBoard?.combo ?? 0,
    (nextCombo) => {
      if (nextCombo >= 2 && nextCombo > lastCombo) {
        triggerComboFlash(nextCombo)
      }
      lastCombo = nextCombo
    },
  )

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
      await confirmDialog('确认认输并结束本局吗？', '俄罗斯方块 PK', {
        type: 'warning',
        confirmButtonText: '认输',
        cancelButtonText: '继续对局',
      })
    } catch {
      return
    }
    surrendering.value = true
    try {
      // WS 通道优先；发不出去（正在重连）就走 HTTP，别让人点了没反应
      if (!roomSocket.send('surrender')) {
        const res = await surrenderTetrisPkRoom(roomId.value)
        if (res?.code !== 0) {
          ElMessage.warning(res?.message || '认输失败，请重试')
          return
        }
        await loadRoom()
      }
    } catch {
      ElMessage.error('认输失败，请检查网络后重试')
    } finally {
      surrendering.value = false
    }
  }

  function startFinishRedirect() {
    if (finishRedirectTimer) return
    finishCountdown.value = FINISH_REDIRECT_SECONDS
    finishRedirectTimer = window.setInterval(() => {
      finishCountdown.value = Math.max(0, finishCountdown.value - 1)
      if (finishCountdown.value <= 0) {
        backMatchNow()
      }
    }, 1000)
  }

  // 看完结果想立刻走的人不该被倒计时按在原地
  function backMatchNow() {
    if (finishRedirectTimer) {
      window.clearInterval(finishRedirectTimer)
      finishRedirectTimer = null
    }
    router.push('/games/tetris/pk')
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
        await confirmDialog('确认离开当前对局吗？', '离开对局', {
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
      if (room.roomStatus === 'PLAYING' && remainSeconds.value > 0) {
        remainSeconds.value -= 1
      }
    }, 1000)
    await nextTick()
    paintAll()
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', onKeyDown, true)
    if (elapsedTimer) window.clearInterval(elapsedTimer)
    if (finishRedirectTimer) window.clearInterval(finishRedirectTimer)
    if (comboFlashTimer) window.clearTimeout(comboFlashTimer)
    roomSocket.close()
  })

  return {
    avatarText,
    backGame,
    backMatchNow,
    bluePlayer,
    canChat,
    chatListRef,
    chatMessages,
    chatText,
    comboFlash,
    elapsedText,
    finishCountdown,
    finishReasonText,
    holdRef,
    isFinished,
    isPlayer,
    isSpectator,
    leftLabel,
    loading,
    myBoardRef,
    myLines,
    myScore,
    nextRef,
    openOpponentStats,
    openPlayerStats,
    opponentBoardRef,
    opponentProfile,
    participantName,
    peerStateText,
    pkBarLeftPercent,
    playerStatsTitle,
    playerStatsVisible,
    redPlayer,
    rightLabel,
    room,
    roomId,
    roomSocket,
    selectedPlayer,
    sendChat,
    sendChatEmoji,
    spectatorCount,
    surrender,
    surrendering,
    timeUrgent,
    winnerText,
  }
}

const {
  avatarText,
  backGame,
  backMatchNow,
  bluePlayer,
  canChat,
  chatListRef,
  chatMessages,
  chatText,
  comboFlash,
  elapsedText,
  finishCountdown,
  finishReasonText,
  holdRef,
  isFinished,
  isPlayer,
  isSpectator,
  leftLabel,
  loading,
  myBoardRef,
  myLines,
  myScore,
  nextRef,
  openOpponentStats,
  openPlayerStats,
  opponentBoardRef,
  opponentProfile,
  participantName,
  peerStateText,
  pkBarLeftPercent,
  playerStatsTitle,
  playerStatsVisible,
  redPlayer,
  rightLabel,
  room,
  roomId,
  roomSocket,
  selectedPlayer,
  sendChat,
  sendChatEmoji,
  spectatorCount,
  surrender,
  surrendering,
  timeUrgent,
  winnerText,
} = useTetrisPkRoom()
