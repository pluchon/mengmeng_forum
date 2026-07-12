import request from './request'

/**
 * 解析题库文件
 * @param {{ subject: string, file: File }} payload
 */
export function analyzeExamWord(payload) {
  const formData = new FormData()
  formData.append('subject', payload.subject)
  formData.append('file', payload.file)
  return request({
    url: '/exam-question-bank/analyze-word',
    method: 'post',
    data: formData,
    timeout: 180000,
  })
}

/**
 * 修改题库题目
 * @param {{ bankId: number|string, questionId: number|string, stem: string, options: Array<{ label: string, text: string }>, answer?: string, explanation?: string }} data
 */
export function updateExamQuestion(data) {
  return request({
    url: '/exam-question-bank/question',
    method: 'post',
    data,
  })
}

/**
 * 获取当前账号已有题库科目
 */
export function getExamBankSubjects() {
  return request({
    url: '/exam-question-bank/subjects',
    method: 'get',
  })
}

/**
 * 获取最新题库
 * @param {string} subject
 */
export function getLatestExamBank(subject) {
  return request({
    url: '/exam-question-bank/latest',
    method: 'get',
    params: { subject },
  })
}

/**
 * 获取题库答题进度
 * @param {number|string} bankId
 */
export function getExamQuestionProgress(bankId) {
  return request({
    url: '/exam-question-bank/progress',
    method: 'get',
    params: { bankId },
  })
}

/**
 * 保存单题答题进度
 * @param {{ bankId: number|string, questionId: number|string, answerText?: string, answered?: boolean, correct?: boolean|null, wrong?: boolean, focus?: boolean, judgeScore?: number }} data
 */
export function saveExamQuestionProgress(data) {
  return request({
    url: '/exam-question-bank/progress',
    method: 'post',
    data,
  })
}

/**
 * 主观题 AI 评分
 * @param {{ subject: string, question: string, standardAnswer: string, userAnswer: string }} data
 */
export function judgeSubjectiveAnswer(data) {
  return request({
    url: '/exam-question-bank/judge-subjective',
    method: 'post',
    data,
    timeout: 120000,
  })
}
