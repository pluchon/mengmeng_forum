import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

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

export function useGameWebSocket(path, handlers = {}) {
  const socket = ref(null)
  const connected = ref(false)
  const connecting = ref(false)
  const lastError = ref('')
  let heartbeatTimer = null

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

  function send(type, data = null) {
    if (socket.value?.readyState !== WebSocket.OPEN) {
      ElMessage.warning('实时连接未就绪，请稍后再试')
      return false
    }
    socket.value.send(JSON.stringify({ type, requestId: requestId(type), data }))
    return true
  }

  function close() {
    stopHeartbeat()
    if (socket.value) {
      try {
        socket.value.close()
      } catch {}
    }
    socket.value = null
    connected.value = false
    connecting.value = false
  }

  function connect() {
    const userStore = useUserStore()
    if (!userStore.token) return false
    if (socket.value?.readyState === WebSocket.OPEN || socket.value?.readyState === WebSocket.CONNECTING) {
      return true
    }
    connecting.value = true
    lastError.value = ''
    const ws = new WebSocket(buildGameWsUrl(path, userStore.token))
    socket.value = ws

    ws.onopen = () => {
      connecting.value = false
      connected.value = true
      startHeartbeat()
      handlers.onOpen?.()
    }

    ws.onmessage = (event) => {
      if (event.data === 'pong') return
      try {
        handlers.onMessage?.(JSON.parse(event.data))
      } catch {
        console.error('[GameWebSocket] 解析消息失败: ', event.data)
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
    }
    return true
  }

  onUnmounted(close)

  return {
    close,
    connect,
    connected,
    connecting,
    lastError,
    send,
    socket,
  }
}
