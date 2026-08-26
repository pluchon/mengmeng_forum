import { ElLoading } from 'element-plus'

const MB = 1024 * 1024

// 与 backend api 4.1：静态图服务端硬上限
export const STATIC_IMAGE_MAX_BYTES = 30 * MB

// GIF 单独上限
export const GIF_IMAGE_MAX_BYTES = 15 * MB

// 超过此大小的静态图由服务端压缩后再上传
export const SERVER_COMPRESS_THRESHOLD_BYTES = 5 * MB

export function isGifFile(file) {
  if (!file) return false
  const t = (file.type || '').toLowerCase()
  if (t === 'image/gif') return true
  const n = file.name || ''
  return /\.gif$/i.test(n)
}

// 上传前本地体积校验 与 docs/backend api 4.1 一致
export function validateLocalImageFile(file) {
  if (!file?.size) return { ok: false, message: '请选择文件' }
  if (isGifFile(file)) {
    if (file.size > GIF_IMAGE_MAX_BYTES) {
      return { ok: false, message: 'GIF 动图不能超过 15MB，请压缩后重试' }
    }
    return { ok: true }
  }
  if (file.size > STATIC_IMAGE_MAX_BYTES) {
    return { ok: false, message: '图片不能超过 30MB，请选择较小的文件' }
  }
  return { ok: true }
}

function detectUnsupportedImageLabel(head) {
  if (head.length >= 12 && head[4] === 0x66 && head[5] === 0x74 && head[6] === 0x79 && head[7] === 0x70) {
    const brand = String.fromCharCode(head[8], head[9], head[10], head[11]).toLowerCase()
    if (brand.startsWith('hei') || brand === 'mif1') return 'HEIC/HEIF'
    if (brand.startsWith('avif')) return 'AVIF'
    if (brand.startsWith('webp')) return 'WebP'
    return `ISOBMFF(${brand})`
  }
  if (head.length >= 12
    && head[0] === 0x52 && head[1] === 0x49 && head[2] === 0x46 && head[3] === 0x46
    && head[8] === 0x57 && head[9] === 0x45 && head[10] === 0x42 && head[11] === 0x50) {
    return 'WebP'
  }
  return null
}

function isSupportedImageMagic(head) {
  if (head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) return true
  if (head.length >= 8
    && head[0] === 0x89 && head[1] === 0x50 && head[2] === 0x4e && head[3] === 0x47
    && head[4] === 0x0d && head[5] === 0x0a && head[6] === 0x1a && head[7] === 0x0a) return true
  if (head.length >= 6) {
    const sig = String.fromCharCode(head[0], head[1], head[2], head[3], head[4], head[5])
    if (sig === 'GIF87a' || sig === 'GIF89a') return true
  }
  return false
}

// 读取文件头魔数，避免扩展名为 .png 实为 HEIC 等格式
export async function validateLocalImageFileMagic(file) {
  const basic = validateLocalImageFile(file)
  if (!basic.ok) return basic
  try {
    const head = new Uint8Array(await file.slice(0, 16).arrayBuffer())
    if (isSupportedImageMagic(head)) return { ok: true }
    const detected = detectUnsupportedImageLabel(head)
    if (detected) {
      return { ok: false, message: `检测到 ${detected} 格式，请转换为 JPG / PNG / GIF 后上传` }
    }
    return { ok: false, message: '无法识别的图片内容，请确认文件为 JPG / PNG / GIF' }
  } catch {
    return { ok: false, message: '无法读取图片文件' }
  }
}

// 大图时提示服务端会压缩 + 审核，避免用户以为卡死
export function getImageUploadLoadingText(file, normalMessage) {
  if (!file?.size) return normalMessage
  if (isGifFile(file)) {
    if (file.size > SERVER_COMPRESS_THRESHOLD_BYTES) return '动图较大，正在上传请稍候…'
    return normalMessage
  }
  if (file.size > SERVER_COMPRESS_THRESHOLD_BYTES) {
    return '图片较大，服务器正在压缩与安全审核，请稍候…'
  }
  return normalMessage
}

export function openImageUploadLoading(file, normalMessage) {
  return ElLoading.service({
    lock: true,
    text: getImageUploadLoadingText(file, normalMessage),
    background: 'rgba(255, 255, 255, 0.72)',
    customClass: 'forum-image-upload-loading',
  })
}

// 多文件插图上传：任一大静态图则显示压缩提示
export function getBatchImageUploadLoadingText(files, normalMessage) {
  const arr = files == null ? [] : Array.isArray(files) ? files : Array.from(files)
  if (arr.some((f) => f?.size && !isGifFile(f) && f.size > SERVER_COMPRESS_THRESHOLD_BYTES)) {
    return '含较大图片，服务器正在压缩与安全审核，请稍候…'
  }
  if (arr.some((f) => f?.size && isGifFile(f) && f.size > SERVER_COMPRESS_THRESHOLD_BYTES)) {
    return '含较大动图，正在上传请稍候…'
  }
  return normalMessage
}
