import { computed, onMounted, onUnmounted, ref } from 'vue'
import Block from './block'
import {
  clearPoints,
  createBlankMatrix,
  eachLines,
  speeds,
} from './constants'
import { createBlockBagPicker, createGameSeed, createRng } from './rng'
import {
  clearLineRows,
  findClearLines,
  ghostDrop,
  isOver,
  mergeBlock,
  want,
} from './unit'

const KEY_MAP = {
  ArrowLeft: 'left',
  ArrowRight: 'right',
  ArrowDown: 'down',
  ArrowUp: 'rotate',
  ' ': 'space',
  s: 'hold',
  S: 'hold',
  p: 'pause',
  P: 'pause',
  Escape: 'pause',
}

export function useTetrisEngine(options = {}) {
  const matrix = ref(createBlankMatrix())
  const cur = ref(null)
  const nextType = ref('')
  const holdType = ref('')
  const canHold = ref(true)
  const points = ref(0)
  const clearLines = ref(0)
  const speedStart = ref(1)
  const speedRun = ref(1)
  const pause = ref(false)
  const gameOver = ref(false)
  const playing = ref(false)
  const dropAnim = ref(false)
  const lock = ref(false)
  const seed = ref(0)
  const startedAt = ref(null)
  const inputs = ref([])
  const replayMode = ref(false)

  let rng = createRng(1)
  let pickNextBlockType = createBlockBagPicker(rng)
  let fallTimer = null
  let startedAtMs = 0

  const ghost = computed(() => {
    if (!cur.value) return null
    return ghostDrop(cur.value, matrix.value)
  })

  function recordInput(action) {
    if (replayMode.value || !playing.value) return
    inputs.value.push({
      t: Math.max(0, Date.now() - startedAtMs),
      a: action,
    })
  }

  function dispatchPoints(nextPoint) {
    points.value = nextPoint
  }

  function addLockPoints() {
    dispatchPoints(points.value + 10 + (speedRun.value - 1) * 2)
  }

  function clearFallTimer() {
    if (fallTimer) {
      clearTimeout(fallTimer)
      fallTimer = null
    }
  }

  function scheduleFall(delay) {
    clearFallTimer()
    if (pause.value || gameOver.value || lock.value || !playing.value) return
    fallTimer = setTimeout(() => {
      softFallStep(true)
    }, delay ?? speeds[speedRun.value - 1])
  }

  function afterLock(matrixAfterLock, stopDownTrigger) {
    clearFallTimer()
    lock.value = true
    matrix.value = matrixAfterLock
    if (typeof stopDownTrigger === 'function') {
      stopDownTrigger()
    }
    addLockPoints()
    canHold.value = true

    const lines = findClearLines(matrixAfterLock)
    if (lines) {
      setTimeout(() => {
        const cleared = clearLineRows(matrixAfterLock, lines)
        matrix.value = cleared
        clearLines.value += lines.length
        dispatchPoints(points.value + clearPoints[lines.length - 1])
        const speedAdd = Math.floor(clearLines.value / eachLines)
        speedRun.value = Math.min(6, speedStart.value + speedAdd)
        spawnNext(cleared)
      }, 100)
      return
    }

    if (isOver(matrixAfterLock)) {
      finishGame()
      return
    }

    setTimeout(() => {
      spawnNext(matrixAfterLock)
    }, 100)
  }

  function spawnNext(currentMatrix) {
    matrix.value = currentMatrix
    const type = nextType.value || drawNextType()
    nextType.value = drawNextType()
    cur.value = new Block({ type })
    lock.value = false
    if (!want(cur.value, matrix.value)) {
      finishGame()
      return
    }
    scheduleFall()
  }

  function drawNextType() {
    return pickNextBlockType()
  }

  function lockCurrent(stopDownTrigger) {
    if (!cur.value) return
    const merged = mergeBlock(matrix.value, cur.value)
    cur.value = null
    afterLock(merged, stopDownTrigger)
  }

  function softFallStep(auto = false) {
    if (lock.value || pause.value || gameOver.value || !cur.value) return
    const next = cur.value.fall()
    if (want(next, matrix.value)) {
      cur.value = next
      scheduleFall()
      return
    }
    lockCurrent()
  }

  function moveHorizontal(direction) {
    if (lock.value || pause.value || gameOver.value || !cur.value) return
    const next = direction === 'left' ? cur.value.left() : cur.value.right()
    if (want(next, matrix.value)) {
      cur.value = next
      recordInput(direction)
    }
  }

  function rotatePiece() {
    if (lock.value || pause.value || gameOver.value || !cur.value) return
    const next = cur.value.rotate()
    if (want(next, matrix.value)) {
      cur.value = next
      recordInput('rotate')
    }
  }

  function hardDrop() {
    if (lock.value || pause.value || gameOver.value || !cur.value) return
    recordInput('space')
    let index = 0
    let bottom = cur.value.fall(index)
    while (want(bottom, matrix.value)) {
      index += 1
      bottom = cur.value.fall(index)
    }
    const landed = cur.value.fall(Math.max(0, index - 1))
    cur.value = landed
    dropAnim.value = true
    setTimeout(() => {
      dropAnim.value = false
    }, 100)
    lockCurrent()
  }

  function holdPiece() {
    if (!canHold.value || lock.value || pause.value || gameOver.value || !cur.value) return
    recordInput('hold')
    const currentType = cur.value.type
    if (holdType.value) {
      cur.value = new Block({ type: holdType.value })
    } else {
      cur.value = new Block({ type: nextType.value })
      nextType.value = drawNextType()
    }
    holdType.value = currentType
    canHold.value = false
    if (!want(cur.value, matrix.value)) {
      finishGame()
    }
  }

  function togglePause(force) {
    const next = typeof force === 'boolean' ? force : !pause.value
    pause.value = next
    recordInput('pause')
    if (next) {
      clearFallTimer()
      return
    }
    scheduleFall()
  }

  function finishGame() {
    playing.value = false
    gameOver.value = true
    lock.value = true
    clearFallTimer()
    options.onGameOver?.()
  }

  function resetState() {
    clearFallTimer()
    matrix.value = createBlankMatrix()
    cur.value = null
    nextType.value = ''
    holdType.value = ''
    canHold.value = true
    points.value = 0
    clearLines.value = 0
    speedStart.value = 1
    speedRun.value = 1
    pause.value = false
    gameOver.value = false
    playing.value = false
    dropAnim.value = false
    lock.value = false
    inputs.value = []
    startedAt.value = null
    startedAtMs = 0
  }

  function startGame() {
    resetState()
    replayMode.value = false
    seed.value = createGameSeed()
    rng = createRng(seed.value)
    pickNextBlockType = createBlockBagPicker(rng)
    startedAt.value = new Date()
    startedAtMs = Date.now()
    playing.value = true
    nextType.value = drawNextType()
    spawnNext(createBlankMatrix())
  }

  function restartGame() {
    startGame()
  }

  function buildReplayPayload() {
    return JSON.stringify({
      v: 2,
      seed: seed.value,
      inputs: inputs.value,
    })
  }

  function getSettlePayload() {
    const endedAt = Date.now()
    return {
      seed: seed.value,
      score: points.value,
      level: speedRun.value,
      linesCleared: clearLines.value,
      durationMs: Math.max(1000, endedAt - startedAtMs),
      replayPayload: buildReplayPayload(),
      startedAtMs,
    }
  }

  function handleAction(action) {
    if (action === 'left' || action === 'right') {
      if (!playing.value && !cur.value && !gameOver.value) {
        startGame()
        return
      }
      if (pause.value) togglePause(false)
      moveHorizontal(action)
      return
    }
    if (action === 'down') {
      if (!playing.value) return
      if (pause.value) togglePause(false)
      recordInput('down')
      softFallStep(false)
      return
    }
    if (action === 'rotate') {
      if (!playing.value && !cur.value && !gameOver.value) {
        startGame()
        return
      }
      if (pause.value) togglePause(false)
      rotatePiece()
      return
    }
    if (action === 'space') {
      if (!playing.value && !cur.value && !gameOver.value) {
        startGame()
        return
      }
      if (pause.value) {
        togglePause(false)
        return
      }
      hardDrop()
      return
    }
    if (action === 'hold') {
      if (!playing.value) return
      holdPiece()
      return
    }
    if (action === 'pause') {
      if (!playing.value) return
      togglePause()
    }
  }

  function onKeyDown(event) {
    if (event.metaKey || event.ctrlKey || event.altKey) return
    const action = KEY_MAP[event.key]
    if (!action) return
    event.preventDefault()
    handleAction(action)
  }

  onMounted(() => {
    window.addEventListener('keydown', onKeyDown, true)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', onKeyDown, true)
    clearFallTimer()
  })

  return {
    matrix,
    cur,
    nextType,
    holdType,
    points,
    clearLines,
    speedRun,
    pause,
    gameOver,
    playing,
    dropAnim,
    ghost,
    seed,
    startedAt,
    inputs,
    startGame,
    restartGame,
    togglePause,
    handleAction,
    getSettlePayload,
    buildReplayPayload,
    resetState,
  }
}
