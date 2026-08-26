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

export function getVipPurchaseRecords(params) {
  return request({ url: '/vip/purchase-records', method: 'get', params })
}
