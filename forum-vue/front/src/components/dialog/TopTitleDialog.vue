<template>
  <el-dialog
    :model-value="modelValue"
    :width="width"
    append-to-body
    align-center
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="!loading"
    :z-index="zIndex"
    class="app-dialog top-title-dialog"
    @close="onCancel"
  >
    <div class="app-dialog__shell top-title-dialog__shell">
      <button
        v-if="showClose && !loading"
        type="button"
        class="top-title-dialog__close"
        aria-label="关闭"
        @click="onCancel"
      >
        ×
      </button>
      <div class="top-title-dialog__head">
        <h2 class="app-dialog__title app-dialog__title--top">{{ title }}</h2>
        <p v-if="hint" class="app-dialog__desc app-dialog__desc--left">{{ hint }}</p>
      </div>
      <div class="top-title-dialog__body">
        <slot />
      </div>
      <div v-if="showFooter" class="app-dialog__actions">
        <button
          v-if="showCancel"
          type="button"
          class="app-dialog__btn app-dialog__btn--ghost"
          :disabled="loading"
          @click="onCancel"
        >
          {{ cancelText }}
        </button>
        <button
          type="button"
          class="app-dialog__btn"
          :class="danger ? 'app-dialog__btn--danger' : 'app-dialog__btn--primary'"
          :disabled="loading || confirmDisabled"
          @click="onConfirm"
        >
          <slot name="confirm-icon" />
          {{ confirmText }}
        </button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup src="./TopTitleDialog.js"></script>
<style lang="scss" src="./dialog-tokens.scss"></style>
<style lang="scss" src="./TopTitleDialog.scss"></style>
