import { blockType } from './constants'

// 可复现伪随机，供 replay 使用
export function createRng(seed) {
  let state = (Number(seed) >>> 0) || 1
  return () => {
    state += 0x6d2b79f5
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function pickBlockType(rng) {
  const index = Math.floor(rng() * blockType.length)
  return blockType[index]
}

export function createGameSeed() {
  return Date.now()
}
