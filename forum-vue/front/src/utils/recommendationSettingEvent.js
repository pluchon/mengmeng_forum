// 个性化推荐设置变更后通知首页「为你推荐」立刻重拉
export const RECOMMENDATION_SETTING_CHANGED_EVENT = 'forum:recommendation-setting-changed'

export function notifyRecommendationSettingChanged(payload = {}) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(RECOMMENDATION_SETTING_CHANGED_EVENT, {
    detail: {
      personalizedEnabled: payload.personalizedEnabled !== false,
      interestBoardIds: Array.isArray(payload.interestBoardIds) ? payload.interestBoardIds : [],
    },
  }))
}

export function onRecommendationSettingChanged(handler) {
  if (typeof window === 'undefined') return () => {}
  const listener = (event) => handler(event?.detail || {})
  window.addEventListener(RECOMMENDATION_SETTING_CHANGED_EVENT, listener)
  return () => window.removeEventListener(RECOMMENDATION_SETTING_CHANGED_EVENT, listener)
}
