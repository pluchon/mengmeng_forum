// 图片下载。原本只长在帖子详情页里，消息中心也要用，抽出来两边共用。
//
// 跨域是这里唯一麻烦的地方：OSS 直链多数没开 CORS，fetch 会被挡下。
// 所以按三档依次尝试——同源代理、原地址、canvas 重绘，任一成功即可。

// 开发态 / 生产同源代理：把阿里云 OSS 地址改写到 /oss-dl/，避开浏览器 CORS
export function toDownloadFetchUrl(url) {
  try {
    const u = new URL(url, window.location.origin)
    if (!/\.aliyuncs\.com$/i.test(u.hostname)) return url
    return `/oss-dl/${u.hostname}${u.pathname}${u.search}`
  } catch {
    return url
  }
}

export function guessImageFileName(url, prefix = 'image', index = 1) {
  const base = `${prefix}-${index}`
  try {
    const path = new URL(url, window.location.origin).pathname
    const last = path.split('/').pop() || ''
    const extMatch = last.match(/\.(jpe?g|png|webp|gif|bmp)$/i)
    if (extMatch) return `${base}.${extMatch[1].toLowerCase()}`
  } catch {
    // 忽略
  }
  return `${base}.jpg`
}

export function triggerBlobDownload(href, fileName, shouldRevoke) {
  const a = document.createElement('a')
  a.href = href
  a.download = fileName
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  if (shouldRevoke && href.startsWith('blob:')) {
    URL.revokeObjectURL(href)
  }
}

async function fetchBlobFromUrl(url) {
  const res = await fetch(url, { mode: 'cors', credentials: 'omit', cache: 'no-store' })
  if (!res.ok) throw new Error(`download status ${res.status}`)
  return res.blob()
}

// OSS 开启跨域时，用 canvas 导出为 blob，不必新开标签页
function blobFromCrossOriginImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = img.naturalWidth || img.width
        canvas.height = img.naturalHeight || img.height
        if (!canvas.width || !canvas.height) {
          reject(new Error('empty image'))
          return
        }
        const ctx = canvas.getContext('2d')
        ctx.drawImage(img, 0, 0)
        canvas.toBlob((blob) => {
          if (blob) resolve(blob)
          else reject(new Error('toBlob failed'))
        }, 'image/jpeg', 0.92)
      } catch (err) {
        reject(err)
      }
    }
    img.onerror = () => reject(new Error('image load failed'))
    // 强制走带 CORS 头的新请求，避开无 CORS 的缓存副本
    try {
      const u = new URL(url, window.location.origin)
      u.searchParams.set('_dl', String(Date.now()))
      img.src = u.toString()
    } catch {
      img.src = url
    }
  })
}

/**
 * 按 URL 下载图片，取回的是原图。
 *
 * @returns {Promise<boolean>} 是否成功；失败由调用方决定怎么提示
 */
export async function downloadImageByUrl(url, fileName) {
  if (!url) return false
  const name = fileName || guessImageFileName(url)
  if (url.startsWith('blob:') || url.startsWith('data:')) {
    triggerBlobDownload(url, name, false)
    return true
  }

  const candidates = []
  const proxied = toDownloadFetchUrl(url)
  if (proxied !== url) candidates.push(proxied)
  if (/^https?:\/\//i.test(url) || url.startsWith('/')) candidates.push(url)

  let blob = null
  for (const candidate of candidates) {
    try {
      blob = await fetchBlobFromUrl(candidate)
      break
    } catch {
      // 换下一个候选
    }
  }
  if (!blob && /^https?:\/\//i.test(url)) {
    try {
      blob = await blobFromCrossOriginImage(url)
    } catch {
      blob = null
    }
  }
  if (!blob) return false

  const objectUrl = URL.createObjectURL(blob)
  triggerBlobDownload(objectUrl, name, true)
  return true
}
