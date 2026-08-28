// 将后端 usageStats 格式化为普通用户可读的单行说明 厂商 token + 耗时
export function formatAiUsageLine(stats) {
  if (!stats || typeof stats !== 'object') return ''
  const parts = []
  const ms = Number(stats.latencyMs)
  if (Number.isFinite(ms) && ms > 0) {
    parts.push(ms >= 1000 ? `用时 ${(ms / 1000).toFixed(1)} 秒` : `用时 ${Math.round(ms)} 毫秒`)
  }
  const inp = Number(stats.inputTokens)
  const out = Number(stats.outputTokens)
  if (Number.isFinite(inp) && inp > 0) parts.push(`阅读 ${inp} token`)
  if (Number.isFinite(out) && out > 0) parts.push(`回复 ${out} token`)
  const imgs = Number(stats.imageCount)
  if (Number.isFinite(imgs) && imgs > 0) parts.push(`生成 ${imgs} 张图`)
  if (stats.estimated) parts.push('（用量为估算）')
  if (stats.billingMode === 'vip_quota') {
    parts.push('会员额度')
  } else if (stats.billingMode === 'free_quota') {
    parts.push('免费额度')
  }
  return parts.join(' · ')
}

export function usageStatsFromApi(metaOrData) {
  if (!metaOrData) return null
  const s = metaOrData.usageStats
  if (s && typeof s === 'object') return s
  return null
}
