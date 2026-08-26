<template>
  <TopTitleDialog
    :model-value="visible"
    :title="dialogTitle"
    confirm-text="确认上传"
    cancel-text="取消"
    :show-close="showClose"
    :show-footer="showFooter"
    :loading="uploading"
    width="min(920px, 94vw)"
    :z-index="6500"
    @update:model-value="onVisibleChange"
    @confirm="confirmCrop"
    @cancel="closeDialog"
  >
    <div
      v-if="mode === 'crop'"
      ref="viewportRef"
      class="profile-banner-dialog__crop-viewport"
    >
      <img
        v-if="imageSrc && naturalWidth"
        class="profile-banner-dialog__crop-image"
        :src="imageSrc"
        :style="imageDisplayStyle"
        alt=""
        draggable="false"
      >
      <div
        v-if="cropW"
        class="profile-banner-dialog__crop-box"
        :style="cropBoxStyle"
        @pointerdown="onCropPointerDown"
        @pointermove="onCropPointerMove"
        @pointerup="onCropPointerUp"
        @pointercancel="onCropPointerUp"
      >
        <span class="profile-banner-dialog__crop-grid" aria-hidden="true" />
      </div>
    </div>
    <div v-else class="profile-banner-dialog__preview-wrap">
      <img v-if="imageSrc" class="profile-banner-dialog__preview" :src="imageSrc" alt="个人主页背景">
    </div>
  </TopTitleDialog>
</template>

<script setup src="./ProfileBannerDialog.js"></script>
<style lang="scss" src="../dialog/dialog-tokens.scss"></style>
<style scoped lang="scss" src="./ProfileBannerDialog.scss"></style>
