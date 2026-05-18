export const PHASE_OPTIONS = [
  { label: '筹划中', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已截止', value: 2 },
]

export const STATUS_OPTIONS = [
  { label: '关闭', value: 0 },
  { label: '开放', value: 1 },
]

export type ActivityFilterKey = 'all' | 'phase_0' | 'phase_1' | 'phase_2' | 'deleted'

export const ACTIVITY_FILTER_OPTIONS: { label: string, value: ActivityFilterKey }[] = [
  { label: '全部', value: 'all' },
  { label: '筹划中', value: 'phase_0' },
  { label: '进行中', value: 'phase_1' },
  { label: '已截止', value: 'phase_2' },
  { label: '已删除', value: 'deleted' },
]

export type ActivitySortMode = 'id_asc' | 'createTime_asc' | 'createTime_desc'

export const ACTIVITY_SORT_OPTIONS: { label: string, value: ActivitySortMode }[] = [
  { label: 'ID 升序', value: 'id_asc' },
  { label: '创建时间 ↑', value: 'createTime_asc' },
  { label: '创建时间 ↓', value: 'createTime_desc' },
]

export const PRIZE_TYPE_LABELS: Record<number, string> = {
  0: '谢谢参与',
  1: '大奖',
  2: '小奖',
  3: '安慰奖',
  4: '积分奖',
  5: 'VIP天',
}

export function phaseLabel(p?: number | null) {
  if (p === undefined || p === null)
    return '—'
  return PHASE_OPTIONS.find(o => o.value === p)?.label ?? String(p)
}

export function parseActivityFilter(key: ActivityFilterKey) {
  if (key === 'deleted')
    return { phase: undefined, deleteState: 1 }
  if (key === 'phase_0')
    return { phase: 0, deleteState: 0 }
  if (key === 'phase_1')
    return { phase: 1, deleteState: 0 }
  if (key === 'phase_2')
    return { phase: 2, deleteState: 0 }
  return { phase: undefined, deleteState: 0 }
}

export function parseActivitySort(mode: ActivitySortMode) {
  if (mode === 'createTime_desc')
    return { sortBy: 'createTime', sortOrder: 'desc' }
  if (mode === 'createTime_asc')
    return { sortBy: 'createTime', sortOrder: 'asc' }
  return { sortBy: 'id', sortOrder: 'asc' }
}

export interface ActivityPrizeLineForm {
  activityPrizeId?: string
  prizeId: string
  /** 管理端录入的中奖概率（%），保存时换算为 weight */
  probabilityPercent: number
  /** 仅用于向后端提交，由 probabilityPercent 换算 */
  weight?: number
  stockRemaining: number
  imagePath: string
}
