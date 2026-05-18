<template>
  <a-card title="用户建议 / 投诉" :bordered="false" class="g-base-card user-feedback-card">
    <a-empty description="功能开发中，敬请期待" />
  </a-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'UserFeedbackCard' })
</script>

<style lang="scss" scoped>
.user-feedback-card {
  margin-top: 14px;
}
</style>
