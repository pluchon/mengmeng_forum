import request from './request'

/** POST 无 body，幂等签到 */
export function doCheckin() {
  return request({ url: '/checkin/doCheckin', method: 'post' })
}

export function getCheckinInfo() {
  return request({ url: '/checkin/info', method: 'get' })
}

export function getCheckinLog(params) {
  return request({ url: '/checkin/log', method: 'get', params })
}

/** @param {number} [month] 1~12，不传为当月 */
export function getCheckinRule(params) {
  return request({ url: '/checkin/rule', method: 'get', params })
}
