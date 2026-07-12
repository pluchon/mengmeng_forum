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
export function markRecommendationNotInterested(articleId) {
  return request({
    url: '/recommend/feedback/not-interested',
    method: 'post',
    data: { articleId },
  })
}
