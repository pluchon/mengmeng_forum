import request from './request'

export function getPointsWallet() {
  return request({ url: '/points/wallet', method: 'get' })
}

export function getMengCoinCenterOverview(params) {
  return request({ url: '/points/center/overview', method: 'get', params })
}

export function getMengCoinCenterLog(params) {
  return request({ url: '/points/center/log', method: 'get', params })
}

export function getMengCoinCenterTrend(params) {
  return request({ url: '/points/center/trend', method: 'get', params })
}

export function getMengCoinCenterChart(params) {
  return request({ url: '/points/center/chart', method: 'get', params })
}

export function claimMengCoinMilestone(milestoneCode) {
  return request({ url: `/points/center/milestones/${encodeURIComponent(milestoneCode)}/claim`, method: 'post' })
}
