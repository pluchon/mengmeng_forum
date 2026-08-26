export const ARTICLE_TYPE = Object.freeze({
  NORMAL: 0,
  QUESTION: 1,
})

export const QUESTION_STATUS = Object.freeze({
  WAITING: 0,
  RESOLVED: 1,
  CLOSED: 2,
})

export function isQuestionArticle(article) {
  return Number(article?.articleType) === ARTICLE_TYPE.QUESTION
}

export function questionStatusLabel(status) {
  const value = Number(status)
  if (value === QUESTION_STATUS.RESOLVED) return '已解决'
  // 历史「已关闭」按待解决展示 关闭能力已移除
  return '待解决'
}

export function questionStatusClass(status) {
  const value = Number(status)
  if (value === QUESTION_STATUS.RESOLVED) return 'is-resolved'
  return 'is-waiting'
}

export function isQuestionResolved(article) {
  return Number(article?.questionStatus) === QUESTION_STATUS.RESOLVED
}
