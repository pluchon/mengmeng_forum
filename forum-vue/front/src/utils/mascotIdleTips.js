/**
 * 看板娘舞台随机气泡：按本地时间（小时）选话术池。
 */

const POOLS = {
  dawn: [
    '天还没亮透呢，你也醒啦？',
    '早起的同学有早饭吃～',
    '再眯五分钟也行，但别睡过头哦',
  ],
  breakfast: [
    '这个点了，不干饭吗？',
    '早饭吃了没？空腹刷论坛对胃不好',
    '来杯热饮配刷帖，仪式感拉满',
  ],
  forenoon: [
    '上午好呀，今天也要元气满满',
    '码字累了就起来伸个懒腰',
    '有问题随时点我，陪伴助手在线',
  ],
  lunch: [
    '嚯，中午不困吗？',
    '干饭时间到，吃完再来聊',
    '午休前刷两条帖子刚刚好',
  ],
  afternoon: [
    '下午茶时间，摸鱼有理',
    '下午容易犯困，站起来走走吧',
    '写帖卡壳了可以找我聊聊',
  ],
  dinner: [
    '这个点了，不干饭吗？',
    '晚饭吃了吗？别饿着肚子逛论坛',
    '夕阳很美，拍一张发「旅行分享」呀',
  ],
  evening: [
    '晚上好～今天过得怎么样',
    '夜猫子模式？记得早点睡',
    '论坛夜生活才开始呢',
  ],
  late: [
    '这么晚还在呀，注意休息',
    '熬夜对皮肤不好，明天还要上班呢',
    '实在睡不着就来写篇生活日记吧',
  ],
  generic: [
    '戳我可以打开陪伴助手',
    '今天想写点什么，还是问问站点功能？',
    '相关帖子我会帮你找哦',
  ],
}

function hourBucket(h) {
  if (h >= 5 && h < 8) return 'dawn'
  if (h >= 8 && h < 10) return 'breakfast'
  if (h >= 10 && h < 11) return 'forenoon'
  if (h >= 11 && h < 14) return 'lunch'
  if (h >= 14 && h < 17) return 'afternoon'
  if (h >= 17 && h < 19) return 'dinner'
  if (h >= 19 && h < 23) return 'evening'
  if (h >= 23 || h < 5) return 'late'
  return 'generic'
}

export function pickMascotIdlePhrase(date = new Date()) {
  const h = date.getHours()
  const bucket = hourBucket(h)
  const pool = POOLS[bucket] || POOLS.generic
  const extra = Math.random() < 0.25 ? POOLS.generic : []
  const merged = [...pool, ...extra]
  return merged[Math.floor(Math.random() * merged.length)]
}
