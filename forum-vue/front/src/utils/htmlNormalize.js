/**
 * 若正文仅为单层 <p>...</p> 且内部无块级标签，则去掉外层 p，减轻「落库多一层 p」的困扰。
 */
export function stripSingleOuterParagraph(html) {
  if (!html || typeof html !== 'string') return html
  const t = html.trim()
  const m = t.match(/^<p(\s[^>]*)?>([\s\S]*?)<\/p>\s*$/i)
  if (!m) return html
  const inner = m[2]
  if (/<\s*(p|div|h[1-6]|ul|ol|table|blockquote|pre)(\s|>|\/)/i.test(inner)) {
    return html
  }
  const out = inner.trim()
  return out.length === 0 ? html : out
}
