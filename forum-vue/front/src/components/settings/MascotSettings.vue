<template>
  <section class="mascot-settings-panel">
    <label class="mascot-settings-toggle">
      <span>显示 / 隐藏看板娘</span>
      <el-switch
        :model-value="mascotUi.visible"
        aria-label="显示或隐藏看板娘"
        @change="mascotUi.setVisible"
      />
    </label>
  </section>
</template>

<script setup src="@scripts/components/settings/MascotSettings.js"></script>

<style scoped src="@/assets/styles/mascot-settings.css"></style>
