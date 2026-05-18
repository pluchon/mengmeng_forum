/**
 * 安全工具类
 */

/**
 * 简单的 XSS 过滤（建议后续引入 DOMPurify）
 * @param {string} html 
 */
export function sanitizeHtml(html) {
  if (!html) return ''
  // 基础过滤：移除 script 标签
  return html.replace(/<script\b[^>]*>([\s\S]*?)<\/script>/gim, "【检测到非法脚本已移除】")
}
