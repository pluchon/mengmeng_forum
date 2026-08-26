import request from './request'

export function followUser(followeeId) {
  return request({ url: '/user/followUser', method: 'put', params: { followeeId } })
}

export function unfollowUser(followeeId) {
  return request({ url: '/user/unfollowUser', method: 'put', params: { followeeId } })
}

export function getFollowStats(userId) {
  return request({ url: '/user/followStats', method: 'get', params: { userId } })
}

export function getCreatorMonthlyNewFollowers() {
  return request({ url: '/user/creator/monthly-new-followers', method: 'get' })
}

export function getMyFollowingIds() {
  return request({ url: '/user/followingIds', method: 'get' })
}

export function getFollowingList(params) {
  return request({ url: '/user/followingList', method: 'get', params })
}

export function getFollowerList(params) {
  return request({ url: '/user/followerList', method: 'get', params })
}
