import http from '@/utils/http'

export interface LotteryPrizeOption {
  id: string
  name: string
  prizeType: number
  prizeValue: number
  isMysteryBundle: number
}

export interface LotteryPrizeCatalogRow {
  id: string
  name: string
  prizeType: number
  prizeValue: number
  stockQuantity: number
  catalogStatus: number
  isMysteryBundle: number
  imagePath: string | null
  deleteState: number
  createTime: string
  updateTime: string
}

export interface LotteryPrizeMysteryItem {
  id: string
  itemType: number
  itemValue: number
  weight: number
}

export interface LotteryPrizeCatalogDetail {
  id: string
  name: string
  prizeType: number
  prizeValue: number
  stockQuantity: number
  catalogStatus: number
  isMysteryBundle: number
  imagePath: string | null
  deleteState: number
  createTime: string
  updateTime: string
  mysteryItems: LotteryPrizeMysteryItem[]
}

export function getLotteryPrizeOptionsOnShelf() {
  return http.get<LotteryPrizeOption[]>('/admin/content/lottery-prize/optionsOnShelf')
}

export function getLotteryPrizeList(params: Record<string, unknown>) {
  return http.get<PageRes<LotteryPrizeCatalogRow[]>>('/admin/content/lottery-prize/getList', params)
}

export function getLotteryPrizeDetail(params: { id: string }) {
  return http.get<LotteryPrizeCatalogDetail>('/admin/content/lottery-prize/detail', params)
}

export function saveLotteryPrize(body: Record<string, unknown>) {
  return http.post<number>('/admin/content/lottery-prize/save', body)
}

export function setLotteryPrizeDeleteState(body: { id: string, deleteState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/lottery-prize/setDeleteState', body)
}

export function setLotteryPrizeCatalogStatus(body: { id: string, catalogStatus: number }) {
  return http.post<unknown>('/admin/content/lottery-prize/setCatalogStatus', body)
}
