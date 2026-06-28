import request from './request'

function newRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(16).slice(2)}`
}

export function getLotteryActivities() {
  return request({ url: '/lottery/activities', method: 'get' })
}

/** @param {number|string|null|undefined} [activityId] */
export function getLotteryInfo(activityId) {
  const params = {}
  if (activityId != null && activityId !== '') {
    params.activityId = activityId
  }
  return request({ url: '/lottery/info', method: 'get', params })
}

/** @param {{activityId?: number|string, pageNum?: number, pageSize?: number}} [params] */
export function getLotteryRecords(params = {}) {
  return request({ url: '/lottery/records', method: 'get', params })
}

/** @param {1|10} times @param {number|string|null|undefined} [activityId] @param {string} [requestId] */
export function lotteryDraw(times, activityId, requestId) {
  const data = {
    times,
    requestId: requestId || newRequestId(),
  }
  if (activityId != null && activityId !== '') {
    data.activityId = activityId
  }
  return request({ url: '/lottery/draw', method: 'post', data })
}

/** 抽奖页「点我看看」彩蛋积分（服务端幂等） */
export function claimLotterySurpriseBonus() {
  return request({ url: '/lottery/surprise-bonus', method: 'post' })
}
