<template>
  <div
    class="particle-hero"
    :class="{
      'particle-hero--auth': isAuthLayout,
      'particle-hero--embedded': embedded,
    }"
  >
    <canvas ref="canvasClassicRef" v-show="!isAuthLayout" class="particle-canvas" />
    <canvas ref="canvasGlRef" v-show="isAuthLayout" class="particle-canvas" />
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import { useParticleSea } from '@scripts/components/common/ParticleSea'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  hostRef: { type: Object, default: null },
})

const { canvasClassicRef, canvasGlRef, isAuthLayout } = useParticleSea({
  embedded: toRef(props, 'embedded'),
  hostRef: toRef(props, 'hostRef'),
})
</script>

<style scoped>
.particle-hero {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: radial-gradient(circle at 50% 40%, #fff5f8 0%, #ffeef2 55%, #fff7f9 100%);
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.particle-hero--embedded {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 0;
}

.particle-hero--auth {
  background: linear-gradient(165deg, #d4dae4 0%, #e1e6ee 45%, #e8ecf2 100%);
}

.particle-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: auto;
}

.particle-hero--embedded .particle-canvas {
  pointer-events: none;
}</style>
