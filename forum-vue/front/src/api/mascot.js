import request from './request'
import { useUserStore } from '@/stores/user'

// 流式空闲超时略短于网关上限，避免浏览器无限等待
const STREAM_IDLE_MS = 145_000

// 看板娘对话 经 Java BFF > Python
// 当前所选模型配额使用率 会员 ≥95% 可启用萌币扣费
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
    timeout: 65000,
  })
}

// 用户确认后检索并保存看板娘相关帖子结果
export function getMascotRelatedRecommendations(data) {
  return request({
    url: '/mascot/related-recommendations',
    method: 'post',
    data,
  })
}

// 读取当前会话已保存的相关帖子检索结果
export function listMascotRelatedRecommendations(sessionId) {
  return request({
    url: '/mascot/related-recommendations',
    method: 'get',
    params: { sessionId },
  })
}

// 看板娘流式对话 SSE
export function streamMascotChat(data, { onChunk, onMeta, onDone, onError } = {}) {
  const userStore = useUserStore()
  const ctrl = new AbortController()
  let idleTimer = null
  let timedOut = false
  let settled = false

  function settle(fn) {
    if (settled) return
    settled = true
    if (idleTimer) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
    fn?.()
  }

  function touchIdle() {
    if (settled) return
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(() => {
      timedOut = true
      ctrl.abort()
    }, STREAM_IDLE_MS)
  }

  touchIdle()

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
        const body = await res.json().catch(() => null)
        const traceId = body?.traceId || res.headers.get('x-trace-id')
        const message = body?.message || '看板娘暂时无法回应，请稍后重试'
        settle(() => {
          onError?.(traceId ? `${message}（参考编号：${traceId}）` : message)
          onDone?.()
        })
        return
      }
      const reader = res.body?.getReader()
      if (!reader) {
        settle(() => {
          onError?.('浏览器不支持流式响应')
          onDone?.()
        })
        return
      }
      const dec = new TextDecoder()
      let buf = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        touchIdle()
        buf += dec.decode(value, { stream: true })
        const parts = buf.split('\n')
        buf = parts.pop() || ''
        for (const line of parts) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) continue
          const payload = trimmed.slice(5).trim()
          if (payload === '[DONE]') {
            settle(() => onDone?.())
            return
          }
          try {
            const o = JSON.parse(payload)
            touchIdle()
            if (o.error) {
              settle(() => {
                onError?.(o.error)
                onDone?.()
              })
              return
            }
            if (o.text) onChunk?.(o.text)
            if (o.meta) onMeta?.(o.meta)
          } catch {
            // 忽略 partial json
          }
        }
      }
      const trailing = buf.trim()
      if (trailing.startsWith('data:')) {
        try {
          const o = JSON.parse(trailing.slice(5).trim())
          if (o.error) {
            settle(() => {
              onError?.(o.error)
              onDone?.()
            })
            return
          }
          if (o.text) onChunk?.(o.text)
          if (o.meta) onMeta?.(o.meta)
        } catch {
          // 忽略 incomplete trailing SSE payload
        }
      }
      settle(() => onDone?.())
    })
    .catch((err) => {
      settle(() => {
        if (err?.name === 'AbortError') {
          if (timedOut) onError?.('响应超时，请重试')
        } else {
          onError?.(err?.message || '网络异常')
        }
        onDone?.()
      })
    })
  return () => {
    if (idleTimer) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
    ctrl.abort()
  }
}

// 陪伴助手：按功能列出会话
export function getCompanionSessions(skill) {
  return request({
    url: '/mascot/companion/sessions',
    method: 'get',
    params: { skill },
  })
}

// 陪伴助手：加载会话消息
export function getCompanionMessages(sessionId) {
  return request({
    url: `/mascot/companion/sessions/${sessionId}/messages`,
    method: 'get',
  })
}

// 陪伴助手：读取会话上下文占用估算
export function getCompanionContextWindow(sessionId) {
  return request({
    url: `/mascot/companion/sessions/${sessionId}/context-window`,
    method: 'get',
  })
}

// 陪伴助手：压缩并保存会话上下文
export function compressCompanionContext(sessionId) {
  return request({
    url: `/mascot/companion/sessions/${sessionId}/compress-context`,
    method: 'post',
  })
}

export function getMascotMemory() {
  return request({
    url: '/mascot/companion/memory',
    method: 'get',
  })
}

export function editMascotMemory(data) {
  return request({
    url: '/mascot/companion/memory',
    method: 'post',
    data,
  })
}

// 陪伴助手：删除指定会话
export function deleteCompanionSession(sessionId) {
  return request({
    url: `/mascot/companion/sessions/${sessionId}`,
    method: 'delete',
  })
}

// 陪伴助手：修改会话名称
export function renameCompanionSession(sessionId, data) {
  return request({
    url: `/mascot/companion/sessions/${sessionId}`,
    method: 'put',
    data,
  })
}
