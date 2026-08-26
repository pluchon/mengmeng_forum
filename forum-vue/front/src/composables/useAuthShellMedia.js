import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import posterFallbackUrl from '@/assets/images/login&register&find_password.png'

// 海报 / 标题资源超时后强制进入兜底，避免慢网无限卡住
const AUTH_MEDIA_TIMEOUT_MS = 12000
// 自上而下渐显时长，需与 CSS 保持一致
const AUTH_REVEAL_MS = 720

// 认证页壳层媒体：加载进度条、海报兜底、就绪后自上而下渐显。 posterUrl: OSS 海报 webp 地址
export function useAuthShellMedia(posterUrl) {
  const posterSrc = ref(posterUrl)
  const posterImgRef = ref(null)
  const progress = ref(8)
  // loading | revealing | ready
  const shellPhase = ref('loading')

  let posterSettled = false
  let titleSettled = false
  let finished = false
  let progressTimer = null
  let timeoutTimer = null
  let revealTimer = null

  const isShellLoading = computed(() => shellPhase.value === 'loading')

  const shellLayoutClass = computed(() => ({
    'auth-layout--pending': shellPhase.value === 'loading',
    'auth-layout--revealing': shellPhase.value === 'revealing',
    'auth-layout--ready': shellPhase.value === 'ready',
  }))

  function clearTimers() {
    if (progressTimer != null) {
      window.clearInterval(progressTimer)
      progressTimer = null
    }
    if (timeoutTimer != null) {
      window.clearTimeout(timeoutTimer)
      timeoutTimer = null
    }
    if (revealTimer != null) {
      window.clearTimeout(revealTimer)
      revealTimer = null
    }
  }

  function applyPosterFallback() {
    if (posterSrc.value === posterFallbackUrl) {
      posterSettled = true
      tryFinish()
      return
    }
    posterSrc.value = posterFallbackUrl
  }

  function tryFinish() {
    if (finished || !posterSettled || !titleSettled) return
    finished = true
    clearTimers()
    progress.value = 100
    shellPhase.value = 'revealing'
    revealTimer = window.setTimeout(() => {
      shellPhase.value = 'ready'
      revealTimer = null
    }, AUTH_REVEAL_MS)
  }

  function onPosterLoad() {
    posterSettled = true
    tryFinish()
  }

  function onPosterError() {
    applyPosterFallback()
  }

  // 标题图成功或已切到文字兜底时由 AuthBrandTitle 触发
  function onTitleReady() {
    titleSettled = true
    tryFinish()
  }

  function inspectPosterCache() {
    const el = posterImgRef.value
    if (!el || !el.complete) return
    if (el.naturalWidth > 0) {
      onPosterLoad()
    } else {
      onPosterError()
    }
  }

  onMounted(async () => {
    progressTimer = window.setInterval(() => {
      if (progress.value >= 90) return
      const remain = 90 - progress.value
      progress.value = Math.min(90, progress.value + Math.max(0.8, remain * 0.05))
    }, 120)

    timeoutTimer = window.setTimeout(() => {
      if (!posterSettled) {
        applyPosterFallback()
        if (!posterSettled) {
          posterSettled = true
        }
      }
      tryFinish()
    }, AUTH_MEDIA_TIMEOUT_MS)

    await nextTick()
    inspectPosterCache()
  })

  onUnmounted(() => {
    clearTimers()
  })

  return {
    posterSrc,
    posterImgRef,
    progress,
    shellPhase,
    isShellLoading,
    shellLayoutClass,
    onPosterLoad,
    onPosterError,
    onTitleReady,
  }
}
