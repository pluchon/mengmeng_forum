import request from './request'

export function getPointsWallet() {
  return request({ url: '/points/wallet', method: 'get' })
}

export function getPointsLog(params) {
  return request({ url: '/points/log', method: 'get', params })
}

export function getPointsDaily(params) {
  return request({ url: '/points/daily', method: 'get', params })
}
