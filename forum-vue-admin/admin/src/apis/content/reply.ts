import http from '@/utils/http'

export interface ReplyRow {
  id: string
  articleId: string
  postUserId: string
  username: string
  nickname: string
  contentPreview: string
  state: number
  deleteState: number
  createTime: string
}

export function getReplyList(params: Record<string, unknown>) {
  return http.get<PageRes<ReplyRow[]>>('/admin/content/reply/getList', params)
}

export function setReplyDeleteState(body: { id: number | string, deleteState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/reply/setDeleteState', {
    id: Number(body.id),
    deleteState: body.deleteState
  })
}

export function setReplyState(body: { id: number | string, state: 0 | 1 }) {
  return http.post<unknown>('/admin/content/reply/setState', {
    id: Number(body.id),
    state: body.state
  })
}
