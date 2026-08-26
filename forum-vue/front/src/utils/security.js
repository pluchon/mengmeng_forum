// 安全工具类
import DOMPurify from 'dompurify'

const HTML_PURIFY_OPTIONS = {
  ALLOWED_TAGS: [
    'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'del', 'code', 'pre',
    'ul', 'ol', 'li', 'blockquote', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'a', 'img', 'video', 'audio', 'source', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'span', 'div',
  ],
  ALLOWED_ATTR: ['href', 'title', 'target', 'rel', 'src', 'alt', 'class', 'controls', 'poster', 'width', 'height'],
  ALLOW_DATA_ATTR: false,
}

// XSS 过滤 DOMPurify 白名单
export function sanitizeHtml(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, HTML_PURIFY_OPTIONS)
}

// 评论等纯文本场景：仅保留换行，不渲染 HTML 标签
export function sanitizePlainTextAsHtml(text) {
  if (!text) return ''
  const escaped = String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
  return escaped.replace(/\n/g, '<br>')
}
