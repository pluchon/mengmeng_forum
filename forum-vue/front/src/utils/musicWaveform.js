const DEFAULT_BAR_COUNT = 96
const MIN_HEIGHT = 6
const MAX_HEIGHT = 36

function fallbackPeaks(barCount = DEFAULT_BAR_COUNT) {
  return Array.from({ length: barCount }, (_, i) => MIN_HEIGHT + ((i * 7) % 5) * 4)
}

// 取 5%~95% 分位做归一化基准，再用 gamma 把中段拉开。
// 之前是「块内最大绝对值 / 全曲最大值」：现代母带把响度压到接近 0dB，
// 几乎每个块的瞬时峰值都贴着 1.0，画出来就是一条平的栅栏。
const PEAK_GAMMA = 0.65

function quantile(sorted, ratio) {
  if (!sorted.length) return 0
  const pos = (sorted.length - 1) * Math.min(1, Math.max(0, ratio))
  const low = Math.floor(pos)
  const high = Math.min(sorted.length - 1, low + 1)
  return sorted[low] + (sorted[high] - sorted[low]) * (pos - low)
}

function normalizePeaks(rawPeaks) {
  const sorted = [...rawPeaks].sort((a, b) => a - b)
  const floor = quantile(sorted, 0.05)
  const ceil = quantile(sorted, 0.95)
  const span = Math.max(ceil - floor, 1e-6)
  return rawPeaks.map((peak) => {
    const clamped = Math.min(1, Math.max(0, (peak - floor) / span))
    const shaped = Math.pow(clamped, PEAK_GAMMA)
    return Math.round(MIN_HEIGHT + shaped * (MAX_HEIGHT - MIN_HEIGHT))
  })
}

export function peaksFromBuffer(buffer, barCount = DEFAULT_BAR_COUNT) {
  const channel = buffer.getChannelData(0)
  const blockSize = Math.max(1, Math.floor(channel.length / barCount))
  const raw = []
  for (let i = 0; i < barCount; i += 1) {
    const start = i * blockSize
    const end = Math.min(start + blockSize, channel.length)
    // RMS 而不是 max：峰值几乎恒等于满刻度，均方根才反映这一段的实际响度
    let sum = 0
    let count = 0
    for (let j = start; j < end; j += 1) {
      sum += channel[j] * channel[j]
      count += 1
    }
    raw.push(count ? Math.sqrt(sum / count) : 0)
  }
  return normalizePeaks(raw)
}

async function readArrayBuffer(source) {
  if (source instanceof Blob) {
    return source.arrayBuffer()
  }
  if (typeof source === 'string') {
    const res = await fetch(source)
    if (!res.ok) {
      throw new Error('audio fetch failed')
    }
    return res.arrayBuffer()
  }
  throw new Error('unsupported audio source')
}

export async function decodeAudioSource(source) {
  const arrayBuffer = await readArrayBuffer(source)
  const audioContext = new (window.AudioContext || window.webkitAudioContext)()
  try {
    return await audioContext.decodeAudioData(arrayBuffer.slice(0))
  } finally {
    if (audioContext.close) {
      await audioContext.close()
    }
  }
}

export async function analyzeAudioPeaks(source, barCount = DEFAULT_BAR_COUNT) {
  try {
    const buffer = await decodeAudioSource(source)
    return peaksFromBuffer(buffer, barCount)
  } catch {
    return fallbackPeaks(barCount)
  }
}

export function sliceAudioBuffer(buffer, startSec, endSec) {
  const sampleRate = buffer.sampleRate
  const start = Math.max(0, Math.floor(startSec * sampleRate))
  const end = Math.min(buffer.length, Math.floor(endSec * sampleRate))
  const length = Math.max(0, end - start)
  const sliced = new AudioBuffer({
    numberOfChannels: buffer.numberOfChannels,
    length,
    sampleRate,
  })
  for (let ch = 0; ch < buffer.numberOfChannels; ch += 1) {
    const src = buffer.getChannelData(ch)
    const dst = sliced.getChannelData(ch)
    dst.set(src.subarray(start, end))
  }
  return sliced
}

function writeString(view, offset, text) {
  for (let i = 0; i < text.length; i += 1) {
    view.setUint8(offset + i, text.charCodeAt(i))
  }
}

export function encodeWav(buffer) {
  const channels = buffer.numberOfChannels
  const sampleRate = buffer.sampleRate
  const bitsPerSample = 16
  const blockAlign = (channels * bitsPerSample) / 8
  const dataLength = buffer.length * blockAlign
  const arrayBuffer = new ArrayBuffer(44 + dataLength)
  const view = new DataView(arrayBuffer)

  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + dataLength, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, channels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * blockAlign, true)
  view.setUint16(32, blockAlign, true)
  view.setUint16(34, bitsPerSample, true)
  writeString(view, 36, 'data')
  view.setUint32(40, dataLength, true)

  let offset = 44
  for (let i = 0; i < buffer.length; i += 1) {
    for (let ch = 0; ch < channels; ch += 1) {
      const sample = Math.max(-1, Math.min(1, buffer.getChannelData(ch)[i]))
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
      offset += 2
    }
  }
  return new Blob([arrayBuffer], { type: 'audio/wav' })
}
