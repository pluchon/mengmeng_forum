import http from '@/utils/http'

export interface MascotModelRow {
  id: string
  code: string
  name: string
  modelRelPath: string
  modelScale: number
  posX: number
  posY: number
  stageWidth: number
  stageHeight: number
  shelfStatus: number
  sortOrder: number
  deleteState: number
  createTime: string
  updateTime: string
}

export function getMascotModelList(params: Record<string, unknown>) {
  return http.get<PageRes<MascotModelRow[]>>('/admin/content/mascot-model/getList', params)
}

export function saveMascotModel(body: Record<string, unknown>) {
  return http.post<number>('/admin/content/mascot-model/save', body)
}

export function setMascotModelShelf(body: { id: string, shelfStatus: number }) {
  return http.post<unknown>('/admin/content/mascot-model/setShelfStatus', body)
}

export function setMascotModelDelete(body: { id: string, deleteState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/mascot-model/setDeleteState', body)
}
