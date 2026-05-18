import request from './request'

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

/** @param {1|10} times @param {number|string|null|undefined} [activityId] */
export function lotteryDraw(times, activityId) {
  const data = { times }
  if (activityId != null && activityId !== '') {
    data.activityId = activityId
  }
  return request({ url: '/lottery/draw', method: 'post', data })
}

/** 抽奖页「点我看看」彩蛋积分（服务端幂等） */
export function claimLotterySurpriseBonus() {
  return request({ url: '/lottery/surprise-bonus', method: 'post' })
}
