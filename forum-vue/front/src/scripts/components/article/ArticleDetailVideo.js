import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Flag, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import Hls from 'hls.js'
import VideoVolumeIcon from '@/components/common/VideoVolumeIcon.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import { likeDanmaku, sendDanmaku, unlikeDanmaku } from '@/api/danmaku'
import { useUserStore } from '@/stores/user'
import {
  DANMAKU_COLOR_PRESETS,
  DANMAKU_DEFAULT_COLOR_CODE,
  DANMAKU_DEFAULT_FONT_SIZE,
  DANMAKU_DEFAULT_MODE,
  DANMAKU_FONT_SIZE_OPTIONS,
  DANMAKU_MAX_CONTENT_LENGTH,
  DANMAKU_MODE_OPTIONS,
  getDanmakuColorHex,
} from '@/constants/danmaku'
import { useDanmakuEngine } from '@scripts/components/article/useDanmakuEngine'
import { useDanmakuSettings } from '@scripts/components/article/useDanmakuSettings'

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2]

// 与后端 VideoTranscodeStatus 对齐：0 NONE / 1 PROCESSING / 2 READY / 3 FAILED
const TRANSCODE_STATUS = {
  NONE: 0,
  PROCESSING: 1,
  READY: 2,
  FAILED: 3,
}

// HLS fatal 先轻量恢复，耗尽后再降级 MP4（不搞复杂重连状态机）
const HLS_NETWORK_RECOVER_MAX = 3
const HLS_MEDIA_RECOVER_MAX = 3
const HLS_NETWORK_RECOVER_DELAY_MS = 800

