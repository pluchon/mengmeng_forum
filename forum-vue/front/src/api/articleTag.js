import request from './request'

export function listArticleTags(boardId, pageNum = 1, keyword = '') {
  return request({
    url: '/article/tag/list',
    method: 'get',
    params: { boardId, pageNum, keyword: keyword || undefined },
  })
}

export function suggestArticleTags({ boardId, title, content, editorMode }) {
  return request({
    url: '/article/tag/suggest',
    method: 'post',
    data: { boardId, title, content, editorMode },
    // 标签推荐走 AI，与润色/配图同级放宽
    timeout: 300000,
  })
}

export function submitArticleTagFeedback({ boardId, proposedName, colorKey }) {
  return request({
    url: '/article/tag/feedback',
    method: 'post',
    data: { boardId, proposedName, colorKey },
    timeout: 35000,
  })
}
