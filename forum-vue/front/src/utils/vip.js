/** 会员是否有效（未过期） */
export function isVipActive(vipTier, vipExpireAt) {
  const t = Number(vipTier) || 0
  if (t <= 0) return false
  if (!vipExpireAt) return true
  const ms = new Date(vipExpireAt).getTime()
  if (Number.isNaN(ms)) return true
  return Date.now() <= ms
}
