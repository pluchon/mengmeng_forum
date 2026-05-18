<template>
  <div class="admin-vip-avatar" :style="{ '--ring-size': `${size}px` }">
    <div v-if="showRing" class="admin-vip-avatar__ring">
      <a-avatar :size="size">
        <img v-if="src" :src="src" alt="">
        <span v-else>{{ fallbackText }}</span>
      </a-avatar>
    </div>
    <a-avatar v-else :size="size">
      <img v-if="src" :src="src" alt="">
      <span v-else>{{ fallbackText }}</span>
    </a-avatar>
  </div>
</template>

<script setup lang="ts">
import { isVipActive } from '@/utils/vip'

const props = withDefaults(
  defineProps<{
    src?: string | null
    size?: number
    vipTier?: number | null
    vipExpireAt?: string | null
    fallbackText?: string
  }>(),
  {
    src: '',
    size: 32,
    vipTier: 0,
    vipExpireAt: null,
    fallbackText: '?'
  }
)

const showRing = computed(() =>
  isVipActive(props.vipTier, props.vipExpireAt)
)
</script>

<style scoped lang="scss">
.admin-vip-avatar {
  display: inline-flex;
  line-height: 0;
}

.admin-vip-avatar__ring {
  padding: 3px;
  border-radius: 50%;
  background: conic-gradient(
    from -45deg,
    #4285f4,
    #34a853,
    #fbbc05,
    #ea4335,
    #ab47bc,
    #4285f4
  );
  box-shadow: 0 0 0 1px rgb(255 255 255 / 45%) inset;

  :deep(.arco-avatar) {
    border: 2px solid #fff;
  }
}
</style>
