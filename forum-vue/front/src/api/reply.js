import request from './request'

// 获取一级回复列表 分页
export function getReplyList(params) {
  return request({
    url: '/articleReply/getArticleReplyByArticleIdWithPage',
    method: 'get',
    params,
    publicAnonymousFallback: true,
  })
}

// 发表一级回复
export function submitReply(data) {
  return request({ url: '/articleReply/replyArticle', method: 'put', data })
}

// 获取楼中楼子回复列表
export function getSubReplyList(params) {
  return request({
    url: '/articleSubReply/getSubReplyByReplyId',
    method: 'get',
    params,
    publicAnonymousFallback: true,
  })
}

// 发表楼中楼子回复
export function submitSubReply(data) {
  return request({ url: '/articleSubReply/subReply', method: 'put', data })
}

export function likeReply(replyId) {
  return request({ url: '/replyLike/likeReply', method: 'put', params: { replyId } })
}

export function unlikeReply(replyId) {
  return request({ url: '/replyLike/unlikeReply', method: 'put', params: { replyId } })
}

export function likeSubReply(subReplyId) {
  return request({ url: '/replyLike/likeSubReply', method: 'put', params: { subReplyId } })
}

export function unlikeSubReply(subReplyId) {
  return request({ url: '/replyLike/unlikeSubReply', method: 'put', params: { subReplyId } })
}
