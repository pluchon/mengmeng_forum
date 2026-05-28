<template>
  <footer class="admin-icp-bar" role="contentinfo">
    <a
      class="admin-icp-link"
      :href="SITE_ICP_URL"
      target="_blank"
      rel="noopener noreferrer"
    >{{ SITE_ICP_NUMBER }}</a>
  </footer>
</template>

<script setup lang="ts">
import { SITE_ICP_NUMBER, SITE_ICP_URL } from '@/constants/site'

defineOptions({ name: 'SiteIcpBar' })
</script>

<style scoped lang="scss">
.admin-icp-bar {
  flex-shrink: 0;
  padding: 12px 16px;
  text-align: center;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-3, #86909c);
  border-top: 1px solid var(--color-border-2, #e5e6eb);
  background: var(--color-bg-2, #f7f8fa);
}

.admin-icp-link {
  color: #666;
  font-weight: 600;
  text-decoration: none;
  letter-spacing: 0.02em;

  &:hover {
    color: rgb(var(--primary-6, 22, 93, 255));
  }
}
</style>