function useArticleDetailVideo(props, emit) {
  const userStore = useUserStore()
  const playerRootRef = ref(null)
  const videoEl = ref(null)
  const playing = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const progressDragging = ref(false)
  const volume = ref(0.8)
  const muted = ref(false)
  const playbackRate = ref(1)
  const speedMenuOpen = ref(false)
  const loadError = ref(false)
  const preferMp4Fallback = ref(false)
  const danmuText = ref('')
  const danmuColorCode = ref(DANMAKU_DEFAULT_COLOR_CODE)
  const danmuMode = ref(DANMAKU_DEFAULT_MODE)
  const danmuFontSize = ref(DANMAKU_DEFAULT_FONT_SIZE)
  const colorPickerOpen = ref(false)
  const danmuSending = ref(false)
  const danmuComposeTimeMs = ref(null)
  const playingBeforeDanmuCompose = ref(false)
  const danmakuScrollPaused = ref(false)
  const hoveredDanmakuKey = ref(null)
  const danmakuLikePending = ref(new Set())
  let rafId = null
  let resizeObserver = null
  let danmuComposeBlurTimer = null
  let hlsInstance = null
  let attachingMedia = false
  let suppressMediaError = false
  let hlsNetworkRecoverCount = 0
  let hlsMediaRecoverCount = 0
  let hlsRecoverTimer = null

  const {
    DANMAKU_AREA_OPTIONS,
    DANMAKU_DENSITY_OPTIONS,
    DANMAKU_TYPE_FILTER_OPTIONS,
    closeSettings,
    setAreaPercent,
    setColoredOnly,
    setDensity,
    setEnabled,
    setOpacity,
    setTypeFilter,
    settings,
    settingsOpen,
    toggleSettings,
  } = useDanmakuSettings()

  const danmakuEngine = useDanmakuEngine({
    getArticleId: () => props.articleId,
    settings,
    scrollPaused: danmakuScrollPaused,
    getVideoState: () => ({
      currentTime: currentTime.value,
      playing: playing.value,
      playbackRate: playbackRate.value,
    }),
  })

  const isLoggedIn = computed(() => userStore.isLoggedIn)

  const progressPercent = computed(() => {
    if (!duration.value) return 0
    return Math.min(100, (currentTime.value / duration.value) * 100)
  })

  const danmuColorHex = computed(() => getDanmakuColorHex(danmuColorCode.value))

  const danmuFormatCustomized = computed(() => {
    return Number(danmuColorCode.value) !== DANMAKU_DEFAULT_COLOR_CODE
      || Number(danmuMode.value) !== DANMAKU_DEFAULT_MODE
      || Number(danmuFontSize.value) !== DANMAKU_DEFAULT_FONT_SIZE
  })

  const danmuFormatIconColor = computed(() => {
    return danmuFormatCustomized.value ? '#44DD66' : '#FFFFFF'
  })

  const danmuSendDisabled = computed(() => {
    return danmuSending.value || !String(danmuText.value || '').trim()
  })

  const showProcessingHint = computed(() => {
    return Number(props.transcodeStatus) === TRANSCODE_STATUS.PROCESSING
  })

  function updateLayerSize() {
    const el = playerRootRef.value
    if (!el) return
    danmakuEngine.setLayerSize(el.clientWidth, el.clientHeight)
  }

  function clearHlsRecoverTimer() {
    if (hlsRecoverTimer == null) return
    clearTimeout(hlsRecoverTimer)
    hlsRecoverTimer = null
  }

  function resetHlsRecoverState() {
    clearHlsRecoverTimer()
    hlsNetworkRecoverCount = 0
    hlsMediaRecoverCount = 0
  }

  function destroyHls() {
    clearHlsRecoverTimer()
    if (!hlsInstance) return
    hlsInstance.destroy()
    hlsInstance = null
  }

  function canUseNativeHls(video) {
    return !!video?.canPlayType?.('application/vnd.apple.mpegurl')
  }

  function shouldPreferHls() {
    if (preferMp4Fallback.value) return false
    const url = String(props.hlsUrl || '').trim()
    return Number(props.transcodeStatus) === TRANSCODE_STATUS.READY && !!url
  }

  function applyMp4Source(video) {
    const mp4 = String(props.src || '').trim()
    if (!mp4) {
      loadError.value = true
      return
    }
    suppressMediaError = true
    video.src = mp4
    nextTick(() => {
      suppressMediaError = false
    })
  }

  function fallbackHlsToMp4(mp4) {
    resetHlsRecoverState()
    destroyHls()
    if (mp4) {
      preferMp4Fallback.value = true
      const el = videoEl.value
      if (el) applyMp4Source(el)
      return
    }
    loadError.value = true
    playing.value = false
  }

  function handleHlsFatalError(data, mp4) {
    if (!hlsInstance || !data?.fatal) {
      fallbackHlsToMp4(mp4)
      return
    }
    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
      if (hlsNetworkRecoverCount < HLS_NETWORK_RECOVER_MAX) {
        hlsNetworkRecoverCount += 1
        clearHlsRecoverTimer()
        hlsRecoverTimer = setTimeout(() => {
          hlsRecoverTimer = null
          if (!hlsInstance) return
          try {
            hlsInstance.startLoad()
          } catch {
            fallbackHlsToMp4(mp4)
          }
        }, HLS_NETWORK_RECOVER_DELAY_MS)
        return
      }
      fallbackHlsToMp4(mp4)
      return
    }
    if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
      if (hlsMediaRecoverCount < HLS_MEDIA_RECOVER_MAX) {
        hlsMediaRecoverCount += 1
        try {
          hlsInstance.recoverMediaError()
        } catch {
          fallbackHlsToMp4(mp4)
        }
        return
      }
      fallbackHlsToMp4(mp4)
      return
    }
    fallbackHlsToMp4(mp4)
  }

  function onNetworkOnline() {
    if (preferMp4Fallback.value || loadError.value) return
    if (hlsInstance) {
      try {
        hlsInstance.startLoad()
      } catch {
        // 恢复失败留给下次 fatal / 用户重试
      }
    }
    const ms = Math.max(0, Math.round((Number(currentTime.value) || 0) * 1000))
    danmakuEngine.requestBuffer?.(ms)
  }

  function attachMediaSource() {
    const video = videoEl.value
    if (!video || attachingMedia) return
    attachingMedia = true
    loadError.value = false
    resetHlsRecoverState()
    destroyHls()
    try {
      const hlsUrl = String(props.hlsUrl || '').trim()
      const mp4 = String(props.src || '').trim()
      if (shouldPreferHls()) {
        if (canUseNativeHls(video)) {
          suppressMediaError = true
          video.src = hlsUrl
          nextTick(() => {
            suppressMediaError = false
          })
          return
        }
        if (Hls.isSupported()) {
          suppressMediaError = true
          video.removeAttribute('src')
          hlsInstance = new Hls({ enableWorker: true })
          hlsInstance.loadSource(hlsUrl)
          hlsInstance.attachMedia(video)
          hlsInstance.on(Hls.Events.ERROR, (_event, data) => {
            if (!data?.fatal) return
            handleHlsFatalError(data, mp4)
          })
          nextTick(() => {
            suppressMediaError = false
          })
          return
        }
      }
      applyMp4Source(video)
    } finally {
      attachingMedia = false
    }
  }

  function onMediaError() {
    if (attachingMedia || suppressMediaError) return
    const mp4 = String(props.src || '').trim()
    const hlsUrl = String(props.hlsUrl || '').trim()
    const ready = Number(props.transcodeStatus) === TRANSCODE_STATUS.READY
    if (!preferMp4Fallback.value && ready && hlsUrl && mp4) {
      preferMp4Fallback.value = true
      destroyHls()
      const video = videoEl.value
      if (video) {
        applyMp4Source(video)
        return
      }
    }
    loadError.value = true
    playing.value = false
    stopProgressLoop()
  }

  function retryLoad() {
    const hadHlsPrefer = Number(props.transcodeStatus) === TRANSCODE_STATUS.READY
      && !!String(props.hlsUrl || '').trim()
    // 重试：先回退 MP4；若已是 MP4 则重新挂载
    preferMp4Fallback.value = hadHlsPrefer && !preferMp4Fallback.value
      ? true
      : false
    loadError.value = false
    currentTime.value = 0
    duration.value = 0
    playing.value = false
    nextTick(() => attachMediaSource())
  }

  function onLoadedMetadata(e) {
    const v = e?.target || videoEl.value
    if (!v) return
    duration.value = Number(v.duration) || 0
    v.volume = volume.value
    v.playbackRate = playbackRate.value
    v.play()
      .then(() => {
        playing.value = true
        emit('playing')
      })
      .catch(() => {
        playing.value = !v.paused
        if (!v.paused) emit('playing')
      })
    startProgressLoop()
    danmakuEngine.resetPlaybackState()
    scheduleInitialDanmakuLoad()
  }

  function scheduleInitialDanmakuLoad() {
    nextTick(() => {
      const v = videoEl.value
      const ms = Math.max(0, Math.round((Number(v?.currentTime) || 0) * 1000))
      danmakuEngine.requestBuffer?.(ms)
    })
  }

  function onTimeUpdate(e) {
    const v = e?.target || videoEl.value
    if (!v) return
    currentTime.value = Number(v.currentTime) || 0
    duration.value = Number(v.duration) || duration.value
  }

  function onEnded() {
    const v = videoEl.value
    if (!v) return
    v.currentTime = 0
    currentTime.value = 0
    danmakuEngine.onReplay()
    v.play().catch(() => {})
  }

  function togglePlay() {
    const v = videoEl.value
    if (!v) return
    if (v.paused) {
      v.play().catch(() => {})
      playing.value = true
    } else {
      v.pause()
      playing.value = false
    }
  }

  function seekByPercent(percent) {
    const v = videoEl.value
    if (!v || !duration.value) return
    const p = Math.min(100, Math.max(0, Number(percent) || 0))
    v.currentTime = (p / 100) * duration.value
    currentTime.value = v.currentTime
  }

  function seekFromPointerEvent(e) {
    const rail = e?.currentTarget
    if (!rail) return
    const rect = rail.getBoundingClientRect()
    if (!rect.width) return
    const percent = ((e.clientX - rect.left) / rect.width) * 100
    seekByPercent(percent)
  }

  function onProgressPointerDown(e) {
    if (e?.button != null && e.button !== 0) return
    progressDragging.value = true
    e.currentTarget?.setPointerCapture?.(e.pointerId)
    seekFromPointerEvent(e)
  }

  function onProgressPointerMove(e) {
    if (!progressDragging.value) return
    seekFromPointerEvent(e)
  }

  function onProgressPointerUp(e) {
    if (!progressDragging.value) return
    seekFromPointerEvent(e)
    progressDragging.value = false
    e.currentTarget?.releasePointerCapture?.(e.pointerId)
  }

  function onProgressKeydown(e) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const step = e.key === 'ArrowRight' ? 5 : -5
    seekByPercent(progressPercent.value + step)
  }

  function onVolumeInput(val) {
    const v = videoEl.value
    const next = Math.min(1, Math.max(0, Number(val) || 0))
    volume.value = next
    muted.value = next <= 0
    if (v) {
      v.volume = next
      v.muted = next <= 0
    }
  }

  function toggleMute() {
    const v = videoEl.value
    if (!v) return
    muted.value = !muted.value
    v.muted = muted.value
    if (!muted.value && volume.value <= 0) {
      volume.value = 0.6
      v.volume = 0.6
    }
  }

  function setSpeed(rate) {
    const v = videoEl.value
    const next = Number(rate) || 1
    playbackRate.value = next
    if (v) v.playbackRate = next
    speedMenuOpen.value = false
  }

  function formatTime(sec) {
    const s = Math.max(0, Math.floor(Number(sec) || 0))
    const m = Math.floor(s / 60)
    const r = s % 60
    return `${m}:${String(r).padStart(2, '0')}`
  }

  function captureDanmuComposeAnchor() {
    if (danmuComposeTimeMs.value != null) return
    const v = videoEl.value
    if (!v) return
    danmuComposeTimeMs.value = Math.max(0, Math.round((Number(v.currentTime) || 0) * 1000))
    playingBeforeDanmuCompose.value = playing.value
    if (!v.paused) {
      v.pause()
      playing.value = false
    }
  }

  function resumeAfterDanmuCompose() {
    const v = videoEl.value
    if (!v || !playingBeforeDanmuCompose.value) return
    v.play().catch(() => {})
    playing.value = true
  }

  function clearDanmuComposeSession(shouldResume = true) {
    if (danmuComposeBlurTimer) {
      clearTimeout(danmuComposeBlurTimer)
      danmuComposeBlurTimer = null
    }
    if (shouldResume) {
      resumeAfterDanmuCompose()
    }
    danmuComposeTimeMs.value = null
    playingBeforeDanmuCompose.value = false
  }

  function onDanmuInputFocus() {
    captureDanmuComposeAnchor()
  }

  function onDanmuInputBlur() {
    if (danmuComposeBlurTimer) clearTimeout(danmuComposeBlurTimer)
    danmuComposeBlurTimer = setTimeout(() => {
      if (colorPickerOpen.value || danmuSending.value) return
      const active = document.activeElement
      if (active instanceof Element && active.closest('.detail-video-player__danmu-input-wrap')) {
        return
      }
      clearDanmuComposeSession(true)
    }, 120)
  }

  function toggleColorPicker() {
    colorPickerOpen.value = !colorPickerOpen.value
    if (colorPickerOpen.value) {
      settingsOpen.value = false
      captureDanmuComposeAnchor()
    }
  }

  function selectDanmuColor(code) {
    danmuColorCode.value = Number(code)
  }

  function selectDanmuMode(mode) {
    danmuMode.value = Number(mode)
  }

  function selectDanmuFontSize(fontSize) {
    danmuFontSize.value = Number(fontSize)
  }

  function onDocumentClick(e) {
    const target = e?.target
    if (!(target instanceof Element)) return
    if (colorPickerOpen.value && !target.closest('.detail-video-player__danmu-format')) {
      colorPickerOpen.value = false
    }
    if (settingsOpen.value && !target.closest('.detail-video-player__danmu-settings')) {
      settingsOpen.value = false
    }
  }

  function onDanmakuLayerEnter() {
    danmakuScrollPaused.value = true
  }

  function onDanmakuLayerLeave() {
    danmakuScrollPaused.value = false
    hoveredDanmakuKey.value = null
  }

  function onDanmakuItemEnter(item) {
    hoveredDanmakuKey.value = item?.key || null
  }

  function onDanmakuItemLeave() {
    hoveredDanmakuKey.value = null
  }

  async function toggleDanmakuLike(item) {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录后再点赞')
      return
    }
    const danmakuId = item?.id
    if (!danmakuId || danmakuLikePending.value.has(danmakuId)) return
    const wasLiked = !!item.liked
    const nextLiked = !wasLiked
    danmakuLikePending.value = new Set(danmakuLikePending.value).add(danmakuId)
    danmakuEngine.updateDanmakuLike(danmakuId, nextLiked)
    try {
      const res = nextLiked ? await likeDanmaku(danmakuId) : await unlikeDanmaku(danmakuId)
      if (res?.code !== 0) {
        danmakuEngine.updateDanmakuLike(danmakuId, wasLiked)
        ElMessage.error(res?.message || '操作失败')
      }
    } catch (err) {
      danmakuEngine.updateDanmakuLike(danmakuId, wasLiked)
      ElMessage.error(err?.message || '操作失败')
    } finally {
      const next = new Set(danmakuLikePending.value)
      next.delete(danmakuId)
      danmakuLikePending.value = next
    }
  }

  function reportDanmakuItem(item) {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录后再举报')
      return
    }
    emit('report-danmaku', item)
  }

  async function sendDanmu() {
    if (danmuSending.value) return
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录后再发送弹幕')
      return
    }
    const content = String(danmuText.value || '').trim()
    if (!content) {
      ElMessage.warning('请输入弹幕内容')
      return
    }
    if (content.length > DANMAKU_MAX_CONTENT_LENGTH) {
      ElMessage.warning(`弹幕最多 ${DANMAKU_MAX_CONTENT_LENGTH} 个字`)
      return
    }
    if (!props.articleId) {
      ElMessage.error('帖子信息异常，暂时无法发送弹幕')
      return
    }
    const videoTimeMs = danmuComposeTimeMs.value != null
      ? danmuComposeTimeMs.value
      : Math.max(0, Math.round((Number(currentTime.value) || 0) * 1000))
    danmuSending.value = true
    try {
      const res = await sendDanmaku({
        articleId: Number(props.articleId),
        content,
        colorCode: danmuColorCode.value,
        mode: danmuMode.value,
        fontSize: danmuFontSize.value,
        videoTimeMs,
      })
      if (res?.code !== 0) {
        ElMessage.error(res?.message || '弹幕发送失败')
        return
      }
      danmuText.value = ''
      colorPickerOpen.value = false
      clearDanmuComposeSession(true)
      danmakuEngine.pushLocalDanmaku(res.data)
      ElMessage.success('弹幕已发送')
    } catch (err) {
      ElMessage.error(err?.message || '弹幕发送失败')
    } finally {
      danmuSending.value = false
    }
  }

  function startProgressLoop() {
    stopProgressLoop()
    const tick = () => {
      const v = videoEl.value
      if (v) currentTime.value = Number(v.currentTime) || 0
      rafId = requestAnimationFrame(tick)
    }
    rafId = requestAnimationFrame(tick)
  }

  function stopProgressLoop() {
    if (rafId != null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
  }

  watch(
    () => [props.src, props.hlsUrl, props.transcodeStatus],
    () => {
      preferMp4Fallback.value = false
      loadError.value = false
      currentTime.value = 0
      duration.value = 0
      playing.value = false
      danmuText.value = ''
      colorPickerOpen.value = false
      clearDanmuComposeSession(false)
      danmakuEngine.resetPlaybackState()
      nextTick(() => attachMediaSource())
    },
  )

  watch(() => props.articleId, () => {
    danmakuEngine.resetPlaybackState()
  })

  onMounted(() => {
    document.addEventListener('click', onDocumentClick)
    window.addEventListener('online', onNetworkOnline)
    danmakuEngine.start()
    nextTick(() => attachMediaSource())
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => updateLayerSize())
      nextTick(() => {
        if (playerRootRef.value) resizeObserver.observe(playerRootRef.value)
      })
    }
  })

  onUnmounted(() => {
    stopProgressLoop()
    resetHlsRecoverState()
    destroyHls()
    danmakuEngine.stop()
    clearDanmuComposeSession(false)
    document.removeEventListener('click', onDocumentClick)
    window.removeEventListener('online', onNetworkOnline)
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  })

  return {
    SPEEDS,
    DANMAKU_AREA_OPTIONS,
    DANMAKU_COLOR_PRESETS,
    DANMAKU_DENSITY_OPTIONS,
    DANMAKU_FONT_SIZE_OPTIONS,
    DANMAKU_MAX_CONTENT_LENGTH,
    DANMAKU_MODE_OPTIONS,
    DANMAKU_TYPE_FILTER_OPTIONS,
    closeSettings,
    colorPickerOpen,
    currentTime,
    danmuColorCode,
    danmuColorHex,
    danmuFontSize,
    danmuFormatCustomized,
    danmuFormatIconColor,
    danmuMode,
    danmuSendDisabled,
    danmuSending,
    danmuText,
    danmakuLayerStyle: danmakuEngine.layerStyle,
    danmakuVisibleItems: danmakuEngine.visibleItems,
    duration,
    formatTime,
    hoveredDanmakuKey,
    isLoggedIn,
    loadError,
    muted,
    onDanmakuItemEnter,
    onDanmakuItemLeave,
    onDanmakuLayerEnter,
    onDanmakuLayerLeave,
    onDanmuInputBlur,
    onDanmuInputFocus,
    onEnded,
    onLoadedMetadata,
    onMediaError,
    onProgressPointerDown,
    onProgressPointerMove,
    onProgressPointerUp,
    onProgressKeydown,
    onTimeUpdate,
    onVolumeInput,
    playbackRate,
    playerRootRef,
    playing,
    progressPercent,
    retryLoad,
    reportDanmakuItem,
    seekByPercent,
    selectDanmuColor,
    selectDanmuFontSize,
    selectDanmuMode,
    sendDanmu,
    setAreaPercent,
    setColoredOnly,
    setDensity,
    setEnabled,
    setOpacity,
    setSpeed,
    setTypeFilter,
    settings,
    settingsOpen,
    showProcessingHint,
    speedMenuOpen,
    toggleColorPicker,
    toggleDanmakuLike,
    toggleMute,
    togglePlay,
    toggleSettings,
    videoEl,
    volume,
    resetDanmakuEngine() {
      danmakuEngine.resetPlaybackState()
    },
    suspendForCloseAnimation() {
      const v = videoEl.value
      if (v && !v.paused) {
        try {
          v.pause()
        } catch {
          // 忽略
        }
      }
      danmakuEngine.stop()
    },
  }
}

