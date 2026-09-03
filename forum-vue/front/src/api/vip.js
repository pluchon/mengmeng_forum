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

// 下单只传档位与渠道，金额一律由后端定价
export function createVipOrder(data) {
  return request({ url: '/vip/order/create', method: 'post', data })
}

export function queryVipOrder(params) {
  return request({ url: '/vip/order/query', method: 'get', params })
}

// 本地模拟支付：后端按真实回调的形状自签一份回调，走同一条发货链路
export function mockPayVipOrder(data) {
  return request({ url: '/vip/order/mock-pay', method: 'post', data })
}
