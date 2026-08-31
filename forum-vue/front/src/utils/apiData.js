// 后端分页统一为 PageResult：{ records, total, pageNum, pageSize, ... } axios 拦截器返回的 data 字段可能是整个 PageResult，也可能是裸数组
// 从失败的请求里取后端业务码。
// 走 200 的业务失败会被响应拦截器 reject 成 res 本身（有 code）；
// 走非 200 的会 reject 成 axios error，业务码在 response.data.code。
// 两种都要认，否则 error.code 拿到的是 ERR_BAD_REQUEST 这类 axios 自己的码
export function apiErrorCode(error) {
  const direct = error?.code
  if (typeof direct === 'number') return direct
  const nested = error?.response?.data?.code
  return typeof nested === 'number' ? nested : null
}

export function unwrapPageRecords(data) {
  if (data == null) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.records)) return data.records
  return []
}
