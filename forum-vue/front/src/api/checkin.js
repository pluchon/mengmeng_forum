import request from './request'

// POST 无 body，幂等签到
export function doCheckin() {
  return request({ url: '/checkin/doCheckin', method: 'post' })
}

export function getCheckinInfo() {
  return request({ url: '/checkin/info', method: 'get' })
}

export function getCheckinLog(params) {
  return request({ url: '/checkin/log', method: 'get', params })
}

export function getCheckinRule(params) {
  return request({ url: '/checkin/rule', method: 'get', params })
}

export function getCheckinMonth(params) {
  return request({ url: '/checkin/month', method: 'get', params })
}

// POST 无 body：服务端自动补离今天最近的漏签日
export function makeupCheckin() {
  return request({ url: '/checkin/makeup', method: 'post' })
}
