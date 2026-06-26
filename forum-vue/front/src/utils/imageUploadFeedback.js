import { ElLoading } from 'element-plus'

const MB = 1024 * 1024

/** 与 backend-api 4.1：静态图服务端硬上限 */
export const STATIC_IMAGE_MAX_BYTES = 30 * MB

/** GIF 单独上限 */
export const GIF_IMAGE_MAX_BYTES = 15 * MB

/** 超过此大小的静态图由服务端压缩后再上传 */
export const SERVER_COMPRESS_THRESHOLD_BYTES = 5 * MB

export function isGifFile(file) {
  if (!file) return false
  const t = (file.type || '').toLowerCase()
  if (t === 'image/gif') return true
  const n = file.name || ''
  return /\.gif$/i.test(n)
}

/**
 * 上传前本地体积校验（与 docs/backend-api 4.1 一致）
 * @returns {{ ok: true } | { ok: false, message: string }}
 */
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

/**
 * 大图时提示服务端会压缩 + 审核，避免用户以为卡死
 * @param {File} file
 * @param {string} normalMessage 小图时的短文案
 */
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

/**
 * 多文件插图上传：任一大静态图则显示压缩提示
 * @param {FileList|File[]} files
 * @param {string} normalMessage
 */
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
