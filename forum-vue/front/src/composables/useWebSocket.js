import { ref } from 'vue'
import { useUserStore } from '../stores/user'
import { useMessageStore } from '../stores/message'

// 全局单例，避免 MainLayout / Header 等多处 useWebSocket 各自 new 一份连接或互相 close 掉 CONNECTING
let sharedSocket = null
let sharedToken = ''
let heartbeatInterval = null
let reconnectTimer = null
let allowReconnect = false
const pendingMessages = []

// 通知长连接是否连着。自己是否在线不必再问服务端——页面开着、连接开着就是在线
const notifyConnected = ref(false)
// 每次「断开后重新连上」自增一次，供页面补拉断线期间落下的数据
const notifyReconnectedSignal = ref(0)
let notifyEverConnected = false

function stopHeartbeat() {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval)
    heartbeatInterval = null
  }
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function scheduleReconnect() {
  if (!allowReconnect || reconnectTimer) return
  if (sharedSocket?.readyState === WebSocket.OPEN || sharedSocket?.readyState === WebSocket.CONNECTING) return
  const userStore = useUserStore()
  if (!userStore.token) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    const latestUserStore = useUserStore()
    if (!allowReconnect || !latestUserStore.token) return
    useWebSocket().initWebSocket()
  }, 1200)
}

function attachHandlers(socket) {
  socket.onopen = () => {
    stopHeartbeat()
    clearReconnectTimer()
    flushPendingMessages()
    notifyConnected.value = true
    // 首次连上不算重连；只有断过再连上才需要补拉
    if (notifyEverConnected) notifyReconnectedSignal.value += 1
    notifyEverConnected = true
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
    } catch {}
  }

  socket.onclose = () => {
    stopHeartbeat()
    notifyConnected.value = false
    if (sharedSocket === socket) {
      sharedSocket = null
      sharedToken = ''
    }
    scheduleReconnect()
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
  if (['private_message_recalled', 'private_message_audit_failed'].includes(notifyData.type)) {
    const messageStore = useMessageStore()
    messageStore.onPrivateMessageMutation(notifyData)
    return
  }
  if (notifyData.type === 'group_create_audit') {
    const messageStore = useMessageStore()
    messageStore.onGroupCreateAudit(notifyData)
    return
  }
  if (['group_message', 'group_message_recalled', 'group_message_deleted', 'group_message_audit_failed', 'private_message_deleted'].includes(notifyData.type)) {
    const messageStore = useMessageStore()
    messageStore.onGroupMessage(notifyData)
    return
  }
}

function flushPendingMessages() {
  if (sharedSocket?.readyState !== WebSocket.OPEN) return
  while (pendingMessages.length) {
    sharedSocket.send(pendingMessages.shift())
  }
}

// 与后端 WebSocketConfigure 注册路径 /ws/notify 一致
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
    const token = userStore.token
    if (!token) return
    allowReconnect = true
    clearReconnectTimer()

    if (sharedSocket && sharedToken !== token) {
      stopHeartbeat()
      try {
        sharedSocket.close()
      } catch {}
      sharedSocket = null
      sharedToken = ''
    }

    if (sharedSocket?.readyState === WebSocket.OPEN) return
    if (sharedSocket?.readyState === WebSocket.CONNECTING) return

    const wsUrl = buildNotifyWsUrl(token)

    sharedSocket = new WebSocket(wsUrl)
    sharedToken = token
    attachHandlers(sharedSocket)
  }

  const closeWebSocket = () => {
    allowReconnect = false
    notifyConnected.value = false
    notifyEverConnected = false
    clearReconnectTimer()
    stopHeartbeat()
    pendingMessages.length = 0
    if (sharedSocket) {
      try {
        sharedSocket.close()
      } catch {}
      sharedSocket = null
    }
    sharedToken = ''
  }

  const sendNotifyMessage = (payload) => {
    if (!payload) return false
    const userStore = useUserStore()
    if (!userStore.token) return false
    const data = typeof payload === 'string' ? payload : JSON.stringify(payload)
    if (sharedSocket?.readyState === WebSocket.OPEN && sharedToken === userStore.token) {
      sharedSocket.send(data)
      return true
    }
    pendingMessages.push(data)
    initWebSocket()
    return true
  }

  const notifySocketState = () => ({
    readyState: sharedSocket?.readyState ?? WebSocket.CLOSED,
    open: sharedSocket?.readyState === WebSocket.OPEN,
    connecting: sharedSocket?.readyState === WebSocket.CONNECTING,
    pending: pendingMessages.length,
  })

  return { initWebSocket, closeWebSocket, notifyConnected, notifyReconnectedSignal, notifySocketState, sendNotifyMessage }
}
