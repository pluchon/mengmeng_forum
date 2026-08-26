import { ref, computed, watch } from 'vue'
import { listDanmakuByTimeWindow } from '@/api/danmaku'
import {
  DANMAKU_FIXED_DURATION_MS,
  DANMAKU_LANE_HEIGHT,
  DANMAKU_MODE_BOTTOM,
  DANMAKU_MODE_SCROLL,
  DANMAKU_MODE_TOP,
  DANMAKU_PRELOAD_AHEAD_MS,
  DANMAKU_QUERY_CHUNK_MS,
  DANMAKU_SPAWN_TOLERANCE_MS,
  DANMAKU_SPEED_PX_PER_SEC,
  estimateDanmakuWidth,
  getDanmakuColorHex,
  getDanmakuFontSizePx,
  getDanmakuLaneHeight,
  shouldShowDanmakuRecord,
} from '@/constants/danmaku'

export function useDanmakuEngine(options) {
  const catalog = ref(new Map())
  const spawnedIds = ref(new Set())
  const activeItems = ref([])
  const laneReleaseAt = ref([])
  const loadedChunkIds = ref(new Set())
  const layerSize = ref({ width: 0, height: 0 })

  let rafId = null
  let lastVideoMs = 0
  let lastFrameTs = 0
  let lastBufferChunk = -1
  let bufferTimer = null
  let bufferLoading = false
  let pendingBufferMs = null
  let seekInFlight = false

  function readSettings() {
    return options.settings?.value || {}
  }

  const CONTROLS_RESERVED_PX = 54

  function getDisplayAreaHeight() {
    const areaPercent = Number(readSettings().areaPercent) || 100
    const available = Math.max(0, layerSize.value.height - CONTROLS_RESERVED_PX)
    return (available * areaPercent) / 100
  }

  const maxLanes = computed(() => {
    const areaH = getDisplayAreaHeight()
    const base = Math.max(1, Math.floor(areaH / DANMAKU_LANE_HEIGHT))
    const density = readSettings().density || 'standard'
    if (density === 'low') return Math.max(1, Math.floor(base * 0.55))
    if (density === 'high') return Math.max(1, Math.ceil(base * 1.25))
    return base
  })

  function setLayerSize(width, height) {
    layerSize.value = {
      width: Math.max(0, Number(width) || 0),
      height: Math.max(0, Number(height) || 0),
    }
  }

  function clearBufferTimer() {
    if (bufferTimer != null) {
      clearTimeout(bufferTimer)
      bufferTimer = null
    }
    pendingBufferMs = null
  }

  function clearActiveItems() {
    spawnedIds.value = new Set()
    activeItems.value = []
    laneReleaseAt.value = []
  }

  function resetPlaybackState() {
    clearBufferTimer()
    clearActiveItems()
    loadedChunkIds.value = new Set()
    catalog.value = new Map()
    lastVideoMs = 0
    lastBufferChunk = -1
    bufferLoading = false
    seekInFlight = false
  }

  function settingsFilterFingerprint(settings) {
    if (!settings) return ''
    return JSON.stringify({
      enabled: settings.enabled,
      showScroll: settings.showScroll,
      showTop: settings.showTop,
      showBottom: settings.showBottom,
      coloredOnly: settings.coloredOnly,
      density: settings.density,
      areaPercent: settings.areaPercent,
    })
  }

  watch(
    () => options.settings?.value,
    (next, prev) => {
      if (!prev) return
      if (settingsFilterFingerprint(next) !== settingsFilterFingerprint(prev)) {
        clearActiveItems()
      }
    },
    { deep: true },
  )

  function mergeCatalog(items) {
    if (!items?.length) return
    const next = new Map(catalog.value)
    for (const item of items) {
      if (item?.id != null) next.set(item.id, item)
    }
    catalog.value = next
  }

  function getChunkIndex(videoTimeMs) {
    return Math.floor(Math.max(0, Number(videoTimeMs) || 0) / DANMAKU_QUERY_CHUNK_MS)
  }

  function getChunkRange(chunkIndex) {
    const fromMs = chunkIndex * DANMAKU_QUERY_CHUNK_MS
    const toMs = fromMs + DANMAKU_QUERY_CHUNK_MS
    return { fromMs, toMs }
  }

  function isChunkLoaded(chunkIndex) {
    return loadedChunkIds.value.has(chunkIndex)
  }

  function markChunkLoaded(chunkIndex) {
    loadedChunkIds.value = new Set(loadedChunkIds.value).add(chunkIndex)
  }

  async function loadChunk(chunkIndex) {
    if (chunkIndex < 0 || isChunkLoaded(chunkIndex)) return
    const articleId = options.getArticleId?.()
    if (!articleId) return
    const { fromMs, toMs } = getChunkRange(chunkIndex)
    const res = await listDanmakuByTimeWindow({ articleId, fromMs, toMs })
    if (res?.code === 0) {
      mergeCatalog(res.data || [])
      markChunkLoaded(chunkIndex)
    }
  }

  async function ensureBuffer(videoTimeMs) {
    const articleId = options.getArticleId?.()
    if (!articleId) return
    const currentChunk = getChunkIndex(videoTimeMs)
    const aheadChunk = getChunkIndex(videoTimeMs + DANMAKU_PRELOAD_AHEAD_MS)
    const startChunk = Math.max(0, currentChunk - 1)
    const tasks = []
    for (let chunkIndex = startChunk; chunkIndex <= aheadChunk; chunkIndex += 1) {
      if (!isChunkLoaded(chunkIndex)) {
        tasks.push(loadChunk(chunkIndex))
      }
    }
    if (tasks.length) {
      await Promise.all(tasks)
    }
  }

  function runBufferFetch(videoTimeMs) {
    if (bufferLoading) {
      pendingBufferMs = videoTimeMs
      return
    }
    bufferLoading = true
    ensureBuffer(videoTimeMs)
      .catch(() => {})
      .finally(() => {
        bufferLoading = false
        if (pendingBufferMs != null) {
          const nextMs = pendingBufferMs
          pendingBufferMs = null
          runBufferFetch(nextMs)
        }
      })
  }

  function scheduleBufferFetch(videoTimeMs) {
    if (bufferTimer != null) return
    bufferTimer = setTimeout(() => {
      bufferTimer = null
      runBufferFetch(videoTimeMs)
    }, 250)
  }

  function maybeEnsureBuffer(videoTimeMs) {
    const currentChunk = getChunkIndex(videoTimeMs)
    const aheadChunk = getChunkIndex(videoTimeMs + DANMAKU_PRELOAD_AHEAD_MS)
    const needFetch = currentChunk !== lastBufferChunk
      || !isChunkLoaded(currentChunk)
      || !isChunkLoaded(aheadChunk)
    if (!needFetch) return
    lastBufferChunk = currentChunk
    scheduleBufferFetch(videoTimeMs)
  }

  async function handleSeek(videoTimeMs) {
    if (seekInFlight) return
    seekInFlight = true
    clearBufferTimer()
    activeItems.value = []
    laneReleaseAt.value = []
    spawnedIds.value = new Set()
    loadedChunkIds.value = new Set()
    lastBufferChunk = -1
    lastVideoMs = videoTimeMs
    try {
      await ensureBuffer(videoTimeMs)
      trySpawn(videoTimeMs, 8000)
    } finally {
      seekInFlight = false
    }
  }

  function laneHasOverlap(lane, startX, width) {
    for (const item of activeItems.value) {
      if (item.mode !== DANMAKU_MODE_SCROLL || item.lane !== lane) continue
      const itemRight = item.x + item.width
      const newRight = startX + width
      if (itemRight > startX && item.x < newRight) {
        return true
      }
    }
    return false
  }

  function pickLane(videoTimeMs, width) {
    const lanes = maxLanes.value
    const release = laneReleaseAt.value.slice()
    while (release.length < lanes) release.push(0)
    const candidates = []
    for (let i = 0; i < lanes; i += 1) {
      if ((release[i] || 0) <= videoTimeMs) candidates.push(i)
    }
    const pool = candidates.length ? candidates : Array.from({ length: lanes }, (_, i) => i)
    const shuffled = pool.slice().sort(() => Math.random() - 0.5)
    for (const lane of shuffled) {
      const laneY = lane * DANMAKU_LANE_HEIGHT + 6
      const startX = layerSize.value.width
      if (!laneHasOverlap(lane, startX, width)) {
        const durationSec = (layerSize.value.width + width) / DANMAKU_SPEED_PX_PER_SEC
        release[lane] = videoTimeMs + durationSec * 1000
        laneReleaseAt.value = release
        return lane
      }
    }
    const lane = shuffled[0]
    const durationSec = (layerSize.value.width + width) / DANMAKU_SPEED_PX_PER_SEC
    release[lane] = videoTimeMs + durationSec * 1000
    laneReleaseAt.value = release
    return lane
  }

  function appendActiveItem(item) {
    activeItems.value = [...activeItems.value, item]
    spawnedIds.value = new Set(spawnedIds.value).add(item.id)
  }

  function spawnScrollDanmaku(record, elapsedMs = 0) {
    const fontSize = Number(record.fontSize ?? 1)
    const width = estimateDanmakuWidth(record.content, fontSize)
    const lane = pickLane(record.videoTimeMs, width)
    const y = lane * getDanmakuLaneHeight(fontSize) + 6
    const elapsedSec = Math.max(0, Number(elapsedMs) || 0) / 1000
    const startX = Math.max(-width, layerSize.value.width - elapsedSec * DANMAKU_SPEED_PX_PER_SEC)
    appendActiveItem({
      key: `${record.id}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      id: record.id,
      content: record.content,
      color: getDanmakuColorHex(record.colorCode),
      colorCode: Number(record.colorCode ?? 0),
      fontSize,
      mode: DANMAKU_MODE_SCROLL,
      lane,
      x: startX,
      y,
      width,
      likeCount: Number(record.likeCount) || 0,
      liked: !!record.liked,
      expiresAtVideoMs: null,
    })
  }

  function spawnFixedDanmaku(record, elapsedMs, mode) {
    const fontSize = Number(record.fontSize ?? 1)
    const width = estimateDanmakuWidth(record.content, fontSize)
    const areaH = getDisplayAreaHeight()
    const laneH = getDanmakuLaneHeight(fontSize)
    const y = mode === DANMAKU_MODE_TOP ? 8 : Math.max(8, areaH - laneH - 8)
    const x = Math.max(0, (layerSize.value.width - width) / 2)
    appendActiveItem({
      key: `${record.id}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      id: record.id,
      content: record.content,
      color: getDanmakuColorHex(record.colorCode),
      colorCode: Number(record.colorCode ?? 0),
      fontSize,
      mode,
      lane: null,
      x,
      y,
      width,
      likeCount: Number(record.likeCount) || 0,
      liked: !!record.liked,
      expiresAtVideoMs: Number(record.videoTimeMs) + DANMAKU_FIXED_DURATION_MS,
    })
  }

  function spawnDanmaku(record, elapsedMs = 0) {
    if (!record || record.id == null) return
    if (spawnedIds.value.has(record.id)) return
    if (!shouldShowDanmakuRecord(record, readSettings())) return
    const mode = Number(record.mode ?? DANMAKU_MODE_SCROLL)
    if (mode === DANMAKU_MODE_TOP || mode === DANMAKU_MODE_BOTTOM) {
      spawnFixedDanmaku(record, elapsedMs, mode)
    } else {
      spawnScrollDanmaku(record, elapsedMs)
    }
  }

  function shouldSkipByDensity() {
    const density = readSettings().density || 'standard'
    if (density === 'low') return Math.random() < 0.45
    return false
  }

  function trySpawn(videoTimeMs, lookbackMs = DANMAKU_SPAWN_TOLERANCE_MS) {
    for (const record of catalog.value.values()) {
      if (spawnedIds.value.has(record.id)) continue
      if (!shouldShowDanmakuRecord(record, readSettings())) continue
      const delta = videoTimeMs - Number(record.videoTimeMs || 0)
      if (delta >= 0 && delta <= lookbackMs) {
        if (shouldSkipByDensity()) continue
        spawnDanmaku(record, delta)
      }
    }
  }

  function tick(ts) {
    const state = options.getVideoState?.() || {}
    const playing = !!state.playing
    const playbackRate = Number(state.playbackRate) || 1
    const currentTime = Number(state.currentTime) || 0
    const videoTimeMs = Math.max(0, Math.round(currentTime * 1000))

    if (Math.abs(videoTimeMs - lastVideoMs) > 1500) {
      handleSeek(videoTimeMs)
    } else {
      lastVideoMs = videoTimeMs
      if (readSettings().enabled !== false) {
        maybeEnsureBuffer(videoTimeMs)
        trySpawn(videoTimeMs)
      }
    }

    activeItems.value = activeItems.value.filter((item) => {
      if (item.expiresAtVideoMs != null && videoTimeMs > item.expiresAtVideoMs) {
        return false
      }
      return true
    })

    const scrollPaused = !!options.scrollPaused?.value
    if (playing && readSettings().enabled !== false && !scrollPaused) {
      const prev = lastFrameTs || ts
      const deltaSec = Math.min(0.05, Math.max(0, (ts - prev) / 1000)) * playbackRate
      lastFrameTs = ts
      activeItems.value = activeItems.value
        .map((item) => {
          if (item.mode !== DANMAKU_MODE_SCROLL) return item
          return { ...item, x: item.x - DANMAKU_SPEED_PX_PER_SEC * deltaSec }
        })
        .filter((item) => item.mode !== DANMAKU_MODE_SCROLL || item.x > -item.width - 24)
    } else {
      lastFrameTs = ts
    }

    rafId = requestAnimationFrame(tick)
  }

  function start() {
    stop()
    lastFrameTs = 0
    rafId = requestAnimationFrame(tick)
  }

  function stop() {
    if (rafId != null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    clearBufferTimer()
  }

  function pushLocalDanmaku(record) {
    if (!record?.id) return
    mergeCatalog([record])
    const state = options.getVideoState?.() || {}
    const videoTimeMs = Math.round((Number(state.currentTime) || 0) * 1000)
    const delta = Math.abs(videoTimeMs - Number(record.videoTimeMs || 0))
    if (delta <= DANMAKU_SPAWN_TOLERANCE_MS) {
      spawnDanmaku(record, delta)
    }
  }

  function onReplay() {
    resetPlaybackState()
    maybeEnsureBuffer(0)
  }

  function updateDanmakuLike(danmakuId, nextLiked) {
    const catalogItem = catalog.value.get(danmakuId)
    if (catalogItem) {
      const prevLiked = !!catalogItem.liked
      const base = Number(catalogItem.likeCount) || 0
      const delta = nextLiked ? (prevLiked ? 0 : 1) : (prevLiked ? -1 : 0)
      mergeCatalog([{
        ...catalogItem,
        liked: nextLiked,
        likeCount: Math.max(0, base + delta),
      }])
    }
    activeItems.value = activeItems.value.map((item) => {
      if (item.id !== danmakuId) return item
      const prevLiked = !!item.liked
      const base = Number(item.likeCount) || 0
      const delta = nextLiked ? (prevLiked ? 0 : 1) : (prevLiked ? -1 : 0)
      return {
        ...item,
        liked: nextLiked,
        likeCount: Math.max(0, base + delta),
      }
    })
  }

  const visibleItems = computed(() => {
    if (readSettings().enabled === false) return []
    const s = readSettings()
    return activeItems.value
      .filter((item) => shouldShowDanmakuRecord({ mode: item.mode, colorCode: item.colorCode }, s))
      .map((item) => {
        const catalogItem = catalog.value.get(item.id)
        const likeCount = Number(catalogItem?.likeCount ?? item.likeCount) || 0
        const liked = catalogItem?.liked ?? item.liked ?? false
        return {
          ...item,
          likeCount,
          liked,
          style: {
            transform: `translate3d(${item.x}px, ${item.y}px, 0)`,
            color: item.color,
            fontSize: `${getDanmakuFontSizePx(item.fontSize)}px`,
          },
        }
      })
  })

  const layerStyle = computed(() => {
    const s = readSettings()
    const areaPercent = Number(s.areaPercent) || 100
    const opacity = Number(s.opacity)
    const controlsReserve = 54
    const available = Math.max(0, layerSize.value.height - controlsReserve)
    return {
      top: 0,
      left: 0,
      right: 0,
      bottom: `${controlsReserve}px`,
      height: `${(available * areaPercent) / 100}px`,
      opacity: Number.isFinite(opacity) ? opacity : 0.85,
    }
  })

  function requestBuffer(videoTimeMs) {
    lastBufferChunk = -1
    maybeEnsureBuffer(videoTimeMs)
  }

  return {
    activeItems,
    clearActiveItems,
    layerStyle,
    onReplay,
    pushLocalDanmaku,
    requestBuffer,
    resetPlaybackState,
    setLayerSize,
    start,
    stop,
    updateDanmakuLike,
    visibleItems,
  }
}
