import request from './request'

export function listArticleTags(boardId) {
  return request({
    url: '/article/tag/list',
    method: 'get',
    params: { boardId },
  })
}

export function suggestArticleTags({ boardId, title, content }) {
  return request({
    url: '/article/tag/suggest',
    method: 'get',
    params: { boardId, title, content: content?.slice(0, 200) },
  })
}

export function submitArticleTagFeedback({ boardId, proposedName }) {
  return request({
    url: '/article/tag/feedback',
    method: 'post',
    data: { boardId, proposedName },
  })
}
