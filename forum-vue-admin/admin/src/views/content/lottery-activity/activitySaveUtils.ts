import type { LotteryActivityDetail } from '@/apis/content/lotteryActivity'
import type { ActivityPrizeLineForm } from './lotteryActivityShared'

const WEIGHT_SCALE = 10000

/** 将各行百分比换算为整数权重（比例与百分比一致） */
export function percentsToWeights(lines: ActivityPrizeLineForm[]): number[] {
  const percents = lines.map(l => Math.max(0, Number(l.probabilityPercent) || 0))
  const sum = percents.reduce((s, p) => s + p, 0)
  if (sum <= 0) {
    const even = lines.length > 0 ? Math.floor(WEIGHT_SCALE / lines.length) : 0
    return lines.map(() => Math.max(1, even))
  }
  const raw = percents.map(p => (p / sum) * WEIGHT_SCALE)
  const ints = raw.map(v => Math.max(1, Math.round(v)))
  const diff = WEIGHT_SCALE - ints.reduce((s, w) => s + w, 0)
  if (diff !== 0 && ints.length > 0) {
    const idx = ints.findIndex((_, i) => percents[i] === Math.max(...percents))
    ints[idx >= 0 ? idx : 0] = Math.max(1, ints[idx >= 0 ? idx : 0] + diff)
  }
  return ints
}

export function weightsToPercents(weights: number[]): number[] {
  const total = weights.reduce((s, w) => s + (Number(w) || 0), 0)
  if (total <= 0) return weights.map(() => 0)
  return weights.map(w => Math.round(((Number(w) || 0) / total) * 10000) / 100)
}

export function linesToSavePayload(lines: ActivityPrizeLineForm[]) {
  const weights = percentsToWeights(lines)
  return lines.map((l, i) => {
    const o: Record<string, unknown> = {
      prizeId: Number(l.prizeId),
      weight: weights[i],
      stockRemaining: l.stockRemaining,
    }
    if (l.activityPrizeId)
      o.activityPrizeId = Number(l.activityPrizeId)
    if (l.imagePath?.trim())
      o.imagePath = l.imagePath.trim()
    return o
  })
}

export function detailToLineForms(detail: LotteryActivityDetail): ActivityPrizeLineForm[] {
  const rows = detail.prizeLines ?? []
  const weights = rows.map(p => Number(p.weight) || 0)
  const percents = weightsToPercents(weights)
  return rows.map((p, i) => ({
    activityPrizeId: p.activityPrizeId ?? undefined,
    prizeId: p.prizeId != null && p.prizeId !== '' ? String(p.prizeId) : '',
    probabilityPercent: percents[i] ?? 0,
    stockRemaining: p.stockRemaining,
    imagePath: p.imagePath ?? '',
  }))
}

export function buildActivitySaveBody(
  detail: LotteryActivityDetail,
  lines: ActivityPrizeLineForm[],
) {
  return {
    id: Number(detail.id),
    title: detail.title,
    description: detail.description || null,
    coverImageUrl: detail.coverImageUrl || null,
    costPointsPerDraw: detail.costPointsPerDraw,
    status: detail.status,
    phase: detail.phase,
    startTime: detail.startTime || null,
    endTime: detail.endTime || null,
    lines: linesToSavePayload(lines),
  }
}
