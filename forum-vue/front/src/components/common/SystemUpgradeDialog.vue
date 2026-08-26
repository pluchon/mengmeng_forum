<template>
  <el-dialog
    v-model="visible"
    width="440px"
    append-to-body
    align-center
    :show-close="true"
    class="system-upgrade-dialog"
  >
    <h2 class="system-upgrade-dialog__title">系统升级中，请耐心等待......</h2>
    <img
      class="system-upgrade-dialog__img"
      :src="updateIllustration"
      alt="系统升级中"
      draggable="false"
    >
  </el-dialog>
</template>

<script setup src="./SystemUpgradeDialog.js"></script>
<style src="./SystemUpgradeDialog.css"></style>
