import request from './request'
import { useUserStore } from '@/stores/user'

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
/** 当前所选模型配额使用率（会员 ≥95% 可启用萌币扣费） */
export function getMascotQuotaHint(llmProvider) {
  return request({
    url: '/mascot/quota-hint',
    method: 'get',
    params: { llmProvider },
  })
}

export function postMascotChat(data) {
  return request({
    url: '/mascot/chat',
    method: 'post',
    data,
  })
}

/**
 * 看板娘流式对话（SSE）
 * @returns {() => void} abort
 */
export function streamMascotChat(data, { onChunk, onMeta, onDone, onError } = {}) {
  const userStore = useUserStore()
  const ctrl = new AbortController()
  fetch('/mascot/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(userStore.token ? { Authorization: userStore.token } : {}),
    },
    body: JSON.stringify(data),
    signal: ctrl.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        onError?.(`请求失败 (${res.status})`)
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
            if (o.error) {
              onError?.(o.error)
              onDone?.()
              return
            }
            if (o.text) onChunk?.(o.text)
            if (o.meta) onMeta?.(o.meta)
          } catch {
            /* ignore partial json */
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
