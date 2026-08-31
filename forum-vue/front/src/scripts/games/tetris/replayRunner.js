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

// 落子回放里每个方块停留多久。录像不含时间，这里只是让人看清楚
const LOCK_INTERVAL_MS = 260

// 带落子序列的录像版本，低于它的旧录像只能按按键流近似回放
const REPLAY_VERSION_LOCKS = 3

/**
 * 按落子序列回放。
 *
 * 这是与服务端校验同一套模型：给定种子，方块序列确定，每条记录给出落点，
 * 逐条合并就能得到与当时完全一致的棋盘。不需要模拟重力，也就不会漂移。
 */
function createLockRunner(seed, locks) {
  const rng = createRng(seed)
  const pick = createBlockBagPicker(rng)
  // 与 useTetrisEngine 的出块顺序保持一致：current 取 next，next 再抽一个
  let next = pick()
  let current = next
  next = pick()
  let held = null

  // 预先把每一步的棋盘算出来，回放时只是按下标取，拖进度条不用重算
  const frames = []
  let matrix = createBlankMatrix()
  let points = 0
  let clearLines = 0
  let combo = 0
  let speedRun = 1

  for (const lock of locks) {
    if (lock && lock.h) {
      const previous = current
      if (held !== null) {
        current = held
      } else {
        current = next
        next = pick()
      }
      held = previous
    }
    // 录像被改过时按录像里的类型画，回放只求还原当时画面，判真假是服务端的事
    const type = lock && lock.t ? lock.t : current
    let block = new Block({ type })
    const rotation = Number(lock && lock.r) || 0
    for (let i = 0; i < rotation; i += 1) {
      block = block.rotate()
    }
    block = new Block({
      type,
      shape: block.shape,
      xy: [Number(lock && lock.y) || 0, Number(lock && lock.x) || 0],
      rotateIndex: block.rotateIndex,
    })
    // 先记「方块停在落点、还没并入」的那一帧，看起来才像落下去的
    frames.push({ matrix, cur: block, points, clearLines, speedRun })

    matrix = mergeBlock(matrix, block)
    points += 10 + (speedRun - 1) * 2
    const full = findClearLines(matrix)
    if (full) {
      matrix = clearLineRows(matrix, full)
      clearLines += full.length
      combo += 1
      points += calcClearScore(full.length, combo)
      speedRun = Math.min(6, 1 + Math.floor(clearLines / eachLines))
    } else {
      combo = 0
    }
    current = next
    next = pick()
  }
  // 收尾帧：最后一子并入后的棋盘
  frames.push({ matrix, cur: null, points, clearLines, speedRun })

  let index = 0
  return {
    getState() {
      const frame = frames[Math.min(index, frames.length - 1)]
      return { ...frame, playing: index < frames.length - 1 }
    },
    stepTo(ms) {
      index = Math.floor(ms / LOCK_INTERVAL_MS)
    },
    isDone() {
      return index >= frames.length - 1
    },
    totalDuration() {
      return Math.max(LOCK_INTERVAL_MS, (frames.length - 1) * LOCK_INTERVAL_MS)
    },
  }
}

/**
 * 按按键流近似回放（v3 之前的旧录像）。
 *
 * 录像里只有按键，重力下落不产生事件，所以自然落下的方块在这里无迹可寻——
 * 这条路径复现不出当时的棋盘，只能给个大概。新录像一律走落子序列。
 */
function createInputRunner(seed, inputs, randomizerVersion) {
  const rng = createRng(seed)
  const pickNextBlockType = Number(randomizerVersion) >= 2
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

  function spawn() {
    const type = nextType
    nextType = pickNextBlockType()
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

// 离线重放：用种子复现棋盘，供回放抽屉使用
export function createReplayRunner(seed, payload = {}) {
  const version = Number(payload.v) || 1
  const locks = Array.isArray(payload.locks) ? payload.locks : []
  if (version >= REPLAY_VERSION_LOCKS && locks.length) {
    return createLockRunner(seed, locks)
  }
  return createInputRunner(seed, Array.isArray(payload.inputs) ? payload.inputs : [], version)
}

// 回放是否只是近似（旧录像没有落子序列，重力下落无迹可寻）
export function isApproximateReplay(payload = {}) {
  const version = Number(payload.v) || 1
  const locks = Array.isArray(payload.locks) ? payload.locks : []
  return !(version >= REPLAY_VERSION_LOCKS && locks.length)
}
