<template>
  <footer class="site-icp-footer" role="contentinfo">
    <SiteIcpLink variant="footer" />
  </footer>
</template>

<script setup>
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
</script>

<style scoped>
.site-icp-footer {
  flex-shrink: 0;
  background: #f5f5f5;
  border-top: 1px solid #e8e8e8;
  text-align: center;
}
</style>
