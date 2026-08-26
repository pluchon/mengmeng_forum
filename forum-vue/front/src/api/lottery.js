import request from './request'

function newRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(16).slice(2)}`
}

export function getLotteryActivities(params = {}) {
  return request({ url: '/lottery/activities', method: 'get', params })
}

export function getLotteryInfo(activityId) {
  const params = {}
  if (activityId != null && activityId !== '') {
    params.activityId = activityId
  }
  return request({ url: '/lottery/info', method: 'get', params })
}

export function getLotteryRecords(params = {}) {
  return request({ url: '/lottery/records', method: 'get', params })
}

export function getLotteryRecentPublic(params = {}) {
  return request({ url: '/lottery/recent-public', method: 'get', params })
}

export function claimLotteryTask(data) {
  return request({ url: '/lottery/tasks/claim', method: 'post', data })
}

export function claimLotteryCollectMilestone(data) {
  return request({ url: '/lottery/collect/claim', method: 'post', data })
}

export function lotteryDraw(times, activityId, requestId, useVoucher = true) {
  const data = {
    times,
    requestId: requestId || newRequestId(),
    useVoucher,
  }
  if (activityId != null && activityId !== '') {
    data.activityId = activityId
  }
  return request({ url: '/lottery/draw', method: 'post', data })
}
