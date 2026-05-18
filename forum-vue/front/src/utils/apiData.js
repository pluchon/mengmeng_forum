/**
 * 后端分页统一为 PageResult：{ records, total, pageNum, pageSize, ... }
 * axios 拦截器返回的 data 字段可能是整个 PageResult，也可能是裸数组。
 */
export function unwrapPageRecords(data) {
  if (data == null) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.records)) return data.records
  return []
}
