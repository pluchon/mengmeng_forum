import http from '@/utils/http'

export interface ArticleRow {
  id: string
  title: string
  boardId: string
  boardName: string
  userId: string
  username: string
  nickname: string
  status: number
  state: number
  deleteState: number
  visitCount: number
  replyCount: number
  createTime: string
}

export interface ArticlePreviewComment {
  nickname: string
  avatarUrl: string | null
  content: string
  likeCount: number
}

export interface ArticlePreview {
  id: string
  title: string
  boardName: string
  categoryName: string
  contentType: number
  content: string
  coverImg: string | null
  imageUrls: string[]
  status: number
  state: number
  deleteState: number
  userId: string
  username: string
  nickname: string
  authorAvatarUrl: string | null
  authorVipTier: number
  authorVipExpireAt: string | null
  topComments: ArticlePreviewComment[]
}

export function getArticleList(params: Record<string, unknown>) {
  return http.get<PageRes<ArticleRow[]>>('/admin/content/article/getList', params)
}

export function getArticlePreview(params: { id: string | number }) {
  return http.get<ArticlePreview>('/admin/content/article/preview', { id: Number(params.id) })
}

export function setArticleDeleteState(body: { id: number | string, deleteState: 0 | 1 }) {
  return http.post<unknown>('/admin/content/article/setDeleteState', {
    id: Number(body.id),
    deleteState: body.deleteState
  })
}

export function setArticleState(body: { id: number | string, state: 0 | 1 }) {
  return http.post<unknown>('/admin/content/article/setState', {
    id: Number(body.id),
    state: body.state
  })
}
