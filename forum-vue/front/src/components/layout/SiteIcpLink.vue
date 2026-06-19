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

<script setup src="./SiteIcpLink.js"></script>

<style scoped src="./SiteIcpLink.css"></style>
