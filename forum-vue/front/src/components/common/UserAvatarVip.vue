<template>
  <div class="user-avatar-vip">
    <el-avatar :size="size" :src="src || defaultAvatar" />
  </div>
</template>

<script setup>
import { DEFAULT_AVATAR } from '@/utils/constants'

defineProps({
  src: { type: String, default: '' },
  size: { type: Number, default: 32 },
})

const defaultAvatar = DEFAULT_AVATAR
</script>

<style scoped>
.user-avatar-vip {
  display: inline-flex;
  align-items: center;
  line-height: 0;
}
</style>
