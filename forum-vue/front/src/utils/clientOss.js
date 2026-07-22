const CLIENT_OSS_BASE = 'https://item-for-picture-with-zhanglihong.oss-cn-shenzhen.aliyuncs.com'
const DEFAULT_PREFIX = `${CLIENT_OSS_BASE}/forum_images/client/webp/`
const RAW_PREFIX = import.meta.env.VITE_CLIENT_OSS_PREFIX || DEFAULT_PREFIX
const PREFIX = RAW_PREFIX ? (RAW_PREFIX.endsWith('/') ? RAW_PREFIX : `${RAW_PREFIX}/`) : DEFAULT_PREFIX

/** 认证页左侧宽屏插画（OSS 完整路径） */
export const LOGIN_WEBP_URL = `${CLIENT_OSS_BASE}/forum_images/client/webp/login.webp`
export const REGISTER_WEBP_URL = `${CLIENT_OSS_BASE}/forum_images/client/webp/register.webp`
export const FIND_WEBP_URL = `${CLIENT_OSS_BASE}/forum_images/client/webp/forget.webp`

export function clientOssUrl(filename, fallbackUrl = '') {
  if (!filename) return fallbackUrl || ''
  const name = String(filename).trim()
  if (/^https?:\/\//i.test(name)) return name
  if (name.startsWith('forum_images/')) {
    return `${CLIENT_OSS_BASE}/${name.replace(/^\//, '')}`
  }
  if (!PREFIX) return fallbackUrl || ''
  const base = PREFIX.endsWith('/') ? PREFIX : `${PREFIX}/`
  const leaf = name.replace(/^\//, '')
  return `${base}${leaf}`
}
