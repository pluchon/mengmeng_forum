/** 会员是否有效（未过期） */
export function isVipActive(vipTier?: number | null, vipExpireAt?: string | null): boolean {
  const t = Number(vipTier) || 0
  if (t <= 0)
    return false
  if (!vipExpireAt)
    return true
  const ms = new Date(vipExpireAt).getTime()
  if (Number.isNaN(ms))
    return true
  return Date.now() <= ms
}

/** VIP 档位展示：pro / max */
export function vipTierLabel(vipTier?: number | null): string {
  const t = Number(vipTier) || 0
  if (t === 2)
    return 'MAX'
  if (t === 1)
    return 'PRO'
  return ''
}

/** 管理端「VIP 信息」一行文案 */
export function formatVipInfo(vipTier?: number | null, vipExpireAt?: string | null): string {
  if (!isVipActive(vipTier, vipExpireAt))
    return '非 VIP'
  const label = vipTierLabel(vipTier)
  return label ? `是 VIP · ${label}` : '是 VIP'
}
