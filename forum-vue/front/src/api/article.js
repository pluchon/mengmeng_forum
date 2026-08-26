import request from './request'
import { useUserStore } from '@/stores/user'

// 按版块获取帖子列表 分页
export function getArticleList(data) {
  return request({ url: '/board/selectBoardListByBoardIdWithPage', method: 'get', params: data })
}

// 首页版块统计 版块数量 + 帖子总数
export function getHomeBoardStats() {
  return request({ url: '/board/selectBoardBy', method: 'get' })
}

// 获取热帖榜单 ID 列表
export function getHotArticleList(topN = 10) {
  return request({ url: '/article/getHotArticleList', method: 'get', params: { topN } })
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

// 发布帖子 仅 APPROVED→PUBLISHED 手动发布模式；异步审核默认自动发布，一般无需调用
export function publishArticle(articleId) {
  return request({ url: '/article/publishArticle', method: 'put', params: { articleId } })
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

// 获取 AI 摘要
export function getAiSummary(articleId) {
  return request({ url: '/article/getSummary', method: 'get', params: { articleId } })
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

// 流式生成 AI 智能导读 SSE
export function streamArticleGuide(articleId, { onChunk, onDone, onError } = {}) {
  const userStore = useUserStore()
  const ctrl = new AbortController()
  const url = `/article/streamGuide?articleId=${encodeURIComponent(articleId)}`
  fetch(url, {
    headers: userStore.token ? { Authorization: userStore.token } : {},
    signal: ctrl.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        const traceId = body?.traceId || res.headers.get('x-trace-id')
        const message = body?.message || '智能导读暂时不可用，请稍后重试'
        onError?.(traceId ? `${message}（参考编号：${traceId}）` : message)
        onDone?.()
        return
      }
      const reader = res.body?.getReader()
      if (!reader) {
        onError?.('浏览器不支持流式响应')
        onDone?.()
        return
      }
      const dec = new TextDecoder()
      let buf = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buf += dec.decode(value, { stream: true })
        const parts = buf.split('\n')
        buf = parts.pop() || ''
        for (const line of parts) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) continue
          const payload = trimmed.slice(5).trim()
          if (payload === '[DONE]') {
            onDone?.()
            return
          }
          try {
            const o = JSON.parse(payload)
            if (o.text) onChunk?.(o.text)
          } catch {
            // 忽略 partial json
          }
        }
      }
      onDone?.()
    })
    .catch((err) => {
      if (err?.name !== 'AbortError') onError?.(err?.message || '网络异常')
      onDone?.()
    })
  return () => ctrl.abort()
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

// AI 内容安全检测
export function validateText(content) {
  return request({ url: '/article/validateText', method: 'post', data: { content } })
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
export function uploadArticleVideo(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadArticleVideo',
    method: 'post',
    data: formData,
    timeout: 600000,
    onUploadProgress,
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
    params,
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
