import { getBaseApi } from '@/apis/base'
import http from '@/utils/http'

export interface ListItem {
  id: string
  createUserString: string
  createTime: string
  disabled: boolean
  deptId: string
  deptName: string
  username: string
  nickname: string
  gender: Gender
  avatar: string
  email: string
  phone: string
  status: Status
  type: 1 | 2
  description: string
  roleIds: string[]
  roleNames: string[]
  forumAdmin?: boolean
  vipTier?: number
  vipExpireAt?: string | null
  deleteState?: number
}

/** 用户模块 */
export const baseAPI = getBaseApi<ListItem>({ baseUrl: '/admin/system/user' })

/** 禁言 / 解禁（user.state） */
export function setUserMute(body: { id: number | string, muted: boolean }) {
  return http.post<unknown>('/admin/system/user/setMute', {
    id: Number(body.id),
    muted: body.muted
  })
}

/** 设置论坛管理员（user.is_admin），仅管理员可调用 */
export function setForumAdmin(body: { id: number | string, isAdmin: 0 | 1 }) {
  return http.post<unknown>('/admin/system/user/setForumAdmin', {
    id: Number(body.id),
    isAdmin: body.isAdmin
  })
}

export function updateUserRemark(body: { id: number | string, remark: string }) {
  return http.post<unknown>('/admin/system/user/updateRemark', {
    id: Number(body.id),
    remark: body.remark
  })
}
