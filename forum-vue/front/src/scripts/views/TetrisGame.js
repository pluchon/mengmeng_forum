import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { HomeFilled, RefreshRight, Trophy, DataLine, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import {
  getTetrisLeaderboard,
  getTetrisProfile,
  getTetrisRecords,
  getTetrisReplay,
  settleTetris,
} from '@/api/game'
import { useForumPointsBalance } from '@/composables/useForumPointsBalance'
import { unwrapPageRecords } from '@/utils/apiData'
import { parseForumDateTime } from '@/utils/datetime'
import { drawBoard, drawPreview } from '@/scripts/games/tetris/canvas'
import { createReplayRunner } from '@/scripts/games/tetris/replayRunner'

import { useTetrisEngine } from '@/scripts/games/tetris/useTetrisEngine'

const CELL_SIZE = 24
const BOARD_WIDTH = 10 * CELL_SIZE
const BOARD_HEIGHT = 20 * CELL_SIZE
const REPLAY_CELL = 20

let replayTimer = null

function formatRecordTime(value) {
  const d = parseForumDateTime(value)
  if (!d) return '刚刚'
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDuration(ms) {
  const total = Math.max(0, Number(ms) || 0)
  const sec = Math.floor(total / 1000)
  const min = Math.floor(sec / 60)
  const rest = sec % 60
  return `${min}:${String(rest).padStart(2, '0')}`
}

function useTetrisGame() {
  const router = useRouter()
  const route = useRoute()
  const { refreshForumPointsBalance } = useForumPointsBalance()
  const loading = ref(false)
  const settling = ref(false)
  const settleResult = ref(null)
  const gameOverVisible = ref(false)
  const recordsVisible = ref(false)
  const leaderboardVisible = ref(false)
  const replayVisible = ref(false)
  const profile = ref(null)
  const records = ref([])
  const recordTotal = ref(0)
  const recordPage = ref(1)
  const recordPageSize = ref(8)
  const leaderboard = ref([])
  const replayRecord = ref(null)
  const replayPayload = ref(null)
  const replayPlaying = ref(false)
  const replayProgress = ref(0)
  let replayRunner = null
  let replayStartedAt = 0

  const boardRef = ref(null)
  const nextRef = ref(null)
  const holdRef = ref(null)
  const replayBoardRef = ref(null)
  const elapsedTick = ref(0)
  let elapsedTimer = null

  const engine = useTetrisEngine({
    onGameOver: () => {
      gameOverVisible.value = true
      submitSettle()
    },
  })

  const modeLabel = computed(() => {
    if (route.meta?.tetrisMode === 'pk') return '在线PK模式'
    return '单人模式'
  })

  const nickname = computed(() => profile.value?.nickname || profile.value?.username || '玩家')
  const bestScore = computed(() => Number(profile.value?.bestScore) || 0)
  const totalCount = computed(() => Number(profile.value?.totalCount) || 0)

  const statusText = computed(() => {
    if (!engine.playing.value) return '准备中'
    if (engine.pause.value) return '已暂停'
    if (engine.gameOver.value) return '已结束'
    return '进行中'
  })

  const elapsedText = computed(() => {
    elapsedTick.value
    if (!engine.playing.value || !engine.startedAt.value) return '00:00'
    const sec = Math.max(0, Math.floor((Date.now() - engine.startedAt.value.getTime()) / 1000))
    const min = Math.floor(sec / 60)
    const rest = sec % 60
    return `${String(min).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
  })

  function startElapsedTimer() {
    if (elapsedTimer) return
    elapsedTimer = window.setInterval(() => {
      if (engine.playing.value && !engine.pause.value) {
        elapsedTick.value += 1
      }
    }, 1000)
  }

  function stopElapsedTimer() {
    if (!elapsedTimer) return
    window.clearInterval(elapsedTimer)
    elapsedTimer = null
  }

  function paintBoard() {
    const canvas = boardRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    drawBoard(ctx, {
      matrix: engine.matrix.value,
      cur: engine.cur.value,
      ghost: engine.ghost.value,
      cellSize: CELL_SIZE,
    })
  }

  function paintPreview(canvas, type) {
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    drawPreview(ctx, type, 18)
  }

  function paintAll() {
    paintBoard()
    paintPreview(nextRef.value, engine.nextType.value)
    paintPreview(holdRef.value, engine.holdType.value)
  }

  watch(
    [
      engine.matrix,
      engine.cur,
      engine.ghost,
      engine.nextType,
      engine.holdType,
      engine.dropAnim,
    ],
    () => {
      requestAnimationFrame(paintAll)
    },
    { deep: true },
  )

  async function loadProfile() {
    const res = await getTetrisProfile()
    if (res.code === 0) {
      profile.value = res.data
    }
  }

  async function loadRecords(page = recordPage.value) {
    const res = await getTetrisRecords({ pageNum: page, pageSize: recordPageSize.value })
    if (res.code === 0 && res.data) {
      records.value = unwrapPageRecords(res.data)
      recordTotal.value = Number(res.data.total) || records.value.length
      recordPage.value = page
    }
  }

  function onRecordPageChange(page) {
    loadRecords(page)
  }

  async function loadLeaderboard() {
    const res = await getTetrisLeaderboard({ pageSize: 20 })
    if (res.code === 0) {
      leaderboard.value = Array.isArray(res.data) ? res.data : []
    }
  }

  async function refreshAll() {
    loading.value = true
    try {
      await Promise.all([loadProfile(), loadRecords(), loadLeaderboard(), refreshForumPointsBalance()])
    } finally {
      loading.value = false
    }
  }

  async function submitSettle() {
    if (settling.value) return
    settling.value = true
    settleResult.value = null
    try {
      const payload = engine.getSettlePayload()
      const res = await settleTetris(payload)
      if (res.code === 0 && res.data) {
        settleResult.value = res.data
        Promise.all([
          loadProfile(),
          refreshForumPointsBalance(),
        ]).catch(() => {})
      } else {
        ElMessage.warning(res.message || '成绩保存失败')
      }
    } catch {
      ElMessage.error('成绩保存失败，请稍后重试')
    } finally {
      settling.value = false
    }
  }

  function startGame() {
    settleResult.value = null
    gameOverVisible.value = false
    engine.startGame()
    startElapsedTimer()
    paintAll()
  }

  function restartGame() {
    settleResult.value = null
    gameOverVisible.value = false
    engine.restartGame()
    startElapsedTimer()
    paintAll()
  }

  function togglePause() {
    engine.togglePause()
  }

  function openRecords() {
    recordsVisible.value = true
    loadRecords()
  }

  function openLeaderboard() {
    leaderboardVisible.value = true
    loadLeaderboard()
  }

  function stopReplay() {
    replayPlaying.value = false
    if (replayTimer) {
      clearInterval(replayTimer)
      replayTimer = null
    }
  }

  function paintReplayFrame() {
    const canvas = replayBoardRef.value
    if (!canvas || !replayRunner) return
    const state = replayRunner.getState()
    const ctx = canvas.getContext('2d')
    drawBoard(ctx, {
      matrix: state.matrix,
      cur: state.cur,
      ghost: null,
      cellSize: REPLAY_CELL,
    })
  }

  function startReplayPlayback() {
    if (!replayPayload.value?.seed) return
    stopReplay()
    replayRunner = createReplayRunner(
      replayPayload.value.seed,
      replayPayload.value.inputs || [],
    )
    replayStartedAt = Date.now()
    replayPlaying.value = true
    replayTimer = setInterval(() => {
      const elapsed = Date.now() - replayStartedAt
      replayRunner.stepTo(elapsed)
      replayProgress.value = Math.min(100, Math.round((elapsed / replayRunner.totalDuration()) * 100))
      paintReplayFrame()
      if (replayRunner.isDone()) {
        stopReplay()
      }
    }, 50)
  }

  async function openReplay(row) {
    stopReplay()
    const res = await getTetrisReplay(row.id)
    if (res.code !== 0 || !res.data) return
    replayRecord.value = res.data.record
    try {
      replayPayload.value = JSON.parse(res.data.replayPayload || '{}')
    } catch {
      replayPayload.value = { seed: res.data.seed, inputs: [] }
    }
    replayProgress.value = 0
    replayVisible.value = true
    await nextTick()
    replayRunner = createReplayRunner(
      replayPayload.value.seed || res.data.seed,
      replayPayload.value.inputs || [],
    )
    paintReplayFrame()
  }

  function backCenter() {
    router.push('/games')
  }

  onMounted(async () => {
    await refreshAll()
    await nextTick()
    startGame()
    paintAll()
  })

  onUnmounted(() => {
    stopReplay()
    stopElapsedTimer()
  })

  return {
    backCenter,
    bestScore,
    boardRef,
    elapsedText,
    engine,
    holdRef,
    leaderboard,
    leaderboardVisible,
    loading,
    modeLabel,
    nickname,
    nextRef,
    onRecordPageChange,
    openLeaderboard,
    openRecords,
    openReplay,
    profile,
    recordPage,
    recordPageSize,
    recordTotal,
    records,
    recordsVisible,
    replayBoardRef,
    replayPlaying,
    replayProgress,
    replayRecord,
    replayVisible,
    restartGame,
    settleResult,
    settling,
    startReplayPlayback,
    statusText,
    stopReplay,
    gameOverVisible,
    togglePause,
    totalCount,
  }
}

const {
  backCenter,
  bestScore,
  boardRef,
  elapsedText,
  engine,
  holdRef,
  leaderboard,
  leaderboardVisible,
  loading,
  modeLabel,
  nickname,
  nextRef,
  onRecordPageChange,
  openLeaderboard,
  openRecords,
  openReplay,
  profile,
  recordPage,
  recordPageSize,
  recordTotal,
  records,
  recordsVisible,
  replayBoardRef,
  replayPlaying,
  replayProgress,
  replayRecord,
  replayVisible,
  restartGame,
  settleResult,
  settling,
  startReplayPlayback,
  statusText,
  stopReplay,
  gameOverVisible,
  togglePause,
  totalCount,
} = useTetrisGame()
