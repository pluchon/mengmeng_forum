<template>
  <span v-if="displayRegion" class="ip-region-label">IP属地：{{ displayRegion }}</span>
</template>

<script setup src="@scripts/components/common/IpRegionLabel.js"></script>

<style scoped src="./IpRegionLabel.css"></style>
