<template>
  <div class="user-avatar-vip">
    <div v-if="showVipRing && effectiveTier > 0" class="vip-chrome-ring">
      <div class="vip-chrome-inner">
        <el-avatar :size="size" :src="src || defaultAvatar" />
      </div>
    </div>
    <el-avatar v-else :size="size" :src="src || defaultAvatar" />
  </div>
</template>

<script setup>
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
</script>

<style scoped>
.user-avatar-vip {
  display: inline-flex;
  align-items: center;
  line-height: 0;
}

/* Google 系分段色环：conic gradient + 内圈留白形成环宽 */
.vip-chrome-ring {
  flex-shrink: 0;
  border-radius: 50%;
  padding: 3px;
  background: conic-gradient(
    from -45deg,
    #4285f4,
    #34a853,
    #fbbc05,
    #ea4335,
    #ab47bc,
    #4285f4
  );
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.45) inset;
}

.vip-chrome-inner {
  border-radius: 50%;
  padding: 2px;
  background: #fff;
  line-height: 0;
}

.vip-chrome-inner :deep(.el-avatar) {
  display: block;
}</style>
