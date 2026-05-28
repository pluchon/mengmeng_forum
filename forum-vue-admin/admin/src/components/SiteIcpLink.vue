<template>
  <p class="admin-icp-link-line" :class="{ 'admin-icp-link-line--auth': variant === 'auth' }">
    <a
      class="admin-icp-link-line__a"
      :href="SITE_ICP_URL"
      target="_blank"
      rel="noopener noreferrer"
    >{{ SITE_ICP_NUMBER }}</a>
  </p>
</template>

<script setup lang="ts">
import { SITE_ICP_NUMBER, SITE_ICP_URL } from '@/constants/site'

defineOptions({ name: 'SiteIcpLink' })

withDefaults(
  defineProps<{
    variant?: 'inline' | 'auth'
  }>(),
  { variant: 'inline' },
)
</script>

<style scoped lang="scss">
.admin-icp-link-line {
  margin: 12px 0 0;
  padding: 0;
  text-align: center;
  font-size: 12px;
  line-height: 1.5;
}

.admin-icp-link-line--auth {
  margin: 20px 0 0;
  padding: 14px 0 0;
  border-top: 1px dashed var(--color-border-2, #e5e6eb);
}

.admin-icp-link-line--auth .admin-icp-link-line__a {
  font-size: 15px;
  font-weight: 700;
  color: #444;
  letter-spacing: 0.02em;
}

.admin-icp-link-line__a {
  color: var(--color-text-3, #86909c);
  text-decoration: none;
  transition: color 0.2s;

  &:hover {
    color: rgb(var(--primary-6, 22, 93, 255));
  }
}
</style>
