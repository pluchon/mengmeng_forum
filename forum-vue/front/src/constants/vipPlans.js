// 会员方案仅保留前端展示元数据，价格与额度由后端返回
export const VIP_PLAN_META = {
  free: {
    code: 'free',
    tier: 0,
    name: '免费',
  },
  pro: {
    code: 'pro',
    tier: 1,
    name: 'PRO',
  },
  max: {
    code: 'max',
    tier: 2,
    name: 'MAX',
  },
}

export function formatYuan(n) {
  const v = Number(n)
  if (!Number.isFinite(v)) return '—'
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}
