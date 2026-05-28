/**
 * 从 axios 错误对象提取后端返回的可读文案（含 HTTP 400 校验失败）。
 */
export function extractApiErrorMessage(error, fallback = '请求失败，请稍后重试') {
  if (!error) return fallback
  const data = error.response?.data
  if (data && typeof data === 'object') {
    if (typeof data.message === 'string' && data.message.trim()) return data.message.trim()
    if (typeof data.msg === 'string' && data.msg.trim()) return data.msg.trim()
  }
  if (typeof error.message === 'string' && error.message.trim() && error.message !== 'Error') {
    return error.message.trim()
  }
  if (error.code !== undefined && typeof error.message === 'string') {
    return error.message
  }
  return fallback
}
