// 从封面图采样柔和主题色，用于卡片描边。 CORS / 污染画布失败时返回 null，由调用方使用类型默认色

function clampByte(n) {
  return Math.max(0, Math.min(255, Math.round(n)))
}

function softenRgb(r, g, b, mixTowardWhite = 0.32) {
  const t = Math.max(0, Math.min(1, mixTowardWhite))
  return {
    r: clampByte(r + (255 - r) * t),
    g: clampByte(g + (255 - g) * t),
    b: clampByte(b + (255 - b) * t),
  }
}

// 略提高饱和度，避免主题描边在白底上发灰
function boostSaturation(r, g, b, amount = 0.22) {
  const avg = (r + g + b) / 3
  const t = Math.max(0, Math.min(1, amount))
  return {
    r: clampByte(avg + (r - avg) * (1 + t)),
    g: clampByte(avg + (g - avg) * (1 + t)),
    b: clampByte(avg + (b - avg) * (1 + t)),
  }
}

function saturation(r, g, b) {
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  if (max === 0) return 0
  return (max - min) / max
}

export function extractCoverThemeOutline(img) {
  try {
    if (!img || !img.naturalWidth || !img.naturalHeight) return null
    const size = 32
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return null
    ctx.drawImage(img, 0, 0, size, size)
    const { data } = ctx.getImageData(0, 0, size, size)

    let best = null
    let bestScore = -1
    let sumR = 0
    let sumG = 0
    let sumB = 0
    let count = 0

    for (let i = 0; i < data.length; i += 4) {
      const a = data[i + 3]
      if (a < 200) continue
      const r = data[i]
      const g = data[i + 1]
      const b = data[i + 2]
      const lum = (r * 299 + g * 587 + b * 114) / 1000
      // 跳过过亮/过暗像素，避免描边发灰或发黑
      if (lum < 28 || lum > 235) continue
      const sat = saturation(r, g, b)
      sumR += r
      sumG += g
      sumB += b
      count += 1
      const score = sat * 1.4 + (1 - Math.abs(lum - 140) / 140) * 0.6
      if (score > bestScore) {
        bestScore = score
        best = { r, g, b }
      }
    }

    const base = best || (count
      ? { r: sumR / count, g: sumG / count, b: sumB / count }
      : null)
    if (!base) return null
    const boosted = boostSaturation(base.r, base.g, base.b, 0.28)
    const soft = softenRgb(boosted.r, boosted.g, boosted.b, 0.28)
    return `rgb(${soft.r}, ${soft.g}, ${soft.b})`
  } catch {
    return null
  }
}

// 无封面或采样失败时的类型默认描边 白底上需比设计稿略深才看得见
export function defaultCardOutline(article) {
  const isQuestion = Number(article?.articleType) === 1
  if (!isQuestion) return '#E8D6E4'
  const status = Number(article?.questionStatus)
  if (status === 1) return '#C8E4D2'
  if (status === 2) return '#D8D4DC'
  return '#EFC9D2'
}
