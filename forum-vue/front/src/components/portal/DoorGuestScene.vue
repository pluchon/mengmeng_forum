<template>
  <div ref="rootRef" class="door-guest-scene" aria-hidden="true">
    <div class="door-guest-scene__bg" :style="{ backgroundImage: `url(${backgroundUrl})` }" />
    <canvas ref="canvasRef" class="door-guest-scene__canvas" />
  </div>
</template>

<script setup src="@scripts/components/portal/DoorGuestScene.js"></script>

<style scoped lang="scss" src="./DoorGuestScene.scss"></style>
