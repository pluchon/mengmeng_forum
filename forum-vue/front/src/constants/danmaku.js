// 弹幕模式，与后端 mode 一致
export const DANMAKU_MODE_SCROLL = 0
export const DANMAKU_MODE_TOP = 1
export const DANMAKU_MODE_BOTTOM = 2

export const DANMAKU_MODE_OPTIONS = [
  { value: DANMAKU_MODE_SCROLL, label: '滚动' },
  { value: DANMAKU_MODE_TOP, label: '顶部' },
  { value: DANMAKU_MODE_BOTTOM, label: '底部' },
]

// 字号档位，与后端 font_size 一致
export const DANMAKU_FONT_SIZE_SMALL = 0
export const DANMAKU_FONT_SIZE_STANDARD = 1

export const DANMAKU_FONT_SIZE_OPTIONS = [
  { value: DANMAKU_FONT_SIZE_SMALL, label: '小' },
  { value: DANMAKU_FONT_SIZE_STANDARD, label: '标准' },
]

export const DANMAKU_DEFAULT_MODE = DANMAKU_MODE_SCROLL
export const DANMAKU_DEFAULT_FONT_SIZE = DANMAKU_FONT_SIZE_STANDARD

// 预设颜色，编码与后端 color_code 一致
export const DANMAKU_COLOR_PRESETS = [
  { code: 0, label: '白色', hex: '#FFFFFF' },
  { code: 1, label: '红色', hex: '#FF4444' },
  { code: 2, label: '黄色', hex: '#FFCC00' },
  { code: 3, label: '绿色', hex: '#44DD66' },
  { code: 4, label: '蓝色', hex: '#4488FF' },
  { code: 5, label: '粉色', hex: '#FF66AA' },
  { code: 6, label: '橙色', hex: '#FF8833' },
  { code: 7, label: '紫色', hex: '#BB66FF' },
  { code: 8, label: '青色', hex: '#33DDDD' },
]

export const DANMAKU_MAX_CONTENT_LENGTH = 30

export const DANMAKU_DEFAULT_COLOR_CODE = 0

export const DANMAKU_SETTINGS_STORAGE_KEY = 'forum_danmaku_settings_v2'

export const DANMAKU_AREA_OPTIONS = [
  { value: 25, label: '1/4 屏' },
  { value: 50, label: '半屏' },
  { value: 75, label: '3/4 屏' },
  { value: 100, label: '全屏' },
]

export const DANMAKU_DENSITY_OPTIONS = [
  { value: 'low', label: '少' },
  { value: 'standard', label: '标准' },
  { value: 'high', label: '多' },
]

export const DANMAKU_TYPE_FILTER_OPTIONS = [
  { key: 'showScroll', label: '滚动' },
  { key: 'showTop', label: '顶部' },
  { key: 'showBottom', label: '底部' },
]

export const DANMAKU_DEFAULT_SETTINGS = {
  enabled: true,
  opacity: 0.85,
  areaPercent: 100,
  showScroll: true,
  showTop: true,
  showBottom: true,
  density: 'standard',
  coloredOnly: false,
}

export const DANMAKU_PRELOAD_AHEAD_MS = 60_000

export const DANMAKU_QUERY_CHUNK_MS = 60_000

export const DANMAKU_SPAWN_TOLERANCE_MS = 600

export const DANMAKU_SPEED_PX_PER_SEC = 110

export const DANMAKU_LANE_HEIGHT = 28

export const DANMAKU_FIXED_DURATION_MS = 4500

export const DANMAKU_FONT_SIZE_PX = {
  [DANMAKU_FONT_SIZE_SMALL]: 14,
  [DANMAKU_FONT_SIZE_STANDARD]: 18,
}

export function getDanmakuColorHex(code) {
  const preset = DANMAKU_COLOR_PRESETS.find((item) => item.code === Number(code))
  return preset?.hex || DANMAKU_COLOR_PRESETS[0].hex
}

export function getDanmakuFontSizePx(fontSizeCode) {
  const code = Number(fontSizeCode)
  return DANMAKU_FONT_SIZE_PX[code] ?? DANMAKU_FONT_SIZE_PX[DANMAKU_FONT_SIZE_STANDARD]
}

export function getDanmakuLaneHeight(fontSizeCode) {
  const px = getDanmakuFontSizePx(fontSizeCode)
  return px <= 14 ? 22 : 28
}

export function formatDanmakuTimeMs(ms) {
  const totalSec = Math.max(0, Math.floor(Number(ms) / 1000))
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function getDanmakuModeLabel(mode) {
  const item = DANMAKU_MODE_OPTIONS.find((opt) => opt.value === Number(mode))
  return item?.label || '滚动'
}

export function loadDanmakuSettings() {
  try {
    let raw = localStorage.getItem(DANMAKU_SETTINGS_STORAGE_KEY)
    if (!raw) {
      raw = localStorage.getItem('forum_danmaku_settings_v1')
    }
    if (!raw) return { ...DANMAKU_DEFAULT_SETTINGS }
    const parsed = JSON.parse(raw)
    return normalizeSettings(parsed)
  } catch {
    return { ...DANMAKU_DEFAULT_SETTINGS }
  }
}

export function saveDanmakuSettings(settings) {
  const payload = normalizeSettings(settings)
  localStorage.setItem(DANMAKU_SETTINGS_STORAGE_KEY, JSON.stringify(payload))
  return payload
}

function normalizeSettings(parsed) {
  return {
    enabled: parsed.enabled !== false,
    opacity: clampOpacity(parsed.opacity),
    areaPercent: normalizeAreaPercent(parsed.areaPercent),
    showScroll: parsed.showScroll !== false,
    showTop: parsed.showTop !== false,
    showBottom: parsed.showBottom !== false,
    density: normalizeDensity(parsed.density),
    coloredOnly: parsed.coloredOnly === true,
  }
}

function clampOpacity(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return DANMAKU_DEFAULT_SETTINGS.opacity
  return Math.min(1, Math.max(0.2, n))
}

function normalizeAreaPercent(value) {
  const allowed = DANMAKU_AREA_OPTIONS.map((item) => item.value)
  const n = Number(value)
  if (allowed.includes(n)) return n
  return DANMAKU_DEFAULT_SETTINGS.areaPercent
}

function normalizeDensity(value) {
  const allowed = DANMAKU_DENSITY_OPTIONS.map((item) => item.value)
  if (allowed.includes(value)) return value
  return DANMAKU_DEFAULT_SETTINGS.density
}

export function estimateDanmakuWidth(text, fontSizeCode = DANMAKU_FONT_SIZE_STANDARD) {
  const str = String(text || '')
  const scale = getDanmakuFontSizePx(fontSizeCode) / DANMAKU_FONT_SIZE_PX[DANMAKU_FONT_SIZE_STANDARD]
  let width = 0
  for (const ch of str) {
    width += /[\u4e00-\u9fff]/.test(ch) ? 16 * scale : 9 * scale
  }
  return Math.max(24, width + 12 * scale)
}

export function shouldShowDanmakuRecord(record, settings) {
  if (!record) return false
  const mode = Number(record.mode ?? DANMAKU_MODE_SCROLL)
  if (mode === DANMAKU_MODE_TOP && settings?.showTop === false) return false
  if (mode === DANMAKU_MODE_BOTTOM && settings?.showBottom === false) return false
  if (mode === DANMAKU_MODE_SCROLL && settings?.showScroll === false) return false
  if (settings?.coloredOnly && Number(record.colorCode) === DANMAKU_DEFAULT_COLOR_CODE) return false
  return true
}
