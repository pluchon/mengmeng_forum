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
    class="app-dialog icon-confirm-dialog"
    @close="onCancel"
  >
    <div class="app-dialog__shell app-dialog__shell--center">
      <div class="app-dialog__icon" :class="danger ? 'app-dialog__icon--danger' : 'app-dialog__icon--warn'" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
          <path d="M12 2a10 10 0 1 0 .001 20.001A10 10 0 0 0 12 2zm1 5v6h-2V7h2zm0 8v2h-2v-2h2z" />
        </svg>
      </div>
      <h2 class="app-dialog__title">{{ title }}</h2>
      <p v-if="message" class="app-dialog__desc">{{ message }}</p>
      <div class="app-dialog__actions">
        <button type="button" class="app-dialog__btn app-dialog__btn--ghost" :disabled="loading" @click="onCancel">
          {{ cancelText }}
        </button>
        <button
          type="button"
          class="app-dialog__btn"
          :class="danger ? 'app-dialog__btn--danger' : 'app-dialog__btn--primary'"
          :disabled="loading"
          @click="onConfirm"
        >
          {{ confirmText }}
        </button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup src="./IconConfirmDialog.js"></script>
<style lang="scss" src="./dialog-tokens.scss"></style>
