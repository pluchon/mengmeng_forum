export const blockShape = {
  I: [[1, 1, 1, 1]],
  L: [[0, 0, 1], [1, 1, 1]],
  J: [[1, 0, 0], [1, 1, 1]],
  Z: [[1, 1, 0], [0, 1, 1]],
  S: [[0, 1, 1], [1, 1, 0]],
  O: [[1, 1], [1, 1]],
  T: [[0, 1, 0], [1, 1, 1]],
}

export const origin = {
  I: [[-1, 1], [1, -1]],
  L: [[0, 0]],
  J: [[0, 0]],
  Z: [[0, 0]],
  S: [[0, 0]],
  O: [[0, 0]],
  T: [[0, 0], [1, 0], [-1, 1], [0, -1]],
}

export const blockType = Object.keys(blockShape)

export const speeds = [800, 650, 500, 370, 250, 160]

export const clearPoints = [100, 300, 700, 1500]

// 四消固定加分，与 PK 服务端 TetrisEngineConstants 一致
export const tetrisBonus = 200

export const eachLines = 20

/** 连击加成百分比：第 2 次 +10%，第 3 次 +15%，第 4 次起 +20%（封顶） */
export function comboBonusPercent(comboCount) {
  if (comboCount >= 4) return 20
  if (comboCount === 3) return 15
  if (comboCount === 2) return 10
  return 0
}

/** 单次消行得分（含连击与四消奖励），comboCount 为本次消行后的连击数 */
export function calcClearScore(lineCount, comboCount) {
  if (lineCount <= 0 || lineCount > clearPoints.length) return 0
  const base = clearPoints[lineCount - 1]
  const bonus = Math.floor((base * comboBonusPercent(comboCount)) / 100)
  let score = base + bonus
  if (lineCount === 4) score += tetrisBonus
  return score
}

export const blankLine = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

export function createBlankMatrix() {
  return Array.from({ length: 20 }, () => [...blankLine])
}

export const COLORS = {
  I: '#00E5FF',
  O: '#FFE600',
  T: '#D050FF',
  S: '#3DFF6A',
  Z: '#FF3B3B',
  J: '#4B8BFF',
  L: '#FF9B2E',
  G: '#8b9cb8',
  ghost: 'rgba(255,255,255,0.22)',
  grid: 'rgba(255,255,255,0.08)',
  filled: '#e2e8f0',
}
