const CARD_ORIGIN_KEY = 'forum_feed_card_origin'
const OPEN_FROM_KEY = 'forum_feed_open_from'

export function captureFeedCardOrigin(articleId, element) {
  if (articleId == null || !element?.getBoundingClientRect) return
  const rect = element.getBoundingClientRect()
  sessionStorage.setItem(
    CARD_ORIGIN_KEY,
    JSON.stringify({
      id: String(articleId),
      left: rect.left,
      top: rect.top,
      width: rect.width,
      height: rect.height,
    }),
  )
}

export function captureFeedOpenFrom(path) {
  sessionStorage.setItem(OPEN_FROM_KEY, path || '/')
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

export function clearFeedNavigationState() {
  sessionStorage.removeItem(CARD_ORIGIN_KEY)
  sessionStorage.removeItem(OPEN_FROM_KEY)
}

export function shouldReturnBackToFeed() {
  return sessionStorage.getItem(OPEN_FROM_KEY) === '/'
}

/** 详情弹窗等比缩小并渐隐，落回首页卡片中心 */
export function animateDetailDialogToCard(origin) {
  return new Promise((resolve) => {
    const dialog = document.querySelector('.article-detail-modal.el-dialog')
    if (!dialog || !origin) {
      resolve()
      return
    }

    const from = dialog.getBoundingClientRect()
    const targetCx = origin.left + origin.width / 2
    const targetCy = origin.top + origin.height / 2
    const fromCx = from.left + from.width / 2
    const fromCy = from.top + from.height / 2
    const dx = targetCx - fromCx
    const dy = targetCy - fromCy

    // 等比缩成小点，避免宽高分别缩放造成的「变窄」感
    const endScale = 0.035
    const finalTransform = `translate(${dx}px, ${dy}px) scale(${endScale})`
    const overlay = dialog.closest('.el-overlay')

    dialog.classList.add('article-detail-modal--shrinking')
    dialog.style.transform = ''
    dialog.style.opacity = ''
    dialog.style.pointerEvents = 'none'
    if (overlay) {
      overlay.style.pointerEvents = 'none'
    }

    const animOptions = { duration: 400, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', fill: 'forwards' }

    const dialogAnim = dialog.animate(
      [
        { transform: 'translate(0, 0) scale(1)', opacity: 1 },
        { transform: finalTransform, opacity: 0 },
      ],
      animOptions,
    )

    const overlayAnim = overlay?.animate(
      [{ opacity: 1 }, { opacity: 0 }],
      animOptions,
    )

    let finished = false
    const freezeEndState = () => {
      dialog.classList.add('article-detail-modal--closed')
      dialog.style.transform = finalTransform
      dialog.style.opacity = '0'
      if (overlay) {
        overlay.classList.add('article-detail-overlay--closed')
        overlay.style.opacity = '0'
      }
    }

    const finish = () => {
      if (finished) return
      finished = true
      freezeEndState()
      resolve()
    }

    dialogAnim.onfinish = finish
    overlayAnim && (overlayAnim.onfinish = finish)
    setTimeout(finish, 460)
  })
}
