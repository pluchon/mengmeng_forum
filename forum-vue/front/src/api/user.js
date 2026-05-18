import request from './request'

/** 当前登录用户完整信息（需 JWT） */
export function getUserByIdForLogin() {
  return request({
    url: '/user/getUserByIdForLogin',
    method: 'get',
  })
}
