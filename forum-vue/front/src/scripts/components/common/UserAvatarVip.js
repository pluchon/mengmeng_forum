import { computed } from 'vue'
import { DEFAULT_AVATAR } from '@/utils/constants'

const props = defineProps({
  src: { type: String, default: '' },
  size: { type: Number, default: 32 },
  // 0 普通 1 PRO 2 MAX 仅用于是否展示会员环；环样式统一为七彩
  vipTier: { type: Number, default: 0 },
  // ISO 日期字符串或 null
  vipExpireAt: { type: String, default: null },
  // 是否展示会员七彩环 首页等场景可关闭
  showVipRing: { type: Boolean, default: true },
})

const defaultAvatar = DEFAULT_AVATAR

const effectiveTier = computed(() => {
  const t = Number(props.vipTier) || 0
  if (t <= 0) return 0
  if (!props.vipExpireAt) return t
  const exp = new Date(props.vipExpireAt).getTime()
  if (Number.isNaN(exp)) return t
  return Date.now() > exp ? 0 : t
})
