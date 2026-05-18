import http from '@/utils/http'

export interface ForumMemberPreview {
  id: string
  username: string
  nickname: string
  gender: number
  avatarUrl: string | null
  articleCount: number | null
  points: number | null
  vipTier: number
  vipExpireAt: string | null
  state: number
  isAdmin: number
  createTime: string
}

export function getForumMemberPreview(params: { userId: string | number }) {
  return http.get<ForumMemberPreview>('/admin/content/member/preview', {
    userId: Number(params.userId)
  })
}
