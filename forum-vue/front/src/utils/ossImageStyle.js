// OSS 图片样式：把原图地址改写成走图片处理的地址。
// 样式在阿里云 OSS 控制台「图片处理 - 图片样式」里配，前端只填名字，
// 以后调尺寸/质量改控制台即可，不用重新构建。
//
// 注意：不要在 OSS 侧开启「原图保护」。下载原图和帖子审核拉图都走原始 URL，
// 一旦只允许按样式访问，这两条会同时失效。

// 相册缩略图。CSS 里是 64px 方块，2 倍屏需要 128px 图源才不糊
export const OSS_STYLE_THUMB = 'thumb'

// 首页瀑布流封面。卡片列宽固定 282px，2 倍屏约 564px，取 600 留点余量。
// 只按宽等比缩，不能裁成方图：瀑布流要按封面自然比例锁卡片高度
export const OSS_STYLE_FEED_COVER = 'feed'

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

// 头像。渲染尺寸 22-40px，96px 覆盖到 2 倍屏还有余量；方图裁剪对应圆形头像
export const OSS_STYLE_AVATAR = 'avatar'

export function ossAvatarUrl(url) {
  return ossStyleUrl(url, OSS_STYLE_AVATAR)
}

export function ossThumbUrl(url) {
  return ossStyleUrl(url, OSS_STYLE_THUMB)
}

export function ossFeedCoverUrl(url) {
  return ossStyleUrl(url, OSS_STYLE_FEED_COVER)
}
