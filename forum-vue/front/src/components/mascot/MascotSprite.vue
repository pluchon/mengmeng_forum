<template>
  <button
    type="button"
    class="mascot-sprite"
    :class="{ 'is-ready': ready, 'is-paused': paused }"
    :style="rootStyle"
    title="打开小萌对话"
    aria-label="打开小萌对话"
    @click="emit('activate')"
    @pointerenter="emit('hover-change', true)"
    @pointerleave="emit('hover-change', false)"
  >
    <span
      v-if="ready"
      class="mascot-sprite__frame"
      :style="frameStyle"
      aria-hidden="true"
    />
    <img
      v-else
      class="mascot-sprite__fallback"
      src="/logo_small.png"
      alt=""
      draggable="false"
    >
    <span
      v-if="tipText"
      class="mascot-sprite__tip"
      role="status"
      aria-live="polite"
    >{{ tipText }}</span>
  </button>
</template>

<script setup src="@scripts/components/mascot/MascotSprite.js"></script>

<style scoped src="./MascotSprite.scss"></style>
