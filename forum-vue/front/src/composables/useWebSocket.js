import { useUserStore } from '../stores/user'
import { useMessageStore } from '../stores/message'

/** 全局单例，避免 MainLayout / Header 等多处 useWebSocket() 各自 new 一份连接或互相 close 掉 CONNECTING */
let sharedSocket = null
let heartbeatInterval = null

function stopHeartbeat() {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval)
    heartbeatInterval = null
  }
}

function attachHandlers(socket) {
  socket.onopen = () => {
    stopHeartbeat()
    heartbeatInterval = setInterval(() => {
      if (sharedSocket?.readyState === WebSocket.OPEN) {
        sharedSocket.send('ping')
      }
    }, 30000)
  }

  socket.onmessage = (event) => {
    if (event.data === 'pong') return
    try {
      const notifyData = JSON.parse(event.data)
      handleNotifyMessage(notifyData)
    } catch (e) {
      console.error('[WebSocket] 解析消息失败: ', event.data)
    }
  }

  socket.onclose = () => {
    stopHeartbeat()
    if (sharedSocket === socket) sharedSocket = null
  }

  socket.onerror = (error) => {
    console.error('[WebSocket] 发生错误: ', error)
  }
}

function handleNotifyMessage(notifyData) {
  if (notifyData.type === 'reply') {
    return
  }
  if (notifyData.type === 'message_read') {
    const messageStore = useMessageStore()
    messageStore.notifyPeerRead({
      readerUserId: notifyData.readerUserId,
      messageId: notifyData.messageId,
    })
    return
  }
  if (notifyData.type === 'message') {
    const messageStore = useMessageStore()
    messageStore.onNewMessage(notifyData)
    return
  }
  if (notifyData.type === 'audit_result') {
    const messageStore = useMessageStore()
    messageStore.onAuditResult(notifyData)
    return
  }
  if (notifyData.type === 'system_message') {
    const messageStore = useMessageStore()
    messageStore.onSystemMessage(notifyData)
    return
  }
  if (notifyData.type === 'group_message') {
    const messageStore = useMessageStore()
    messageStore.onGroupMessage(notifyData)
  }
}

/** 与后端 WebSocketConfigure 注册路径 /ws/notify 一致 */
function buildNotifyWsUrl(token) {
  const envBase = (import.meta.env.VITE_WS_BASE_URL || '').trim().replace(/\/+$/, '')
  if (envBase) {
    const base = envBase.endsWith('/ws') ? envBase : `${envBase}/ws`
    return `${base}/notify?token=${encodeURIComponent(token)}`
  }
  const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${location.host}/ws/notify?token=${encodeURIComponent(token)}`
}

export function useWebSocket() {
  const initWebSocket = () => {
    const userStore = useUserStore()
    if (!userStore.token) return

    if (sharedSocket?.readyState === WebSocket.OPEN) return
    if (sharedSocket?.readyState === WebSocket.CONNECTING) return

    const wsUrl = buildNotifyWsUrl(userStore.token)

    sharedSocket = new WebSocket(wsUrl)
    attachHandlers(sharedSocket)
  }

  const closeWebSocket = () => {
    stopHeartbeat()
    if (sharedSocket) {
      try {
        sharedSocket.close()
      } catch {}
      sharedSocket = null
    }
  }

  return { initWebSocket, closeWebSocket }
}
