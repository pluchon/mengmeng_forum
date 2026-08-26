import request from './request'

// 获取为你推荐分页；需登录
export function getRecommendationFeed(params) {
  return request({ url: '/recommend/feed', method: 'get', params })
}

// 获取当前用户的个性化推荐开关
export function getRecommendationSetting() {
  return request({ url: '/recommend/setting', method: 'get' })
}

// 更新当前用户的个性化推荐开关与兴趣版块
export function updateRecommendationSetting(personalizedEnabled, interestBoardIds) {
  const data = { personalizedEnabled: Boolean(personalizedEnabled) }
  if (interestBoardIds !== undefined) {
    data.interestBoardIds = Array.isArray(interestBoardIds) ? interestBoardIds : []
  }
  return request({
    url: '/recommend/setting',
    method: 'put',
    data,
  })
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
