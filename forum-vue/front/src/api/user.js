import request from './request'

/** 查询指定用户是否在线（WebSocket） */
export function getUserIsOnline(userId) {
  return request({
    url: '/user/isOnline',
    method: 'get',
    params: { userId },
  })
}

/** 当前登录用户完整信息（需 JWT） */
export function getUserByIdForLogin() {
  return request({
    url: '/user/getUserByIdForLogin',
    method: 'get',
  })
}

/** 当前账号退出登录，后端会让当前 JWT 立即失效 */
export function logoutCurrentUser() {
  return request({
    url: '/user/logout',
    method: 'post',
  })
}
