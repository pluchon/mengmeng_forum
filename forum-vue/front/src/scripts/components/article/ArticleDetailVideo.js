import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPause, VideoPlay } from '@element-plus/icons-vue'
import VideoVolumeIcon from '@/components/common/VideoVolumeIcon.vue'
import { sendDanmaku } from '@/api/danmaku'
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

function useArticleDetailVideo(props) {
  const userStore = useUserStore()
  const playerRootRef = ref(null)
  const videoEl = ref(null)
  const playing = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(0.8)
  const muted = ref(false)
  const playbackRate = ref(1)
  const speedMenuOpen = ref(false)
  const danmuText = ref('')
  const danmuColorCode = ref(DANMAKU_DEFAULT_COLOR_CODE)
  const danmuMode = ref(DANMAKU_DEFAULT_MODE)
  const danmuFontSize = ref(DANMAKU_DEFAULT_FONT_SIZE)
  const colorPickerOpen = ref(false)
  const danmuSending = ref(false)
  const danmuComposeTimeMs = ref(null)
  const playingBeforeDanmuCompose = ref(false)
  let rafId = null
  let resizeObserver = null
  let danmuComposeBlurTimer = null

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
    getVideoState: () => ({
      currentTime: currentTime.value,
      playing: playing.value,
      playbackRate: playbackRate.value,
    }),
  })

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

  function updateLayerSize() {
    const el = playerRootRef.value
    if (!el) return
    danmakuEngine.setLayerSize(el.clientWidth, el.clientHeight)
  }

  function onLoadedMetadata(e) {
    const v = e?.target || videoEl.value
    if (!v) return
    duration.value = Number(v.duration) || 0
    v.volume = volume.value
    v.playbackRate = playbackRate.value
    v.play().catch(() => {})
    playing.value = !v.paused
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

  function onProgressClick(e) {
    const rail = e?.currentTarget
    if (!rail) return
    const rect = rail.getBoundingClientRect()
    if (!rect.width) return
    const percent = ((e.clientX - rect.left) / rect.width) * 100
    seekByPercent(percent)
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

  watch(() => props.src, () => {
    currentTime.value = 0
    duration.value = 0
    playing.value = false
    danmuText.value = ''
    colorPickerOpen.value = false
    clearDanmuComposeSession(false)
    danmakuEngine.resetPlaybackState()
  })

  watch(() => props.articleId, () => {
    danmakuEngine.resetPlaybackState()
  })

  onMounted(() => {
    document.addEventListener('click', onDocumentClick)
    danmakuEngine.start()
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => updateLayerSize())
      nextTick(() => {
        if (playerRootRef.value) resizeObserver.observe(playerRootRef.value)
      })
    }
  })

  onUnmounted(() => {
    stopProgressLoop()
    danmakuEngine.stop()
    clearDanmuComposeSession(false)
    document.removeEventListener('click', onDocumentClick)
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
    muted,
    onDanmuInputBlur,
    onDanmuInputFocus,
    onEnded,
    onLoadedMetadata,
    onProgressClick,
    onTimeUpdate,
    onVolumeInput,
    playbackRate,
    playerRootRef,
    playing,
    progressPercent,
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
    speedMenuOpen,
    toggleColorPicker,
    toggleMute,
    togglePlay,
    toggleSettings,
    videoEl,
    volume,
  }
}

const props = defineProps({
  src: { type: String, required: true },
  articleId: { type: [Number, String], default: null },
})

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
  muted,
  onDanmuInputBlur,
  onDanmuInputFocus,
  onEnded,
  onLoadedMetadata,
  onProgressClick,
  onTimeUpdate,
  onVolumeInput,
  playbackRate,
  playerRootRef,
  playing,
  progressPercent,
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
  speedMenuOpen,
  toggleColorPicker,
  toggleMute,
  togglePlay,
  toggleSettings,
  videoEl,
  volume,
} = useArticleDetailVideo(props)
