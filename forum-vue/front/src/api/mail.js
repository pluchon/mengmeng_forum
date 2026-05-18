import request from './request'

export function sendMailCode(email) {
  return request({
    url: '/mail/send',
    method: 'post',
    params: { email }
  })
}