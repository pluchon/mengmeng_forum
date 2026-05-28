<template>
  <p
    class="site-icp-link"
    :class="{
      'site-icp-link--footer': variant === 'footer',
      'site-icp-link--auth': variant === 'auth',
    }"
  >
    <a
      class="site-icp-link__a"
      :href="SITE_ICP_URL"
      target="_blank"
      rel="noopener noreferrer"
    >{{ SITE_ICP_NUMBER }}</a>
  </p>
</template>

<script setup>
import { SITE_ICP_NUMBER, SITE_ICP_URL } from '@/constants/site'

defineProps({
  variant: {
    type: String,
    default: 'inline',
  },
})
</script>

<style scoped>
.site-icp-link {
  margin: 12px 0 0;
  padding: 0;
  text-align: center;
  font-size: 12px;
  line-height: 1.5;
}

.site-icp-link--auth {
  margin: 16px 0 4px;
  padding: 10px 0 0;
  border-top: 1px dashed #e8e8e8;
}

.site-icp-link--auth .site-icp-link__a {
  font-size: 15px;
  font-weight: 700;
  color: #444;
  letter-spacing: 0.02em;
}

.site-icp-link--footer {
  margin: 0;
  padding: 14px 16px;
  font-size: 13px;
}

.site-icp-link__a {
  color: #888;
  text-decoration: none;
  transition: color 0.2s;
}

.site-icp-link__a:hover {
  color: var(--primary-red, #ff2442);
}

.site-icp-link--footer .site-icp-link__a {
  color: #666;
  font-weight: 500;
}
</style>
