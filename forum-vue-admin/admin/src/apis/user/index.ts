import type * as T from './type'
import type { ListItem } from '@/apis/system/menu'
import http from '@/utils/http'

export type * from './type'

/** 登录 */
export function login(data: { username: string, password: string }) {
  return http.post<T.Login>('/admin/login', data)
}

/** 退出登录 */
export function logout() {
  return http.post('/admin/logout')
}

/** 获取用户信息 */
export const getUserInfo = () => {
  return http.get<T.UserInfo>('/admin/user/info')
}

/** 获取用户路由信息 */
export const getUserRoutes = () => {
  return http.get<ListItem[]>('/admin/user/routes')
}
