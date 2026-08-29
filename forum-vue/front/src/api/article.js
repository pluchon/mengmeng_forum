import request from './request'

// 按版块获取帖子列表 分页
export function getArticleList(data) {
  return request({ url: '/board/selectBoardListByBoardIdWithPage', method: 'get', params: data })
}

// 热帖榜后端分页 每页最多14条，总榜最多28条
export function getHotArticleListWithPage(params) {
  return request({ url: '/article/getHotArticleListWithPage', method: 'get', params })
}

// 获取帖子详情
export function getArticleDetail(articleId) {
  return request({
    url: '/article/selectArticleDetailByArticleId',
    method: 'get',
    params: { articleId },
    publicAnonymousFallback: true,
  })
}

// 创建帖子草稿
export function createDraft(data) {
  return request({ url: '/article/createDraft', method: 'post', data })
}

// 提交异步内容审核 LangGraph ；成功后 data 为 taskId UUID
export function submitForAudit(data) {
  return request({ url: '/article/submitForAudit', method: 'post', data })
}

// 轮询审核状态 刷新后兜底
export function getAuditStatus(articleId) {
  return request({ url: '/article/getAuditStatus', method: 'get', params: { articleId } })
}

// 全量替换帖子相册图 URL 列表 0 15 张
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

// 查询持久化帖子总结及异步状态
export function getArticleSummaryState(articleId) {
  return request({
    url: '/article/summary',
    method: 'get',
    params: { articleId },
    publicAnonymousFallback: true,
  })
}

// 重新生成帖子总结
export function regenerateArticleSummary(articleId) {
  return request({ url: '/article/summary/regenerate', method: 'post', data: { articleId }, timeout: 65000 })
}

// 按用户获取帖子列表 包含用户信息
export function getArticleListWithUser(params) {
  return request({ url: '/article/getArticleListByUserIdWithPageAndUserInfo', method: 'get', params })
}

// 按用户获取帖子列表
export function getArticleListByUser(params) {
  return request({ url: '/article/getArticleListByUserIdWithPage', method: 'get', params })
}

// 创作中心数据看板 当前登录创作者
export function getCreatorDashboard(params) {
  return request({ url: '/article/creator/dashboard', method: 'get', params })
}

// 生成创作中心 AI 数据小结
export function generateCreatorInsight(period = 'WEEK') {
  return request({
    url: '/article/creator/insight',
    method: 'post',
    params: { period },
    timeout: 120000,
  })
}

export function getCreatorInsightData(period = 'WEEK') {
  return request({
    url: '/article/creator/insight-data',
    method: 'get',
    params: { period },
  })
}

export function reportArticleContent(data) {
  return request({ url: '/article/report', method: 'post', data })
}

// 上传帖子封面 第一步：上传文件拿 URL
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

// 上传帖子内容图片 用于编辑器内插图
export function uploadArticleImage(file, { onUploadProgress, silentHttpError } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadArticleImage',
    method: 'post',
    data: formData,
    onUploadProgress,
    silentHttpError: !!silentHttpError,
  })
}

// 批量上传帖子相册图（一次最多 9 张，支持部分成功）
export function uploadArticleImages(files, { onUploadProgress, silentHttpError } = {}) {
  const formData = new FormData()
  const list = Array.isArray(files) ? files : [files]
  list.forEach((file) => {
    if (file) formData.append('files', file)
  })
  return request({
    url: '/file/uploadArticleImages',
    method: 'post',
    data: formData,
    timeout: 300000,
    onUploadProgress,
    silentHttpError: !!silentHttpError,
  })
}

// 上传帖子视频 单个
export function uploadArticleVideo(file, { onUploadProgress, signal } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadArticleVideo',
    method: 'post',
    data: formData,
    timeout: 600000,
    onUploadProgress,
    signal,
  })
}

// 绑定帖子视频 URL 切换为视频帖
export function setArticleVideo(articleId, videoUrl) {
  return request({ url: '/article/setArticleVideo', method: 'post', params: { articleId, videoUrl } })
}

