import http from '@/utils/http'

/** 工作台公告预览（已发布） */
export interface NoticePreviewItem {
  id: number
  title: string
  subtitle: string | null
  updateTime: string
  pinTop: number
  noticeKind: number
}

export interface AiSeries {
  name: string
  data: number[]
}

export interface AiChartPayload {
  categories: string[]
  series: AiSeries[]
}

export interface AiUsageTrends {
  day: AiChartPayload
  week: AiChartPayload
  month: AiChartPayload
}

/** 文本模型 / 生图模型分开展示 */
export interface AiUsageTrendsBundle {
  text: AiUsageTrends
  image: AiUsageTrends
}

export interface LotteryDrawTrend {
  activityId: number | null
  activityTitle: string
  categories: string[]
  draws: number[]
}

export interface WorkbenchSummary {
  articleCount: number
  totalUserCount: number
  memberUserCount: number
  interactionCount: number
  nickname: string
  avatar: string
  noticePreview: NoticePreviewItem[]
  aiUsageTrends: AiUsageTrendsBundle
  lotteryDrawTrend?: LotteryDrawTrend
}

export function getWorkbenchSummary() {
  return http.get<WorkbenchSummary>('/admin/dashboard/workbench')
}
