const CARD_ORIGIN_KEY = 'forum_feed_card_origin'
const OPEN_FROM_KEY = 'forum_feed_open_from'
const HERO_LAYER_ID = 'forum-feed-close-hero'
const RESTORE_COVER_EVENT = 'forum-feed-restore-cover'
const VISIT_COUNT_EVENT = 'forum-feed-visit-count'

// 小红书 getSafeTransformDistance：取偶数像素，避免亚像素抖动
function evenPx(n) {
  const t = Math.round(Number(n) || 0)
  return t % 2 === 0 ? t : t - 1
}

function rectStyle(el, rect) {
  el.style.left = `${evenPx(rect.left)}px`
  el.style.top = `${evenPx(rect.top)}px`
  el.style.width = `${evenPx(rect.width)}px`
  el.style.height = `${evenPx(rect.height)}px`
}

function safeBgUrl(url) {
  return String(url || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

function prefersReducedMotion() {
  try {
    return !!window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
  } catch {
    return false
  }
}

function resolveDetailDialogEl() {
  return document.querySelector('.article-detail-modal.el-dialog:not(.article-detail-modal--closed)')
}

function resolveDialogInner(dialog) {
  return (
    dialog.querySelector('.red-detail-page--modal') ||
    dialog.querySelector('.red-detail-page') ||
    dialog.querySelector('.el-dialog__body')
  )
}

function waitTransition(el, propertyNames, timeoutMs) {
  return new Promise((resolve) => {
    if (!el) {
      resolve()
      return
    }
    const wanted = new Set(propertyNames)
    let finished = false
    const done = () => {
      if (finished) return
      finished = true
      el.removeEventListener('transitionend', onEnd)
      resolve()
    }
    const onEnd = (event) => {
      if (event.target !== el) return
      if (wanted.size && !wanted.has(event.propertyName)) return
      done()
    }
    el.addEventListener('transitionend', onEnd)
    setTimeout(done, timeoutMs)
  })
}

// 记录首页封面起点
export function captureFeedCardOrigin(articleId, element, extra = {}) {
  if (articleId == null || !element?.getBoundingClientRect) return
  const cover =
    (element.matches?.('.note-cover') && element) ||
    element.querySelector?.('.note-cover') ||
    element
  const rect = cover.getBoundingClientRect()
  const img = cover.querySelector?.('img.note-cover-img, img')
  const coverUrl =
    extra.coverUrl ||
    img?.currentSrc ||
    img?.src ||
    ''
  sessionStorage.setItem(
    CARD_ORIGIN_KEY,
    JSON.stringify({
      id: String(articleId),
      left: rect.left,
      top: rect.top,
      width: rect.width,
      height: rect.height,
      coverUrl,
      restoreCoverUrl: extra.restoreCoverUrl || '',
    }),
  )
}

export function captureFeedOpenFrom(path) {
  sessionStorage.setItem(OPEN_FROM_KEY, path || '/community')
}

export function getFeedReturnPath() {
  const path = sessionStorage.getItem(OPEN_FROM_KEY) || '/community'
  return path.startsWith('/') && !path.startsWith('//') ? path : '/community'
}

export function getFeedCardOrigin(articleId) {
  try {
    const raw = sessionStorage.getItem(CARD_ORIGIN_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (String(data.id) !== String(articleId)) return null
    return data
  } catch {
    return null
  }
}

export function notifyFeedRestoreCover(articleId, restoreCoverUrl = '') {
  if (articleId == null) return
  try {
    window.dispatchEvent(
      new CustomEvent(RESTORE_COVER_EVENT, {
        detail: {
          articleId: String(articleId),
          restoreCoverUrl: String(restoreCoverUrl || ''),
        },
      }),
    )
  } catch {
    // 忽略
  }
}

export function onFeedRestoreCover(handler) {
  const listener = (event) => handler?.(event?.detail)
  window.addEventListener(RESTORE_COVER_EVENT, listener)
  return () => window.removeEventListener(RESTORE_COVER_EVENT, listener)
}

export function notifyFeedVisitCountUpdate(articleId, visitCount) {
  if (articleId == null) return
  try {
    window.dispatchEvent(
      new CustomEvent(VISIT_COUNT_EVENT, {
        detail: {
          articleId: String(articleId),
          visitCount: Math.max(0, Number(visitCount) || 0),
        },
      }),
    )
  } catch {
    // 忽略
  }
}

export function onFeedVisitCountUpdate(handler) {
  const listener = (event) => handler?.(event?.detail)
  window.addEventListener(VISIT_COUNT_EVENT, listener)
  return () => window.removeEventListener(VISIT_COUNT_EVENT, listener)
}

function removeHeroLayer() {
  document.getElementById(HERO_LAYER_ID)?.remove()
}

export function clearFeedNavigationState() {
  sessionStorage.removeItem(CARD_ORIGIN_KEY)
  sessionStorage.removeItem(OPEN_FROM_KEY)
  removeHeroLayer()
}

export function shouldReturnBackToFeed() {
  const path = getFeedReturnPath()
  return path === '/' || path === '/community' || path.startsWith('/search')
}

export function shouldReturnBackToSearch() {
  return getFeedReturnPath().startsWith('/search')
}

// 冻结详情内视频：避免关闭时硬件层穿透/闪黑
function freezeDetailVideos(dialog) {
  if (!dialog) return
  dialog.querySelectorAll('video').forEach((video) => {
    try {
      video.pause()
    } catch {
      // 忽略
    }
    video.style.visibility = 'hidden'
    video.style.opacity = '0'
  })
}

function clearDialogMotionStyles(dialog, overlay, { keepHidden = false } = {}) {
  if (!dialog) return
  const inner = resolveDialogInner(dialog)
  if (inner) {
    inner.style.width = ''
    inner.style.height = ''
    inner.style.minWidth = ''
    inner.style.minHeight = ''
    inner.style.flex = ''
    inner.style.transform = ''
    inner.style.opacity = ''
  }
  const interaction = dialog.querySelector('.info-section')
  if (interaction) {
    interaction.style.removeProperty('width')
    interaction.style.removeProperty('min-width')
    interaction.style.removeProperty('max-width')
    interaction.style.removeProperty('flex')
    interaction.style.opacity = ''
    interaction.style.overflow = ''
    interaction.style.transition = ''
  }
  dialog.classList.remove(
    'article-detail-modal--expanding',
    'article-detail-modal--expand-prep',
    'article-detail-modal--shrinking',
    'article-detail-modal--portrait-shell',
  )
  if (!keepHidden) {
    dialog.classList.remove('article-detail-modal--closed')
  }
  dialog.style.transition = ''
  dialog.style.transform = ''
  dialog.style.transformOrigin = ''
  dialog.style.position = ''
  dialog.style.left = ''
  dialog.style.top = ''
  dialog.style.margin = ''
  dialog.style.marginTop = ''
  dialog.style.width = ''
  dialog.style.height = ''
  dialog.style.maxHeight = ''
  dialog.style.overflow = ''
  dialog.style.pointerEvents = ''
  dialog.style.clipPath = ''
  if (!keepHidden) {
    dialog.style.visibility = ''
    dialog.style.opacity = ''
  }
  if (overlay) {
    overlay.classList.remove('article-detail-overlay--expand-prep')
    if (!keepHidden) {
      overlay.classList.remove('article-detail-overlay--closed')
      overlay.style.opacity = ''
      overlay.style.visibility = ''
    }
    overlay.style.transition = ''
    overlay.style.backgroundColor = ''
    overlay.style.pointerEvents = ''
  }
}

function resolveRightPanel(dialog) {
  return dialog.querySelector('.info-section') || null
}

function pinDialogShell(dialog) {
  dialog.style.position = 'fixed'
  dialog.style.left = '0px'
  dialog.style.top = '0px'
  dialog.style.margin = '0'
  dialog.style.marginTop = '0'
  dialog.style.maxHeight = 'none'
  dialog.style.transformOrigin = 'left top'
  dialog.style.pointerEvents = 'none'
}

function measureNoteGeometry(dialog) {
  const media = dialog.querySelector('.media-section')
  const info = resolveRightPanel(dialog)
  const dialogRect = dialog.getBoundingClientRect()
  const mediaRect = media?.getBoundingClientRect()
  const infoRect = info?.getBoundingClientRect()
  const noteW = evenPx(mediaRect?.width || Math.max(dialogRect.width * 0.55, 280))
  const interW = evenPx(infoRect?.width || Math.max(dialogRect.width - noteW, 300))
  const fullW = evenPx(noteW + interW)
  const gapY = evenPx(dialogRect.top > 0 ? dialogRect.top : 32)
  const centerX = evenPx(Math.max((window.innerWidth - fullW) / 2, 0))
  return { noteW, interW, fullW, gapY, centerX, dialogH: evenPx(dialogRect.height) }
}

// 对齐小红书源码：translate + scale(卡片宽/笔记宽) + width 从 noteW 扩到 noteW+interaction
export function animateDetailDialogFromCard(origin) {
  return new Promise((resolve) => {
    if (!origin || prefersReducedMotion()) {
      resolve()
      return
    }

    let attempts = 0
    const tryRun = () => {
      const dialog = resolveDetailDialogEl()
      if (!dialog) {
        if (attempts++ < 40) {
          requestAnimationFrame(tryRun)
          return
        }
        resolve()
        return
      }

      dialog.style.visibility = 'hidden'
      dialog.style.transition = 'none'
      dialog.style.transform = 'none'
      dialog.style.width = ''
      dialog.style.height = ''
      dialog.style.left = ''
      dialog.style.top = ''
      dialog.style.position = ''
      dialog.style.margin = ''
      dialog.style.opacity = ''
      dialog.classList.remove('article-detail-modal--closed')

      const final = dialog.getBoundingClientRect()
      if (final.width < 40 || final.height < 40) {
        if (attempts++ < 40) {
          requestAnimationFrame(tryRun)
          return
        }
        dialog.style.visibility = ''
        dialog.classList.remove('article-detail-modal--expand-prep')
        resolve()
        return
      }

      const overlay = dialog.closest('.el-overlay')
      if (overlay) {
        overlay.classList.remove('article-detail-overlay--closed')
        overlay.style.opacity = ''
        overlay.style.visibility = ''
      }

      const geo = measureNoteGeometry(dialog)
      const cardW = Math.max(Number(origin.width) || 1, 1)
      const scale = cardW / geo.noteW
      const startX = evenPx(origin.left)
      const startY = evenPx(origin.top)

      dialog.classList.remove('article-detail-modal--expand-prep')
      dialog.classList.add('article-detail-modal--expanding')
      pinDialogShell(dialog)
      dialog.style.height = `${geo.dialogH}px`
      dialog.style.width = `${geo.noteW}px`
      dialog.style.overflow = 'hidden'
      dialog.style.transform = `translate(${startX}px, ${startY}px) scale(${scale})`
      dialog.style.visibility = 'visible'

      if (overlay) {
        overlay.classList.remove('article-detail-overlay--expand-prep')
        overlay.style.transition = 'none'
        overlay.style.backgroundColor = 'transparent'
        overlay.style.pointerEvents = 'none'
      }

      void dialog.offsetWidth

      // 与小红书一致：transform .4s, width .4s（打开不加 height）
      dialog.style.transition = 'transform .4s, width .4s'
      dialog.style.transform = `translate(${geo.centerX}px, ${geo.gapY}px) scale(1)`
      dialog.style.width = `${geo.fullW}px`
      dialog.style.overflow = 'visible'

      if (overlay) {
        overlay.style.transition = 'background-color .4s'
        overlay.style.backgroundColor = ''
      }

      let finished = false
      const finish = () => {
        if (finished) return
        finished = true
        clearDialogMotionStyles(dialog, overlay)
        resolve()
      }

      const onEnd = (event) => {
        if (event.target !== dialog) return
        if (!['transform', 'width'].includes(event.propertyName)) return
        dialog.removeEventListener('transitionend', onEnd)
        finish()
      }
      dialog.addEventListener('transitionend', onEnd)
      setTimeout(finish, 480)
    }

    requestAnimationFrame(tryRun)
  })
}

// 对齐小红书 onExit：先按封面比例钉 height，再 width+transform+height 一并缩回卡片
export function animateDetailDialogToCard(origin, options = {}) {
  return new Promise((resolve) => {
    const dialog = resolveDetailDialogEl()
    if (!dialog || !origin) {
      resolve()
      return
    }

    const articleId = options.articleId ?? origin.id
    const restoreCoverUrl = options.restoreCoverUrl || origin.restoreCoverUrl || ''
    const overlay = dialog.closest('.el-overlay')

    if (prefersReducedMotion()) {
      freezeDetailVideos(dialog)
      notifyFeedRestoreCover(articleId, restoreCoverUrl)
      resolve()
      return
    }

    freezeDetailVideos(dialog)
    removeHeroLayer()

    const info = resolveRightPanel(dialog)
    if (info) {
      info.style.transition = 'opacity .12s ease'
      info.style.opacity = '0'
      info.style.pointerEvents = 'none'
      info.style.overflow = 'hidden'
    }
    dialog.querySelector('.detail-video-player__danmaku-layer')?.style.setProperty('visibility', 'hidden')

    const geo = measureNoteGeometry(dialog)
    const cardW = Math.max(Number(origin.width) || 1, 1)
    const scale = cardW / geo.noteW
    const startX = evenPx(origin.left)
    const startY = evenPx(origin.top)

    dialog.classList.add('article-detail-modal--shrinking')
    pinDialogShell(dialog)
    dialog.style.transition = 'none'
    dialog.style.width = `${geo.fullW}px`
    dialog.style.height = `${geo.dialogH}px`
    dialog.style.transform = `translate(${geo.centerX}px, ${geo.gapY}px) scale(1)`
    dialog.style.overflow = 'hidden'

    if (overlay) {
      overlay.style.pointerEvents = 'none'
    }

    void dialog.offsetWidth

    // 与打开对称：只动 transform + width，避免 height 过渡引发整页重排卡顿
    dialog.style.transition = 'transform .4s, width .4s'
    dialog.style.width = `${geo.noteW}px`
    dialog.style.transform = `translate(${startX}px, ${startY}px) scale(${scale})`

    if (overlay) {
      overlay.style.transition = 'background-color .4s'
      overlay.style.backgroundColor = 'transparent'
    }

    let finished = false
    const finish = () => {
      if (finished) return
      finished = true
      notifyFeedRestoreCover(articleId, restoreCoverUrl)
      dialog.classList.add('article-detail-modal--closed')
      dialog.style.opacity = '0'
      dialog.style.visibility = 'hidden'
      if (overlay) {
        overlay.classList.add('article-detail-overlay--closed')
        overlay.style.opacity = '0'
        overlay.style.visibility = 'hidden'
      }
      clearDialogMotionStyles(dialog, overlay, { keepHidden: true })
      resolve()
    }

    const onEnd = (event) => {
      if (event.target !== dialog) return
      if (!['transform', 'width'].includes(event.propertyName)) return
      dialog.removeEventListener('transitionend', onEnd)
      finish()
    }
    dialog.addEventListener('transitionend', onEnd)
    setTimeout(finish, 460)
  })
}

// 兼容旧调用：不再钉 ghost / morph 层
export function pinFeedOpenGhost() {
  return null
}

export function removeFeedOpenGhost() {}

export function updateFeedOpenGhostImage() {}

export function preloadFeedOpenImage(imageUrl) {
  return new Promise((resolve) => {
    if (!imageUrl) {
      resolve(false)
      return
    }
    const img = new Image()
    img.onload = () => resolve(true)
    img.onerror = () => resolve(false)
    img.src = imageUrl
  })
}

export function captureVideoFirstFrame(videoUrl) {
  return new Promise((resolve) => {
    const url = String(videoUrl || '').trim()
    if (!url) {
      resolve('')
      return
    }
    const video = document.createElement('video')
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      try {
        video.removeAttribute('src')
        video.load()
      } catch {
        // 忽略
      }
      resolve(value || '')
    }
    const timer = setTimeout(() => finish(''), 2800)
    video.muted = true
    video.playsInline = true
    // 只要第一帧，preload='auto' 会把整片拉下来；
    // 而且这里的 crossOrigin 和播放器的 <video> 不一致，两边不共用缓存，等于下两遍
    video.preload = 'metadata'
    video.crossOrigin = 'anonymous'
    video.onerror = () => {
      clearTimeout(timer)
      finish('')
    }
    video.onloadeddata = () => {
      try {
        const t = Math.min(0.08, Math.max(0.01, (Number(video.duration) || 1) * 0.01))
        if (typeof video.fastSeek === 'function') {
          video.fastSeek(t)
        } else {
          video.currentTime = t
        }
      } catch {
        clearTimeout(timer)
        finish('')
      }
    }
    video.onseeked = () => {
      try {
        const w = video.videoWidth || 0
        const h = video.videoHeight || 0
        if (!w || !h) {
          clearTimeout(timer)
          finish('')
          return
        }
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          clearTimeout(timer)
          finish('')
          return
        }
        ctx.drawImage(video, 0, 0, w, h)
        clearTimeout(timer)
        finish(canvas.toDataURL('image/jpeg', 0.86))
      } catch {
        clearTimeout(timer)
        finish('')
      }
    }
    video.src = url
  })
}

export function prepareFeedOpenVideoFirstFrame() {
  return Promise.resolve(false)
}

export function removeFeedOpenMorphLayers() {
  removeHeroLayer()
}
