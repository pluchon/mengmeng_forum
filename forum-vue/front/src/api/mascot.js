import request from './request'

/** 上架中的看板娘模型（无需登录） */
export function getMascotPublicModels() {
  return request({
    url: '/mascot/public/models',
    method: 'get',
  })
}

/** 登录用户设置看板娘 */
export function setMascotModel(modelId) {
  return request({
    url: '/user/setMascotModel',
    method: 'post',
    params: { modelId },
  })
}

/**
 * 看板娘对话（经 Java BFF -> Python）
 */
export function postMascotChat(data) {
  return request({
    url: '/mascot/chat',
    method: 'post',
    data,
  })
}

/** 陪伴助手：按功能列出会话 */
export function getCompanionSessions(skill) {
  return request({
    url: '/mascot/companion/sessions',
    method: 'get',
    params: { skill },
  })
}

/** 陪伴助手：加载会话消息 */
export function getCompanionMessages(sessionId) {
  return request({
    url: `/mascot/companion/sessions/${sessionId}/messages`,
    method: 'get',
  })
}
