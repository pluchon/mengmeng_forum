import request from './request'

// AI 长调用统一放宽：润色 / 标签 / 配图均为上游模型调用，慢属正常
const AI_LONG_TIMEOUT_MS = 300000

// 封面推荐配图要点 不计入写作配额
export function aiCoverHints(data) {
  return request({
    url: '/ai/cover-hints',
    method: 'post',
    data,
    timeout: AI_LONG_TIMEOUT_MS,
  })
}

// AI 生图 Java BFF > ai server
export function aiImage(data) {
  return request({
    url: '/ai/image',
    method: 'post',
    data,
    timeout: AI_LONG_TIMEOUT_MS,
  })
}

// 帖子正文一键生成封面 理解正文 > 按需检索 > 生图
export function aiArticleCover(data) {
  return request({
    url: '/ai/article-cover',
    method: 'post',
    data,
    timeout: AI_LONG_TIMEOUT_MS,
  })
}

// 帖子正文一键润色 Java BFF > ai server
export function aiPolish(data) {
  return request({
    url: '/ai/polish',
    method: 'post',
    data,
    timeout: AI_LONG_TIMEOUT_MS,
  })
}

// 查询 AI 创作工作区版本历史
export function getAiWorkspaceVersions(workspaceId) {
  return request({
    url: `/ai/workspaces/${workspaceId}/versions`,
    method: 'get',
  })
}

// 选择 AI 创作工作区版本
export function selectAiWorkspaceVersion(workspaceId, versionId) {
  return request({
    url: `/ai/workspaces/${workspaceId}/selected-version/${versionId}`,
    method: 'put',
  })
}

