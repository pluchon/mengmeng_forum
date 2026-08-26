// 从 axios 错误对象提取后端返回的可读文案 含 HTTP 400 校验失败
export function extractApiErrorMessage(error, fallback = '请求失败，请稍后重试') {
  if (!error) return fallback
  const data = error.response?.data
  const traceId = data?.traceId || error.response?.headers?.['x-trace-id']
  const withTrace = message => traceId ? `${message}（参考编号：${traceId}）` : message
  if (data && typeof data === 'object') {
    if (typeof data.message === 'string' && data.message.trim()) return withTrace(data.message.trim())
    if (typeof data.msg === 'string' && data.msg.trim()) return withTrace(data.msg.trim())
  }
  return withTrace(fallback)
}
