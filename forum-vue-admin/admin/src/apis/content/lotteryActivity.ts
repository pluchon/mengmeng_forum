import http from '@/utils/http'

export interface LotteryActivityRow {
  id: string
  title: string
  coverImageUrl: string | null
  publisherId: string | null
  costPointsPerDraw: number
  status: number
  phase: number
  deleteState: number
  createTime: string
  updateTime: string
}

export interface LotteryPrizeLine {
  activityPrizeId?: string | null
  prizeId?: string | null
  name: string
  prizeType: number
  prizeValue: number
  weight: number
  stockRemaining: number
  isJackpot: number
  imagePath?: string | null
  isMysteryBundle?: number
  catalogStatus?: number
}

export interface LotteryActivityDetail {
  id: string
  title: string
  description: string | null
  coverImageUrl: string | null
  publisherId: string | null
  costPointsPerDraw: number
  status: number
  phase: number
  deleteState: number
  startTime: string | null
  endTime: string | null
  createTime: string
  updateTime: string
  prizeLines: LotteryPrizeLine[]
}

export interface LotteryWinRow {
  id: string
  userId: string
  nickname: string
  prizeName: string
  prizeType: number
  prizeValue: number
  grantPoints: number
  isJackpot: number
  createTime: string
}

export interface LotteryDrawUserRow {
  userId: string
  nickname: string
  avatarUrl: string | null
  vipTier: number
  vipExpireAt: string | null
  drawCount: number
  lastDrawTime: string
}

export function getLotteryActivityList(params: Record<string, unknown>) {
  return http.get<PageRes<LotteryActivityRow[]>>('/admin/content/lottery-activity/getList', params)
}

export function getLotteryActivityDetail(params: { id: string }) {
  return http.get<LotteryActivityDetail>('/admin/content/lottery-activity/detail', params)
}

export function getLotteryWinList(params: Record<string, unknown>) {
  return http.get<PageRes<LotteryWinRow[]>>('/admin/content/lottery-activity/wins', params)
}

export function getLotteryDrawUserList(params: Record<string, unknown>) {
  return http.get<PageRes<LotteryDrawUserRow[]>>('/admin/content/lottery-activity/drawUsers', params)
}

export function saveLotteryActivity(body: Record<string, unknown>) {
  return http.post<number>('/admin/content/lottery-activity/save', body)
}

export function setLotteryActivityDeleteState(body: { id: string, deleteState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/lottery-activity/setDeleteState', body)
}

export function updateLotteryActivityMeta(body: Record<string, unknown>) {
  return http.post<unknown>('/admin/content/lottery-activity/updateMeta', body)
}

export function patchLotteryActivityPhase(body: { id: string, phase?: number, status?: number }) {
  return http.post<unknown>('/admin/content/lottery-activity/patchPhase', body)
}
