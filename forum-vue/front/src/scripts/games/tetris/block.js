import { blockShape, origin } from './constants'

export default class Block {
  constructor(option) {
    this.type = option.type
    this.rotateIndex = option.rotateIndex || 0
    this.timeStamp = option.timeStamp || Date.now()
    this.shape = option.shape || blockShape[option.type]
    if (option.xy) {
      this.xy = option.xy
    } else {
      this.xy = option.type === 'I' ? [0, 3] : [-1, 4]
    }
  }

  rotate() {
    const shape = this.shape
    const result = []
    shape.forEach((row) => {
      row.forEach((cell, colIndex) => {
        const index = row.length - colIndex - 1
        if (!result[index]) result[index] = []
        result[index].push(cell)
      })
    })
    const kicks = origin[this.type]
    const kick = kicks[this.rotateIndex % kicks.length]
    const nextRotateIndex = this.rotateIndex + 1 >= kicks.length ? 0 : this.rotateIndex + 1
    return new Block({
      type: this.type,
      shape: result,
      xy: [this.xy[0] + kick[0], this.xy[1] + kick[1]],
      rotateIndex: nextRotateIndex,
      timeStamp: this.timeStamp,
    })
  }

  fall(n = 1) {
    return new Block({
      type: this.type,
      shape: this.shape,
      xy: [this.xy[0] + n, this.xy[1]],
      rotateIndex: this.rotateIndex,
      timeStamp: Date.now(),
    })
  }

  right() {
    return new Block({
      type: this.type,
      shape: this.shape,
      xy: [this.xy[0], this.xy[1] + 1],
      rotateIndex: this.rotateIndex,
      timeStamp: this.timeStamp,
    })
  }

  left() {
    return new Block({
      type: this.type,
      shape: this.shape,
      xy: [this.xy[0], this.xy[1] - 1],
      rotateIndex: this.rotateIndex,
      timeStamp: this.timeStamp,
    })
  }

  clone() {
    return new Block({
      type: this.type,
      shape: this.shape.map((row) => [...row]),
      xy: [...this.xy],
      rotateIndex: this.rotateIndex,
      timeStamp: this.timeStamp,
    })
  }
}
