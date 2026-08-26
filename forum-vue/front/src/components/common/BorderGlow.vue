<template>
  <div
    ref="cardRef"
    class="border-glow"
    :class="className"
    :style="rootStyle"
    @pointermove="handlePointerMove"
    @pointerenter="handlePointerEnter"
    @pointerleave="handlePointerLeave"
  >
    <div class="border-glow__border" :style="borderLayerStyle" />
    <div class="border-glow__fill" :style="fillLayerStyle" />
    <span class="border-glow__outer" :style="glowWrapStyle">
      <span class="border-glow__outer-inner" :style="glowInnerStyle" />
    </span>
    <div class="border-glow__content">
      <slot />
    </div>
  </div>
</template>

<script setup src="./BorderGlow.js"></script>

<style lang="scss" scoped src="./BorderGlow.scss"></style>
