import request from './request'

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
