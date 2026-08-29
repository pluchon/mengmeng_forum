// OSS 图片样式：把原图地址改写成走图片处理的地址。
// 样式在阿里云 OSS 控制台「图片处理 - 图片样式」里配，前端只填名字，
// 以后调尺寸/质量改控制台即可，不用重新构建。
//
// 注意：不要在 OSS 侧开启「原图保护」。下载原图和帖子审核拉图都走原始 URL，
// 一旦只允许按样式访问，这两条会同时失效。

// 相册缩略图。CSS 里是 64px 方块，2 倍屏需要 128px 图源才不糊
export const OSS_STYLE_THUMB = 'thumb'

// 只改写自家 OSS 的地址：外链、data:、blob: 一律原样返回
export function ossStyleUrl(url, styleName) {
  const raw = String(url || '').trim()
  if (!raw || !styleName) return raw
  if (!/^https?:\/\//i.test(raw)) return raw
  try {
    const parsed = new URL(raw)
    if (!/\.aliyuncs\.com$/i.test(parsed.hostname)) return raw
    // 已经带了处理参数就不再叠加，避免拼出非法的 x-oss-process
    if (parsed.searchParams.has('x-oss-process')) return raw
    parsed.searchParams.set('x-oss-process', `style/${styleName}`)
    return parsed.toString()
  } catch {
    return raw
  }
}

export function ossThumbUrl(url) {
  return ossStyleUrl(url, OSS_STYLE_THUMB)
}
