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
  // 拖动期间滑块跟手指走，不跟视频真实位置走
  const dragPercent = ref(0)
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
  // 换源（HLS 降级 / 重试 / 转码完成）前记下位置与播放状态，新源就绪后还原
  let pendingResume = null

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
    // 拖动时不能用视频真实位置：seek 要等一会儿才生效，
    // 期间滑块会在"手指位置"和"视频旧位置"之间来回跳，这就是拖起来卡的手感
    if (progressDragging.value) return dragPercent.value
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

  // shouldPlayOverride 传 true 表示"用户主动要求继续"，忽略当前的暂停态
  function captureResumePoint(shouldPlayOverride = null) {
    const v = videoEl.value
    const t = Number(currentTime.value) || 0
    pendingResume = {
      timeSec: t > 0.5 ? t : 0,
      shouldPlay: shouldPlayOverride != null
        ? shouldPlayOverride
        : (v ? !v.paused : playing.value),
    }
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
    captureResumePoint()
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
      captureResumePoint()
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
    // 从断点续，不要让用户重看一遍；点了重试就是想接着看，强制播放
    captureResumePoint(true)
    preferMp4Fallback.value = hadHlsPrefer && !preferMp4Fallback.value
      ? true
      : false
    loadError.value = false
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
    const resume = pendingResume
    pendingResume = null
    if (resume && resume.timeSec > 0 && Number.isFinite(v.duration) && resume.timeSec < v.duration) {
      try {
        v.currentTime = resume.timeSec
        currentTime.value = resume.timeSec
      } catch {
        // 拿不到就从头播，不值得为此报错
      }
    }
    // 换源前是暂停的就保持暂停：用户明明按了暂停，不该被一次降级弄成自动播放
    if (!resume || resume.shouldPlay) {
      v.play().catch(() => {
        // 浏览器可能拦截带声音的自动播放，交给用户手动点
        playing.value = !v.paused
      })
    }
    // 只清屏上正在飞的弹幕，已下载的分片留着 —— 同一个帖子的弹幕不会变
    danmakuEngine.clearActiveItems()
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
    duration.value = Number(v.duration) || duration.value
    if (progressDragging.value) return
    currentTime.value = Number(v.currentTime) || 0
  }

  function onEnded() {
    const v = videoEl.value
    if (!v) return
    v.currentTime = 0
    currentTime.value = 0
    danmakuEngine.onReplay()
    v.play().catch(() => {})
  }

  // playing 与进度循环统一由 video 的 play / pause 事件驱动，
  // 避免"以为在播其实没播"这类状态漂移
  function onMediaPlay() {
    playing.value = true
    startProgressLoop()
    emit('playing')
  }

  function onMediaPause() {
    playing.value = false
    stopProgressLoop()
  }

  function togglePlay() {
    const v = videoEl.value
    if (!v) return
    if (v.paused) {
      v.play().catch(() => {})
    } else {
      v.pause()
    }
  }

  function seekByPercent(percent) {
    const v = videoEl.value
    if (!v || !duration.value) return
    const p = Math.min(100, Math.max(0, Number(percent) || 0))
    v.currentTime = (p / 100) * duration.value
    currentTime.value = v.currentTime
  }

  function percentFromPointerEvent(e) {
    const rail = e?.currentTarget
    if (!rail) return null
    const rect = rail.getBoundingClientRect()
    if (!rect.width) return null
    return Math.min(100, Math.max(0, ((e.clientX - rect.left) / rect.width) * 100))
  }

  function onProgressPointerDown(e) {
    if (e?.button != null && e.button !== 0) return
    const percent = percentFromPointerEvent(e)
    if (percent == null) return
    progressDragging.value = true
    dragPercent.value = percent
    e.currentTarget?.setPointerCapture?.(e.pointerId)
  }

  // 拖动过程中只挪滑块，不碰 video.currentTime：
  // 每次 seek 都要冲掉解码缓冲并重发 Range 请求，按 pointermove 的频率来必然卡
  function onProgressPointerMove(e) {
    if (!progressDragging.value) return
    const percent = percentFromPointerEvent(e)
    if (percent == null) return
    dragPercent.value = percent
  }

  // 松手才真正跳一次。单击（按下即抬起）走的也是这条，行为不变
  function onProgressPointerUp(e) {
    if (!progressDragging.value) return
    const percent = percentFromPointerEvent(e)
    if (percent != null) dragPercent.value = percent
    progressDragging.value = false
    e.currentTarget?.releasePointerCapture?.(e.pointerId)
    seekByPercent(dragPercent.value)
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

  // 弹幕层已改为点击穿透（否则整块画面都点不到 video），
  // 悬停暂停只能挂在单条弹幕上——这也更接近常见播放器的行为
  function onDanmakuItemEnter(item) {
    hoveredDanmakuKey.value = item?.key || null
    danmakuScrollPaused.value = true
  }

  function onDanmakuItemLeave() {
    hoveredDanmakuKey.value = null
    danmakuScrollPaused.value = false
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
    } catch {
      danmakuEngine.updateDanmakuLike(danmakuId, wasLiked)
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
    } catch {
      // 拦截器已弹出真实原因，这里不再重复提示
    } finally {
      danmuSending.value = false
    }
  }

  function startProgressLoop() {
    stopProgressLoop()
    const tick = () => {
      const v = videoEl.value
      // 拖动时不能用视频真实位置盖掉滑块位置
      if (v && !progressDragging.value) currentTime.value = Number(v.currentTime) || 0
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
      // 转码完成会把 transcodeStatus 从 1 切到 2，这里要续播，
      // 否则用户看到一半会被自己的转码任务弹回片头
      captureResumePoint()
      preferMp4Fallback.value = false
      loadError.value = false
      duration.value = 0
      playing.value = false
      danmuText.value = ''
      colorPickerOpen.value = false
      clearDanmuComposeSession(false)
      // 只是换了播放源，帖子没变，弹幕不用重下
      danmakuEngine.clearActiveItems()
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
    const v = videoEl.value
    if (v) {
      try {
        v.pause()
        v.removeAttribute('src')
        v.load()
      } catch {
        // 忽略
      }
    }
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
    onDanmuInputBlur,
    onDanmuInputFocus,
    onEnded,
    onLoadedMetadata,
    onMediaError,
    onMediaPause,
    onMediaPlay,
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
  onDanmuInputBlur,
  onDanmuInputFocus,
  onEnded,
  onLoadedMetadata,
  onMediaError,
  onMediaPause,
  onMediaPlay,
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
