import { COLORS } from './constants'

export function drawBoard(ctx, {
  matrix,
  cur,
  ghost,
  cellSize,
  offsetX = 0,
  offsetY = 0,
  cols = 10,
  rows = 20,
}) {
  ctx.clearRect(offsetX, offsetY, cols * cellSize, rows * cellSize)
  ctx.fillStyle = 'rgba(8, 12, 20, 0.92)'
  ctx.fillRect(offsetX, offsetY, cols * cellSize, rows * cellSize)

  for (let row = 0; row < rows; row += 1) {
    for (let col = 0; col < cols; col += 1) {
      const x = offsetX + col * cellSize
      const y = offsetY + row * cellSize
      ctx.strokeStyle = COLORS.grid
      ctx.strokeRect(x + 0.5, y + 0.5, cellSize - 1, cellSize - 1)
      const cell = matrix[row]?.[col]
      if (cell) {
        drawCell(ctx, x, y, cellSize, COLORS[cell] || COLORS.I)
      }
    }
  }

  if (ghost) {
    const ghostColor = COLORS[ghost.type] || COLORS.ghost
    drawBlock(ctx, ghost, cellSize, offsetX, offsetY, ghostColor, true)
  }
  if (cur) {
    const color = COLORS[cur.type] || COLORS.I
    drawBlock(ctx, cur, cellSize, offsetX, offsetY, color, false)
  }
}

export function drawPreview(ctx, type, cellSize, cols = 4, rows = 4) {
  const canvas = ctx.canvas
  const width = canvas.width
  const height = canvas.height
  ctx.clearRect(0, 0, width, height)
  if (!type) return
  const shape = getShape(type)
  const shapeRows = shape.length
  const shapeCols = shape[0].length
  const gridWidth = cols * cellSize
  const gridHeight = rows * cellSize
  const gridOffsetX = (width - gridWidth) / 2
  const gridOffsetY = (height - gridHeight) / 2
  const offsetRow = Math.floor((rows - shapeRows) / 2)
  const offsetCol = Math.floor((cols - shapeCols) / 2)
  const block = { type, shape, xy: [offsetRow, offsetCol] }
  drawBlock(ctx, block, cellSize, gridOffsetX, gridOffsetY, COLORS[type] || COLORS.I, false, true)
}

function getShape(type) {
  const shapes = {
    I: [[1, 1, 1, 1]],
    L: [[0, 0, 1], [1, 1, 1]],
    J: [[1, 0, 0], [1, 1, 1]],
    Z: [[1, 1, 0], [0, 1, 1]],
    S: [[0, 1, 1], [1, 1, 0]],
    O: [[1, 1], [1, 1]],
    T: [[0, 1, 0], [1, 1, 1]],
  }
  return shapes[type]
}

function previewOrigin(type) {
  if (type === 'I') return [1, 0]
  if (type === 'O') return [1, 1]
  return [0, 1]
}

function drawBlock(ctx, block, cellSize, offsetX, offsetY, color, dashed, whiteStroke = false) {
  block.shape.forEach((row, rowOffset) => {
    row.forEach((cell, colOffset) => {
      if (!cell) return
      const x = offsetX + (block.xy[1] + colOffset) * cellSize
      const y = offsetY + (block.xy[0] + rowOffset) * cellSize
      drawCell(ctx, x, y, cellSize, color, dashed, whiteStroke)
    })
  })
}

function drawCell(ctx, x, y, size, color, dashed = false, whiteStroke = false) {
  if (dashed) {
    ctx.strokeStyle = color
    ctx.globalAlpha = 0.45
    ctx.lineWidth = 2
    ctx.strokeRect(x + 3, y + 3, size - 6, size - 6)
    ctx.globalAlpha = 1
    return
  }
  const highlight = tintHex(color, 0.42)
  const shadow = tintHex(color, -0.12)
  const gradient = ctx.createLinearGradient(x, y, x + size, y + size)
  gradient.addColorStop(0, highlight)
  gradient.addColorStop(0.55, color)
  gradient.addColorStop(1, shadow)
  ctx.fillStyle = gradient
  ctx.fillRect(x + 1, y + 1, size - 2, size - 2)
  ctx.strokeStyle = whiteStroke ? 'rgba(255, 255, 255, 0.95)' : 'rgba(255, 255, 255, 0.28)'
  ctx.lineWidth = whiteStroke ? 1.6 : 1
  ctx.strokeRect(x + 1.5, y + 1.5, size - 3, size - 3)
}

function tintHex(hex, amount) {
  const raw = hex.replace('#', '')
  const full = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw
  const num = Number.parseInt(full, 16)
  const r = (num >> 16) & 255
  const g = (num >> 8) & 255
  const b = num & 255
  const mix = (channel) => {
    if (amount >= 0) {
      return Math.round(channel + (255 - channel) * amount)
    }
    return Math.round(channel * (1 + amount))
  }
  const toHex = (channel) => mix(channel).toString(16).padStart(2, '0')
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`
}
