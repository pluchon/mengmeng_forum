<template>
  <div
    class="circular-gallery"
    role="region"
    aria-label="小萌动作轮播"
  >
    <div
      ref="containerRef"
      class="circular-gallery__stage"
      :class="{ 'is-hidden': failed }"
    />

    <div v-if="failed" class="circular-gallery__fallback">
      <article v-for="item in items" :key="item.text" class="circular-gallery__fallback-card">
        <span class="circular-gallery__fallback-frame" :style="fallbackStyle(item)" />
        <strong>{{ item.text }}</strong>
      </article>
    </div>

    <div class="circular-gallery__guide" aria-hidden="true">
      <span>DRAG TO EXPLORE</span>
      <i />
      <small>{{ items.length }} 个动作</small>
    </div>

    <ul class="circular-gallery__a11y">
      <li v-for="item in items" :key="`a11y-${item.text}`">{{ item.text }}</li>
    </ul>
  </div>
</template>

<script setup src="@scripts/components/portal/CircularGallery.js"></script>

<style scoped lang="scss" src="./CircularGallery.scss"></style>
