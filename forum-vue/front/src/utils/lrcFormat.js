// 时间标签：[mm:ss] / [mm:ss.xx] / [mm:ss:xx]
const TIME_TAG = /\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\]/
const LEADING_TAGS = /^(?:\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\])+/

/**
 * 判断是否为逐字（增强型）歌词。
 *
 * 标准 LRC 允许一行挂多个**行首**时间戳来复用副歌，例如
 * `[00:12.00][01:30.00]副歌`，所以不能简单地数标签个数。
 * 真正的逐字歌词特征是：剥掉行首连续标签后，正文里**仍然**嵌着时间戳，
 * 例如 `[00:00.000]发[00:00.290]如[00:00.580]雪`。
 * 用占比判断而不是「有一行就算」，避免个别脏行造成误判。
 */
export function isWordTimedLrc(text) {
  const lines = String(text || '').split(/\r?\n/)
  let counted = 0
  let inlineHits = 0
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    const body = line.replace(LEADING_TAGS, '')
    if (!body.trim()) continue
    counted += 1
    if (TIME_TAG.test(body)) inlineHits += 1
  }
  return counted > 0 && inlineHits / counted >= 0.5
}
