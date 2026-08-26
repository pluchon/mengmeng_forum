// 论坛接口约定：无时区的 yyyy MM dd HH:mm:ss 按东八区 与后端 DB / Jackson 对齐
const CN_OFFSET = '+08:00'
const NAIVE_DT = /^(\d{4}-\d{2}-\d{2})[\sT](\d{2}:\d{2}(?::\d{2})?)(?:\.(\d{1,3}))?$/

export function parseForumDateTime(input) {
  if (input == null || input === '') return null
  if (input instanceof Date) return Number.isNaN(input.getTime()) ? null : input
  if (typeof input === 'number') {
    const d = new Date(input)
    return Number.isNaN(d.getTime()) ? null : d
  }
  if (typeof input === 'string') {
    const s = input.trim()
    if (/^\d+$/.test(s)) {
      const d = new Date(Number(s))
      return Number.isNaN(d.getTime()) ? null : d
    }
    const hasTz = /[zZ]$|[+-]\d{2}:?\d{2}$/.test(s)
    const m = s.match(NAIVE_DT)
    if (m && !hasTz) {
      const datePart = m[1]
      let timePart = m[2]
      if (timePart.length === 5) timePart += ':00'
      const frac = m[3] ? `.${m[3].padEnd(3, '0').slice(0, 3)}` : ''
      const isoLocal = `${datePart}T${timePart}${frac}${CN_OFFSET}`
      const d = new Date(isoLocal)
      return Number.isNaN(d.getTime()) ? null : d
    }
    let d = new Date(s)
    if (!Number.isNaN(d.getTime())) return d
    const replaced = s.replace(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})/, '$1T$2')
    d = new Date(replaced)
    return Number.isNaN(d.getTime()) ? null : d
  }
  return null
}

// 签到记录表格「日历日」：优先按无时区 yyyy MM dd 展示；带 Z 的 ISO 按 Asia/Taipei 取日
export function formatCheckinLogDateOnly(input) {
  if (input == null || input === '') return '—'
  const s = String(input).trim()
  const head = s.match(/^(\d{4}-\d{2}-\d{2})/)
  const hasExplicitTz = /[zZ]$|[+-]\d{2}:?\d{2}$/.test(s)
  if (head && !hasExplicitTz) return head[1]
  const d = parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return head ? head[1] : s.slice(0, 10)
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d)
}

// 当前日历日在 Asia/Taipei 的 yyyy MM dd 用于本地缓存键等
export function shanghaiCalendarYmd(date = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function _intlPartsShanghai(d, withTime) {
  const opts = {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    ...(withTime
      ? { hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }
      : {}),
  }
  const parts = new Intl.DateTimeFormat('en-CA', opts).formatToParts(d)
  const pick = (t) => parts.find((p) => p.type === t)?.value ?? ''
  const date = `${pick('year')}-${pick('month')}-${pick('day')}`
  if (!withTime) return date
  return `${date} ${pick('hour')}:${pick('minute')}:${pick('second')}`
}

// 签到时间东八区展示：无时区串按 +08:00 解析；带 Z 的按台北墙钟格式化
export function formatCheckinLogDateTimeShanghai(input) {
  if (input == null || input === '') return '—'
  const d = parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return String(input)
  return _intlPartsShanghai(d, true)
}

// 签到流水「真实打点时刻」展示 东八区墙钟 。 后端 `checkinDate` 为归属日历日，时间恒为 00:00:00；`createTime`/`updateTime` 为实际写入时刻 见 checkin api §2.3
export function formatCheckinLogInstantShanghai(row) {
  if (!row || typeof row !== 'object') return '—'
  const raw = row.createTime ?? row.updateTime ?? row.checkinDate
  return formatCheckinLogDateTimeShanghai(raw)
}

// 论坛通用时间展示 东八区墙钟 ，适用于帖子/评论 createTime
export function formatForumDateTimeShanghai(input) {
  return formatCheckinLogDateTimeShanghai(input)
}

// 评论时间：今天 HH:mm，昨天/前天带相对日期，今年内带月日，跨年显示完整日期
export function formatCommentTimeShanghai(input, now = new Date()) {
  const d = parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return ''
  const time = formatChatBubbleTimeShanghai(d, now)
  const targetKey = shanghaiDayKey(d)
  const todayKey = shanghaiDayKey(now)
  if (targetKey === todayKey) return time
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  if (targetKey === shanghaiDayKey(yesterday)) return `昨天 ${time}`
  const beforeYesterday = new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000)
  if (targetKey === shanghaiDayKey(beforeYesterday)) return `前天 ${time}`
  const target = shanghaiYmdParts(d)
  const current = shanghaiYmdParts(now)
  if (!target || !current) return time
  if (target.y === current.y) return `${target.m}月${target.d}日 ${time}`
  return `${target.y}年${target.m}月${target.d}日 ${time}`
}

