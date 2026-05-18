import request from './request'

// 按版块获取帖子列表（分页）
export function getArticleList(data) {
  return request({ url: '/board/selectBoardListByBoardIdWithPage', method: 'get', params: data })
}

// 首页版块统计（版块数量 + 帖子总数）
export function getHomeBoardStats() {
  return request({ url: '/board/selectBoardBy', method: 'get' })
}

// 获取热帖榜单 ID 列表
export function getHotArticleList(topN = 10) {
  return request({ url: '/article/getHotArticleList', method: 'get', params: { topN } })
}

// 获取帖子详情
export function getArticleDetail(articleId) {
  return request({ url: '/article/selectArticleDetailByArticleId', method: 'get', params: { articleId } })
}

// 创建帖子草稿
export function createDraft(data) {
  return request({ url: '/article/createDraft', method: 'post', data })
}

// 发布帖子（仅 APPROVED→PUBLISHED 手动发布模式；异步审核默认自动发布，一般无需调用）
export function publishArticle(articleId) {
  return request({ url: '/article/publishArticle', method: 'put', params: { articleId } })
}

/** 提交异步内容审核（LangGraph）；成功后 data 为 taskId UUID */
export function submitForAudit(data) {
  return request({ url: '/article/submitForAudit', method: 'post', data })
}

/** 轮询审核状态（刷新后兜底） */
export function getAuditStatus(articleId) {
  return request({ url: '/article/getAuditStatus', method: 'get', params: { articleId } })
}

/** 全量替换帖子相册图 URL 列表（0~15 张） */
export function replaceArticleImages(data) {
  return request({ url: '/article/replaceArticleImages', method: 'post', data })
}

// 更新帖子
export function updateArticle(data) {
  return request({ url: '/article/updateArticleByArticleId', method: 'put', data })
}

// 删除帖子
export function deleteArticle(articleId) {
  return request({ url: '/article/deleteArticle', method: 'delete', params: { articleId } })
}

// 获取 AI 摘要
export function getAiSummary(articleId) {
  return request({ url: '/article/getSummary', method: 'get', params: { articleId } })
}

// 按用户获取帖子列表 (包含用户信息)
export function getArticleListWithUser(params) {
  return request({ url: '/article/getArticleListByUserIdWithPageAndUserInfo', method: 'get', params })
}

// 按用户获取帖子列表
export function getArticleListByUser(params) {
  return request({ url: '/article/getArticleListByUserIdWithPage', method: 'get', params })
}

// AI 内容安全检测
export function validateText(content) {
  return request({ url: '/article/validateText', method: 'post', data: { content } })
}

// 上传帖子封面（第一步：上传文件拿 URL）
export function uploadCoverFile(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadCover',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

// 上传帖子内容图片（用于编辑器内插图）
export function uploadArticleImage(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadArticleImage',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

// 获取最新点赞用户
export function getLatestLikers(articleId, count = 15) {
  return request({ url: '/like/getLatestLikerUsers', method: 'get', params: { articleId, count } })
}

// 通过 URL 直接更新帖子封面（避免 CORS 重下载）
export function updateArticleCoverByUrl(articleId, coverUrl) {
  return request({ url: '/article/updateCoverUrl', method: 'post', params: { articleId, coverUrl } })
}

