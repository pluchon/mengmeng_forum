import request from './request'

export function getAcceptedQuestionAnswer(articleId) {
  return request({
    url: '/articleQuestion/acceptedAnswer',
    method: 'get',
    params: { articleId },
  })
}

export function acceptQuestionAnswer(data) {
  return request({
    url: '/articleQuestion/acceptAnswer',
    method: 'post',
    data,
  })
}

export function closeQuestion(data) {
  return request({
    url: '/articleQuestion/close',
    method: 'post',
    data,
  })
}
