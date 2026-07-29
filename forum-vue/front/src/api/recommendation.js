import request from './request'

// 获取为你推荐分页；游客会返回公开兜底流
export function getRecommendationFeed(params) {
  return request({ url: '/recommend/feed', method: 'get', params })
}

// 获取当前用户的推荐兴趣设置
export function getRecommendationInterests() {
  return request({ url: '/profile/interests', method: 'get' })
}

// 整体保存当前用户的推荐兴趣设置
export function saveRecommendationInterests(data) {
  return request({ url: '/profile/interests', method: 'put', data })
}

// 清空当前用户的兴趣与帖子级反馈
export function resetRecommendationInterests() {
  return request({ url: '/profile/interests/reset', method: 'post' })
}

// 标记当前帖子不感兴趣
export function markRecommendationNotInterested(articleId, reasonCode, reasonDetail) {
  return request({
    url: '/recommend/feedback/not-interested',
    method: 'post',
    data: { articleId, reasonCode, reasonDetail },
  })
}

// 分页查询当前用户设为不感兴趣的帖子
export function getNotInterestedArticles(params) {
  return request({ url: '/recommend/feedback/not-interested', method: 'get', params })
}

// 恢复当前用户对帖子的兴趣
export function restoreRecommendationInterested(articleId) {
  return request({
    url: `/recommend/feedback/not-interested/${articleId}`,
    method: 'delete',
  })
}