// 仅日期部分 东八区
export function formatForumDateOnlyShanghai(input) {
  return formatCheckinLogDateOnly(input)
}

// 东八区日历日键 yyyy MM dd，用于聊天日期分组
export function shanghaiDayKey(input) {
  const d = input instanceof Date ? input : parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return ''
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d)
}

function shanghaiYmdParts(input) {
  const d = input instanceof Date ? input : parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return null
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(d)
  return {
    y: Number(parts.find((p) => p.type === 'year')?.value),
    m: Number(parts.find((p) => p.type === 'month')?.value),
    d: Number(parts.find((p) => p.type === 'day')?.value),
  }
}

// 私信气泡时间：当天仅 HH:mm，其余日期同样只显示时刻 日期由分隔条承担
export function formatChatBubbleTimeShanghai(input, now = new Date()) {
  const d = parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Taipei',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(d)
  const hour = parts.find((p) => p.type === 'hour')?.value ?? '00'
  const minute = parts.find((p) => p.type === 'minute')?.value ?? '00'
  return `${hour}:${minute}`
}

// 会话列表时间：当天 HH:mm，昨天/日期分级展示
export function formatChatSessionTimeShanghai(input, now = new Date()) {
  const d = parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return ''
  const dayKey = shanghaiDayKey(d)
  const todayKey = shanghaiDayKey(now)
  if (dayKey === todayKey) return formatChatBubbleTimeShanghai(d, now)

  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  if (dayKey === shanghaiDayKey(yesterday)) return '昨天'

  const target = shanghaiYmdParts(d)
  const current = shanghaiYmdParts(now)
  if (!target || !current) return ''
  if (target.y === current.y) return `${target.m}月${target.d}日`
  return `${target.y}年${target.m}月${target.d}日`
}

// 聊天日期分隔条文案 微信风格
export function formatChatDateDividerShanghai(input, now = new Date()) {
  const d = parseForumDateTime(input)
  if (!d || Number.isNaN(d.getTime())) return ''
  const dayKey = shanghaiDayKey(d)
  const todayKey = shanghaiDayKey(now)
  if (dayKey === todayKey) return '今天'

  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  if (dayKey === shanghaiDayKey(yesterday)) return '昨天'

  const target = shanghaiYmdParts(d)
  const current = shanghaiYmdParts(now)
  if (!target || !current) return ''
  if (target.y === current.y) return `${target.m}月${target.d}日`
  return `${target.y}年${target.m}月${target.d}日`
}

// 将私信消息列表展开为「日期分隔 + 消息」时间线。 首条若是当天消息则不插「今天」分隔条；跨日时插入对应日期文案
export function buildChatMessageTimeline(messages, getCreateTime = (row) => row?.message?.createTime) {
  const rows = []
  let lastDayKey = ''
  const list = Array.isArray(messages) ? messages : []

  list.forEach((msg, index) => {
    const time = getCreateTime(msg)
    const dayKey = shanghaiDayKey(time)
    if (!dayKey || dayKey === lastDayKey) {
      rows.push({ type: 'message', key: `msg-${msg?.message?.id ?? index}`, msg })
      return
    }

    const label = formatChatDateDividerShanghai(time)
    const isFirst = lastDayKey === ''
    const showDivider = !(isFirst && label === '今天')
    if (showDivider) {
      rows.push({ type: 'date', key: `date-${dayKey}-${index}`, label, dayKey })
    }
    lastDayKey = dayKey
    rows.push({ type: 'message', key: `msg-${msg?.message?.id ?? index}`, msg })
  })

  return rows
}