// 清空帖子视频 切回图片帖
export function clearArticleVideo(articleId) {
  return request({ url: '/article/clearArticleVideo', method: 'post', params: { articleId } })
}

// 氛围标签候选集（后端 Nacos 配置，筛选栏与投稿快选共用同一来源）
export function listMusicMoodTags() {
  return request({ url: '/article/music/moods', method: 'get' })
}

// 曲库列表（已发布且 AI 画像就绪，后端分页）
export function listMusicCatalog({ keyword, scope, mood, pageNum = 1, pageSize = 10 } = {}) {
  const params = { pageNum, pageSize }
  if (keyword) params.keyword = keyword
  if (scope && scope !== 'all') params.scope = scope
  if (mood) params.mood = mood
  return request({
    url: '/article/music/catalog',
    method: 'get',
    params,
  })
}

export function getMusicDiscoverFeatured() {
  return request({
    url: '/article/music/discover/featured',
    method: 'get',
  })
}

export function listMusicDiscoverRecommend({ pageNum = 1, pageSize = 6, excludeMusicKey } = {}) {
  const params = { pageNum, pageSize }
  if (excludeMusicKey) params.excludeMusicKey = excludeMusicKey
  return request({
    url: '/article/music/discover/recommend',
    method: 'get',
    params,
  })
}

export function listMusicDiscoverHot({ pageNum = 1, pageSize = 6 } = {}) {
  return request({
    url: '/article/music/discover/hot',
    method: 'get',
    params: { pageNum, pageSize },
  })
}

export function recommendArticleMusic(data) {
  return request({
    url: '/article/music/recommend',
    method: 'post',
    data,
    timeout: 120000,
  })
}

export function aiSearchArticleMusic(data) {
  return request({
    url: '/article/music/ai-search',
    method: 'post',
    data,
    timeout: 120000,
  })
}

export function parseArticleMusic(file) {
  const formData = new FormData()
  formData.append('audio', file)
  return request({
    url: '/article/music/parse',
    method: 'post',
    data: formData,
    timeout: 120000,
  })
}

export function trimArticleMusic(file, startSec, endSec) {
  const formData = new FormData()
  formData.append('audio', file)
  formData.append('startSec', String(startSec))
  formData.append('endSec', String(endSec))
  return request({
    url: '/article/music/trim',
    method: 'post',
    data: formData,
    timeout: 300000,
  })
}

export function uploadArticleMusic(formData, { onUploadProgress } = {}) {
  return request({
    url: '/article/music/upload',
    method: 'post',
    data: formData,
    timeout: 600000,
    onUploadProgress,
  })
}

export function retryArticleMusicAudit(id) {
  return request({
    url: '/article/music/retry-audit',
    method: 'post',
    params: { id },
  })
}

export function listMyMusic(scope) {
  return request({
    url: '/article/music/mine',
    method: 'get',
    params: { scope },
  })
}

export function listMusicFavorites() {
  return request({ url: '/article/music/favorites', method: 'get' })
}

export function toggleMusicFavorite(data) {
  return request({ url: '/article/music/favorite', method: 'post', data })
}

export function listMusicRecentPlays(pageNum = 1, pageSize = 5) {
  return request({
    url: '/article/music/recent',
    method: 'get',
    params: { pageNum, pageSize },
  })
}

export function recordMusicRecentPlay(data) {
  return request({ url: '/article/music/recent', method: 'post', data })
}

// 绑定帖子配乐
export function setArticleMusic(data) {
  return request({ url: '/article/setArticleMusic', method: 'post', data })
}

// 清空帖子配乐
export function clearArticleMusic(articleId) {
  return request({ url: '/article/clearArticleMusic', method: 'post', params: { articleId } })
}

// 通过 URL 直接更新帖子封面 避免 CORS 重下载
export function updateArticleCoverByUrl(articleId, coverUrl) {
  return request({ url: '/article/updateCoverUrl', method: 'post', params: { articleId, coverUrl } })
}
