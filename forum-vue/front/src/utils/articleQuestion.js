export const ARTICLE_TYPE = Object.freeze({
  NORMAL: 0,
  QUESTION: 1,
})

export const QUESTION_STATUS = Object.freeze({
  WAITING: 0,
  RESOLVED: 1,
  CLOSED: 2,
})

export const QUESTION_FILTER = Object.freeze({
  ALL: 'all',
  QUESTION: 'question',
  WAITING: 'waiting',
  RESOLVED: 'resolved',
})

export function isQuestionArticle(article) {
  return Number(article?.articleType) === ARTICLE_TYPE.QUESTION
}

export function questionStatusLabel(status) {
  const value = Number(status)
  if (value === QUESTION_STATUS.RESOLVED) return '已解决'
  if (value === QUESTION_STATUS.CLOSED) return '已关闭'
  return '待解决'
}

export function questionStatusClass(status) {
  const value = Number(status)
  if (value === QUESTION_STATUS.RESOLVED) return 'is-resolved'
  if (value === QUESTION_STATUS.CLOSED) return 'is-closed'
  return 'is-waiting'
}

export function questionFilterParams(filter) {
  if (filter === QUESTION_FILTER.QUESTION) {
    return { articleType: ARTICLE_TYPE.QUESTION }
  }
  if (filter === QUESTION_FILTER.WAITING) {
    return {
      articleType: ARTICLE_TYPE.QUESTION,
      questionStatus: QUESTION_STATUS.WAITING,
    }
  }
  if (filter === QUESTION_FILTER.RESOLVED) {
    return {
      articleType: ARTICLE_TYPE.QUESTION,
      questionStatus: QUESTION_STATUS.RESOLVED,
    }
  }
  return {}
}
