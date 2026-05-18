import request from './request'

// 获取一级回复列表（分页）
export function getReplyList(params) {
  return request({ url: '/articleReply/getArticleReplyByArticleIdWithPage', method: 'get', params })
}

// 发表一级回复
export function submitReply(data) {
  return request({ url: '/articleReply/replyArticle', method: 'put', data })
}

// 获取楼中楼子回复列表
export function getSubReplyList(params) {
  return request({ url: '/articleSubReply/getSubReplyByReplyId', method: 'get', params })
}

// 发表楼中楼子回复
export function submitSubReply(data) {
  return request({ url: '/articleSubReply/subReply', method: 'put', data })
}
