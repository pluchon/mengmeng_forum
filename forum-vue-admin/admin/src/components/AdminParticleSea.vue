<template>
  <div class="admin-particle-hero">
    <canvas ref="canvasRef" class="admin-particle-canvas" />
  </div>
</template>

<script setup lang="ts">
import { createAuthWallThree } from '@/utils/authWallThree'

defineOptions({ name: 'AdminParticleSea' })

const canvasRef = useTemplateRef<HTMLCanvasElement>('canvasRef')
let api: { stop: () => void } | null = null

onMounted(async () => {
  if (!canvasRef.value)
    return
  try {
    api = await createAuthWallThree(canvasRef.value)
  } catch (err) {
    console.warn('AdminParticleSea init failed', err)
  }
})

onUnmounted(() => {
  api?.stop()
  api = null
})
</script>

<style scoped>
.admin-particle-hero {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: linear-gradient(165deg, #d4dae4 0%, #e1e6ee 45%, #e8ecf2 100%);
}

.admin-particle-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: auto;
}
</style>
