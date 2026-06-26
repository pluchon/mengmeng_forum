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
  const next = matrix.map((row) => [...row])
  const sorted = [...lines].sort((a, b) => b - a)
  sorted.forEach((lineIndex) => {
    next.splice(lineIndex, 1)
    next.unshift([0, 0, 0, 0, 0, 0, 0, 0, 0, 0])
  })
  return next
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
