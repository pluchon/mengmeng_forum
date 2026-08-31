import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 断线后的重连节奏：越退越慢，但封顶，别让人一直等
const RECONNECT_DELAYS_MS = [1000, 2000, 4000, 8000, 10000]
// 连续重连多少次仍失败就放弃，避免开着页面无限空转
const MAX_RECONNECT_ATTEMPTS = 20

function buildGameWsUrl(path, token) {
  const cleanPath = String(path || '').replace(/^\/+/, '')
  const envBase = (import.meta.env.VITE_WS_BASE_URL || '').trim().replace(/\/+$/, '')
  if (envBase) {
    const base = envBase.endsWith('/ws') ? envBase : `${envBase}/ws`
    return `${base}/${cleanPath}?token=${encodeURIComponent(token)}`
  }
  const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${location.host}/ws/${cleanPath}?token=${encodeURIComponent(token)}`
}

function requestId(type) {
  return `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/**
 * 游戏实时连接。
 *
 * 断线会自动重连——服务端的对局不会因为你掉线就停下来，尤其是俄罗斯方块，
 * 重力照常推进，连不回去就只能看着方块自己堆死。重连成功后会回调 onReconnect，
 * 页面应当在那里重新拉一次权威状态，而不是接着用断线前的旧数据。
 */
export function useGameWebSocket(path, handlers = {}) {
  const socket = ref(null)
  const connected = ref(false)
  const connecting = ref(false)
  const reconnecting = ref(false)
  const lastError = ref('')
  let heartbeatTimer = null
  let reconnectTimer = null
  let reconnectAttempts = 0
  // 主动关闭（离开页面、切换房间）不该触发重连
  let manualClosed = false
  // 至少成功连上过一次，才把后续的断开当成“掉线”
  let everConnected = false

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socket.value?.readyState === WebSocket.OPEN) {
        socket.value.send('ping')
      }
    }, 30000)
  }

  function stopReconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    reconnecting.value = false
    reconnectAttempts = 0
  }

  function scheduleReconnect() {
    if (manualClosed || reconnectTimer) return
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      reconnecting.value = false
      lastError.value = '实时连接已断开，请刷新页面'
      handlers.onReconnectFailed?.()
      return
    }
    const delay = RECONNECT_DELAYS_MS[Math.min(reconnectAttempts, RECONNECT_DELAYS_MS.length - 1)]
    reconnectAttempts += 1
    reconnecting.value = true
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      if (manualClosed) return
      openSocket()
    }, delay)
  }

  function send(type, data = null) {
    if (socket.value?.readyState !== WebSocket.OPEN) {
      ElMessage.warning(reconnecting.value ? '正在重连，请稍候' : '实时连接未就绪，请稍后再试')
      return false
    }
    socket.value.send(JSON.stringify({ type, requestId: requestId(type), data }))
    return true
  }

  function close() {
    manualClosed = true
    stopHeartbeat()
    stopReconnect()
    if (socket.value) {
      try {
        socket.value.close()
      } catch {}
    }
    socket.value = null
    connected.value = false
    connecting.value = false
  }

  function openSocket() {
    const userStore = useUserStore()
    if (!userStore.token) return false
    if (socket.value?.readyState === WebSocket.OPEN || socket.value?.readyState === WebSocket.CONNECTING) {
      return true
    }
    connecting.value = true
    const ws = new WebSocket(buildGameWsUrl(path, userStore.token))
    socket.value = ws

    ws.onopen = () => {
      connecting.value = false
      connected.value = true
      lastError.value = ''
      const wasReconnect = everConnected
      everConnected = true
      stopReconnect()
      startHeartbeat()
      handlers.onOpen?.()
      // 断线期间服务端的状态早就变了，交给页面重新拉一次权威数据
      if (wasReconnect) handlers.onReconnect?.()
    }

    ws.onmessage = (event) => {
      if (event.data === 'pong') return
      try {
        handlers.onMessage?.(JSON.parse(event.data))
      } catch {
        lastError.value = '实时消息格式异常'
        handlers.onError?.()
      }
    }

    ws.onerror = () => {
      lastError.value = '实时连接发生异常'
      handlers.onError?.()
    }

    ws.onclose = () => {
      stopHeartbeat()
      if (socket.value === ws) socket.value = null
      connecting.value = false
      connected.value = false
      handlers.onClose?.()
      scheduleReconnect()
    }
    return true
  }

  function connect() {
    manualClosed = false
    reconnectAttempts = 0
    return openSocket()
  }

  onUnmounted(close)

  return {
    close,
    connect,
    connected,
    connecting,
    lastError,
    reconnecting,
    send,
    socket,
  }
}
