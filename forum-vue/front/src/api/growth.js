import request from './request'

export const getGrowthOverview = () => request({ url: '/growth/overview', method: 'get' })
export const startGrowthChallenge = challengeCode => request({ url: `/growth/challenges/${challengeCode}/start`, method: 'post' })
export const submitGrowthChallenge = (challengeCode, data) => request({ url: `/growth/challenges/${challengeCode}/submit`, method: 'post', data })
