import Block from './block'
import { calcClearScore, createBlankMatrix, eachLines, speeds } from './constants'
import { createBlockBagPicker, createRng, pickBlockType } from './rng'
import {
  clearLineRows,
  findClearLines,
  isOver,
  mergeBlock,
  want,
} from './unit'

// 离线重放：用种子与操作流复现棋盘，供回放抽屉使用
export function createReplayRunner(seed, inputs = [], randomizerVersion = 2) {
  let rng = createRng(seed)
  let pickNextBlockType = Number(randomizerVersion) >= 2
    ? createBlockBagPicker(rng)
    : () => pickBlockType(rng)
  let matrix = createBlankMatrix()
  let cur = null
  let nextType = pickNextBlockType()
  let speedRun = 1
  let clearLines = 0
  let points = 0
  let combo = 0
  let playing = true

  function drawNextType() {
    return pickNextBlockType()
  }

  function spawn() {
    const type = nextType
    nextType = drawNextType()
    cur = new Block({ type })
    if (!want(cur, matrix)) {
      playing = false
    }
  }

  function lockCurrent() {
    if (!cur) return
    matrix = mergeBlock(matrix, cur)
    points += 10 + (speedRun - 1) * 2
    cur = null
    const lines = findClearLines(matrix)
    if (lines) {
      matrix = clearLineRows(matrix, lines)
      clearLines += lines.length
      combo += 1
      points += calcClearScore(lines.length, combo)
      speedRun = Math.min(6, 1 + Math.floor(clearLines / eachLines))
    } else {
      combo = 0
    }
    if (isOver(matrix)) {
      playing = false
      return
    }
    spawn()
  }

  function apply(action) {
    if (!playing || !cur) return
    if (action === 'left') {
      const next = cur.left()
      if (want(next, matrix)) cur = next
      return
    }
    if (action === 'right') {
      const next = cur.right()
      if (want(next, matrix)) cur = next
      return
    }
    if (action === 'down') {
      const next = cur.fall()
      if (want(next, matrix)) {
        cur = next
      } else {
        lockCurrent()
      }
      return
    }
    if (action === 'rotate') {
      const next = cur.rotate()
      if (want(next, matrix)) cur = next
      return
    }
    if (action === 'space') {
      let index = 0
      let bottom = cur.fall(index)
      while (want(bottom, matrix)) {
        index += 1
        bottom = cur.fall(index)
      }
      cur = cur.fall(Math.max(0, index - 1))
      lockCurrent()
    }
  }

  spawn()

  let inputIndex = 0
  const sorted = [...inputs].sort((a, b) => a.t - b.t)

  return {
    getState() {
      return { matrix, cur, points, clearLines, speedRun, playing }
    },
    stepTo(ms) {
      while (inputIndex < sorted.length && sorted[inputIndex].t <= ms) {
        apply(sorted[inputIndex].a)
        inputIndex += 1
        if (!playing) break
      }
    },
    isDone() {
      return !playing || inputIndex >= sorted.length
    },
    totalDuration() {
      if (!sorted.length) return speeds[0]
      return sorted[sorted.length - 1].t + speeds[0]
    },
  }
}
