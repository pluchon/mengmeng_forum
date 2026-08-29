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
  // 顶部/底部弹幕各自的占位表：key 是槽位序号，值是该槽空出来的视频时间
  let fixedSlotReleaseAt = { 1: [], 2: [] }
  // trySpawn 不必每帧跑：视频推进不到一帧的距离时没有新弹幕可出
  let lastSpawnScanMs = -1
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
    fixedSlotReleaseAt = { 1: [], 2: [] }
  }

  function resetPlaybackState() {
    clearBufferTimer()
    clearActiveItems()
    loadedChunkIds.value = new Set()
    catalog.value = new Map()
    lastVideoMs = 0
    lastBufferChunk = -1
    lastSpawnScanMs = -1
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
    // 只清屏上正在飞的，已下载的分片留着：
    // 弹幕分片是不变的静态数据，拖一次进度条重下一次纯属浪费
    clearActiveItems()
    lastBufferChunk = -1
    lastSpawnScanMs = -1
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

  // 同一时刻的多条顶部（或底部）弹幕原本 y 完全相同，会叠成一团。
  // 这里按停留时长占位，依次往下（顶部）/ 往上（底部）排
  function pickFixedSlot(mode, videoTimeMs, laneH) {
    const slots = fixedSlotReleaseAt[mode] || []
    const areaH = getDisplayAreaHeight()
    const maxSlots = Math.max(1, Math.floor((areaH - 16) / laneH))
    for (let i = 0; i < maxSlots; i += 1) {
      if ((slots[i] || 0) <= videoTimeMs) {
        slots[i] = videoTimeMs + DANMAKU_FIXED_DURATION_MS
        fixedSlotReleaseAt[mode] = slots
        return i
      }
    }
    // 全占满了就压在最后一槽，宁可重叠也别漏掉
    const last = maxSlots - 1
    slots[last] = videoTimeMs + DANMAKU_FIXED_DURATION_MS
    fixedSlotReleaseAt[mode] = slots
    return last
  }

  function spawnFixedDanmaku(record, elapsedMs, mode) {
    const fontSize = Number(record.fontSize ?? 1)
    const width = estimateDanmakuWidth(record.content, fontSize)
    const areaH = getDisplayAreaHeight()
    const laneH = getDanmakuLaneHeight(fontSize)
    const slot = pickFixedSlot(mode, Number(record.videoTimeMs) || 0, laneH)
    const y = mode === DANMAKU_MODE_TOP
      ? 8 + slot * laneH
      : Math.max(8, areaH - laneH - 8 - slot * laneH)
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

  // 原本用 Math.random() 抽签，但被抽掉的记录不会进 spawnedIds，
  // 下一帧还会再抽一次——600ms 容差里有几十帧，等于几乎不生效。
  // 改成按 id 取模的确定性判据：稳定，刷新前后看到的也一致
  function shouldSkipByDensity(record) {
    const density = readSettings().density || 'standard'
    if (density !== 'low') return false
    return (Number(record?.id) || 0) % 2 !== 0
  }

  function trySpawn(videoTimeMs, lookbackMs = DANMAKU_SPAWN_TOLERANCE_MS) {
    for (const record of catalog.value.values()) {
      if (spawnedIds.value.has(record.id)) continue
      if (!shouldShowDanmakuRecord(record, readSettings())) continue
      const delta = videoTimeMs - Number(record.videoTimeMs || 0)
      if (delta >= 0 && delta <= lookbackMs) {
        if (shouldSkipByDensity(record)) continue
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
      // trySpawn 要遍历整个 catalog（长视频几千条），不必每帧都扫；
      // 视频推进不到 100ms 时不可能有新弹幕该出场
      if (readSettings().enabled !== false
          && (lastSpawnScanMs < 0 || Math.abs(videoTimeMs - lastSpawnScanMs) >= 100)) {
        lastSpawnScanMs = videoTimeMs
        maybeEnsureBuffer(videoTimeMs)
        trySpawn(videoTimeMs)
      }
    }

    const scrollPaused = !!options.scrollPaused?.value
    const moving = playing && readSettings().enabled !== false && !scrollPaused
    const deltaSec = moving
      ? Math.min(0.05, Math.max(0, (ts - (lastFrameTs || ts)) / 1000)) * playbackRate
      : 0
    lastFrameTs = ts

    // 就地改 x，不再每帧给每条弹幕造一个新对象：
    // ref 里的对象本身是响应式的，改属性一样能触发重渲染
    if (moving && deltaSec > 0) {
      const step = DANMAKU_SPEED_PX_PER_SEC * deltaSec
      for (const item of activeItems.value) {
        if (item.mode === DANMAKU_MODE_SCROLL) item.x -= step
      }
    }

    // 只有真的淘汰了东西才换数组，否则每帧无条件重建会白白触发一轮 diff
    const kept = activeItems.value.filter((item) => {
      if (item.expiresAtVideoMs != null && videoTimeMs > item.expiresAtVideoMs) return false
      if (item.mode === DANMAKU_MODE_SCROLL && item.x <= -item.width - 24) return false
      return true
    })
    if (kept.length !== activeItems.value.length) {
      activeItems.value = kept
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
    // 只重置播放进度相关的状态，catalog / loadedChunkIds 留着：
    // 循环一轮就把整片弹幕重下一遍太亏
    clearBufferTimer()
    clearActiveItems()
    lastVideoMs = 0
    lastBufferChunk = -1
    lastSpawnScanMs = -1
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
