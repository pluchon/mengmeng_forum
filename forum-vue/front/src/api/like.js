import request from './request'

// 点赞帖子
export function likeArticle(articleId) {
  return request({ url: '/like/likeArticle', method: 'put', params: { articleId } })
}

// 取消点赞帖子
export function unlikeArticle(articleId) {
  return request({ url: '/like/unlikeArticle', method: 'put', params: { articleId } })
}

// 获取我的点赞列表（分页）
export function getMyLikeList(params) {
  return request({ url: '/like/queryArticleListForLikeWithPage', method: 'get', params })
}
