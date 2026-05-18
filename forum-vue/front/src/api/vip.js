import request from './request'

export function getVipStatus() {
  return request({ url: '/vip/status', method: 'get' })
}

export function getVipCenter() {
  return request({ url: '/vip/center', method: 'get' })
}

export function getVipQuota() {
  return request({ url: '/vip/quota', method: 'get' })
}

/** @param {{ tier: 1 | 2 }} data */
export function vipSubscribe(data) {
  return request({ url: '/vip/subscribe', method: 'post', data })
}