const props = defineProps({
  src: { type: String, required: true },
  hlsUrl: { type: String, default: '' },
  // 0 NONE / 1 PROCESSING / 2 READY / 3 FAILED
  transcodeStatus: { type: [Number, String], default: 0 },
  articleId: { type: [Number, String], default: null },
  // 打开动效结束后的静帧海报，避免黑屏闪一下
  poster: { type: String, default: '' },
})

const emit = defineEmits(['ended', 'playing', 'report-danmaku'])

const {
  DANMAKU_AREA_OPTIONS,
  DANMAKU_DENSITY_OPTIONS,
  DANMAKU_TYPE_FILTER_OPTIONS,
  colorPickerOpen,
  currentTime,
  danmuColorCode,
  danmuColorHex,
  danmuFontSize,
  danmuFormatCustomized,
  danmuFormatIconColor,
  danmuMode,
  danmuSendDisabled,
  danmuText,
  danmakuLayerStyle,
  danmakuVisibleItems,
  duration,
  formatTime,
  hoveredDanmakuKey,
  isLoggedIn,
  loadError,
  muted,
  onDanmakuItemEnter,
  onDanmakuItemLeave,
  onDanmakuLayerEnter,
  onDanmakuLayerLeave,
  onDanmuInputBlur,
  onDanmuInputFocus,
  onEnded,
  onLoadedMetadata,
  onMediaError,
  onProgressPointerDown,
  onProgressPointerMove,
  onProgressPointerUp,
  onProgressKeydown,
  onTimeUpdate,
  onVolumeInput,
  playbackRate,
  playerRootRef,
  playing,
  progressPercent,
  retryLoad,
  reportDanmakuItem,
  selectDanmuColor,
  selectDanmuFontSize,
  selectDanmuMode,
  sendDanmu,
  setAreaPercent,
  setColoredOnly,
  setDensity,
  setEnabled,
  setOpacity,
  setSpeed,
  setTypeFilter,
  settings,
  settingsOpen,
  showProcessingHint,
  speedMenuOpen,
  toggleColorPicker,
  toggleDanmakuLike,
  toggleMute,
  togglePlay,
  toggleSettings,
  videoEl,
  volume,
  resetDanmakuEngine,
  suspendForCloseAnimation,
} = useArticleDetailVideo(props, emit)

defineExpose({
  resetDanmakuEngine,
  pausePlayback() {
    const v = videoEl.value
    if (v && !v.paused) {
      try {
        v.pause()
      } catch {
        // 忽略
      }
    }
  },
  suspendForCloseAnimation,
})
