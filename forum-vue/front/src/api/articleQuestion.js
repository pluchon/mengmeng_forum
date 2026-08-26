import request from './request'

// 采纳一条回答 一级 replyId 或楼中楼 subReplyId，可多条，不联动已解决
export function acceptQuestionAnswer(data) {
  return request({
    url: '/articleQuestion/acceptAnswer',
    method: 'post',
    data,
  })
}

// 作者切换已解决 / 未解决
export function setQuestionResolved(data) {
  return request({
    url: '/articleQuestion/setResolved',
    method: 'post',
    data,
  })
}
