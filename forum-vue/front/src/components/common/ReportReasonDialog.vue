<template>
  <TopTitleDialog
    :model-value="visible"
    :title="title"
    hint="举报将依据社区规范独立审核，选择理由不会直接决定审核结果。"
    confirm-text="提交举报"
    :show-close="false"
    :loading="submitting"
    :confirm-disabled="!canSubmit"
    width="min(400px, 92vw)"
    :z-index="6500"
    @update:model-value="onVisible"
    @confirm="submitReason"
    @cancel="closeDialog"
  >
    <template #confirm-icon>
      <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
        <path d="M6 3v18l1-.5V14l3-1 4 1.5 4-2V5l-4 2-4-1.5L7 7V3H6z" />
      </svg>
    </template>
    <div class="app-dialog__chip-grid" role="radiogroup" aria-label="举报理由">
      <div
        v-for="(row, rowIndex) in reasonRows"
        :key="'row-' + rowIndex"
        class="app-dialog__chip-row"
      >
        <button
          v-for="option in row"
          :key="option.value"
          type="button"
          class="app-dialog__chip"
          :class="{ 'is-active': selectedReason === option.value }"
          :disabled="submitting"
          @click="selectedReason = option.value"
        >
          {{ option.label }}
        </button>
      </div>
    </div>
    <el-input
      v-if="selectedReason === 'OTHER'"
      v-model="customReason"
      class="report-reason-dialog__input"
      type="textarea"
      :rows="3"
      maxlength="200"
      show-word-limit
      resize="none"
      placeholder="请填写 5～200 字的具体理由"
      :disabled="submitting"
    />
  </TopTitleDialog>
</template>

<script setup src="./ReportReasonDialog.js"></script>
<style lang="scss" src="../dialog/dialog-tokens.scss"></style>
<style lang="scss" src="./ReportReasonDialog.scss"></style>
