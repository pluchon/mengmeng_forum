const SCROLL_KEY = 'forum_feed_scroll_y'

/** 首页信息流滚动容器 */
export function getFeedScrollEl() {
  return (
    document.querySelector('.shell-main-outlet .shell-page-scroll')
    || document.querySelector('.home-xhs-main')
    || document.documentElement
  )
}

export function captureFeedScroll() {
  const el = getFeedScrollEl()
  const y = el === document.documentElement ? window.scrollY : el.scrollTop
  sessionStorage.setItem(SCROLL_KEY, String(Math.max(0, y)))
}

export function restoreFeedScroll() {
  const raw = sessionStorage.getItem(SCROLL_KEY)
  sessionStorage.removeItem(SCROLL_KEY)
  if (raw == null) return
  const y = Number(raw)
  if (!Number.isFinite(y)) return
  requestAnimationFrame(() => {
    const el = getFeedScrollEl()
    if (el === document.documentElement) {
      window.scrollTo({ top: y, behavior: 'auto' })
    } else {
      el.scrollTop = y
    }
  })
}
