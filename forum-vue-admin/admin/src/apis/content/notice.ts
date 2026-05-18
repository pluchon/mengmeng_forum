import http from '@/utils/http'

export interface NoticeRow {
  id: string
  noticeKind: number
  categoryScope: string
  templateId: string
  sidebarKey: string
  title: string
  subtitle: string
  bodyPreview: string
  contentPreview?: string
  pinTop?: number
  sort: number
  publishState: number
  deleteState: number
  createTime: string
  updateTime: string
}

export interface NoticeDetail extends Omit<NoticeRow, 'bodyPreview' | 'contentPreview'> {
  bodyJson: string
  contentMarkdown: string
}

/** 版规「适用范围」下拉：全站为前端补 0，其余来自接口 */
export interface NoticeCategoryOption {
  id: string
  name: string
}

export function getNoticeList(params: Record<string, unknown>) {
  return http.get<PageRes<NoticeRow[]>>('/admin/content/notice/getList', params)
}

export function getNoticeDetail(params: { id: string }) {
  return http.get<NoticeDetail>('/admin/content/notice/getDetail', params)
}

export function getNoticeCategories() {
  return http.get<NoticeCategoryOption[]>('/admin/content/notice/getCategories')
}

export function saveNotice(body: Record<string, unknown>) {
  return http.post<unknown>('/admin/content/notice/save', body)
}

export function updateNotice(body: Record<string, unknown>) {
  return http.post<unknown>('/admin/content/notice/update', body)
}

export function setNoticeDeleteState(body: { id: number | string, deleteState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/notice/setDeleteState', {
    id: Number(body.id),
    deleteState: body.deleteState
  })
}

export function setNoticePublishState(body: { id: number | string, publishState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/notice/setPublishState', {
    id: Number(body.id),
    publishState: body.publishState
  })
}

export function setNoticePinTop(body: { id: number | string, pinTop: 0 | 1 }) {
  return http.post<unknown>('/admin/content/notice/setPinTop', {
    id: Number(body.id),
    pinTop: body.pinTop
  })
}
