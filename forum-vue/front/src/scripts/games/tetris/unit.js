import { blankLine } from './constants'

export function want(next, matrix) {
  const xy = next.xy
  const shape = next.shape
  const horizontal = shape[0].length
  return shape.every((row, rowOffset) =>
    row.every((cell, colOffset) => {
      if (xy[1] < 0) return false
      if (xy[1] + horizontal > 10) return false
      if (xy[0] + rowOffset < 0) return true
      if (xy[0] + rowOffset >= 20) return false
      if (cell) {
        return !matrix[xy[0] + rowOffset][xy[1] + colOffset]
      }
      return true
    }),
  )
}

export function findClearLines(matrix) {
  const clearLines = []
  matrix.forEach((row, index) => {
    if (row.every((cell) => !!cell)) {
      clearLines.push(index)
    }
  })
  return clearLines.length ? clearLines : null
}

export function isOver(matrix) {
  return matrix[0].some((cell) => !!cell)
}

export function mergeBlock(matrix, block) {
  const next = matrix.map((row) => [...row])
  block.shape.forEach((row, rowOffset) => {
    row.forEach((cell, colOffset) => {
      if (cell && block.xy[0] + rowOffset >= 0) {
        next[block.xy[0] + rowOffset][block.xy[1] + colOffset] = block.type
      }
    })
  })
  return next
}

export function clearLineRows(matrix, lines) {
  const clearSet = new Set(lines)
  const kept = matrix.filter((_, index) => !clearSet.has(index))
  const emptyRows = lines.map(() => [...blankLine])
  return [...emptyRows, ...kept]
}

export function ghostDrop(block, matrix) {
  let ghost = block.clone()
  let next = ghost.fall()
  while (want(next, matrix)) {
    ghost = next
    next = ghost.fall()
  }
  return ghost
}
