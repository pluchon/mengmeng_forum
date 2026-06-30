import request from './request'

// 扔漂流瓶
export function createDriftBottle(data) {
  return request({ url: '/drift-bottle/create', method: 'post', data })
}

// 随机捞漂流瓶
export function pickDriftBottle() {
  return request({ url: '/drift-bottle/pick', method: 'get' })
}

// 查询漂流瓶详情
export function getDriftBottleDetail(bottleId) {
  return request({ url: `/drift-bottle/${bottleId}`, method: 'get' })
}

// 评论漂流瓶
export function commentDriftBottle(bottleId, data) {
  return request({ url: `/drift-bottle/${bottleId}/comment`, method: 'post', data })
}

// 查询我的漂流瓶
export function getMyDriftBottles(params) {
  return request({ url: '/drift-bottle/mine', method: 'get', params })
}

// 删除自己的漂流瓶
export function deleteDriftBottle(bottleId) {
  return request({ url: `/drift-bottle/${bottleId}`, method: 'delete' })
}

// 举报漂流瓶
export function reportDriftBottle(bottleId, data) {
  return request({ url: `/drift-bottle/${bottleId}/report`, method: 'post', data })
}

// 举报漂流瓶评论
export function reportDriftBottleComment(commentId, data) {
  return request({ url: `/drift-bottle/comments/${commentId}/report`, method: 'post', data })
}

// 查询今日额度
export function getDriftBottleQuota() {
  return request({ url: '/drift-bottle/quota', method: 'get' })
}
