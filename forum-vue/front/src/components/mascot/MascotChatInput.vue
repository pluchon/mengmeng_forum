<template>
  <div class="mascot-panel-input" :class="{ 'mascot-panel-input--drawing': mode === 'drawing' }">
    <p v-if="estimateHint" class="mascot-estimate-hint">
      {{ estimateHint }}
      <span v-if="estimateLoading" class="mascot-estimate-hint__loading">…</span>
    </p>

    <div class="mascot-input-shell">
      <textarea
        ref="textareaRef"
        :value="modelValue"
        class="mascot-input-box"
        :placeholder="placeholder"
        rows="1"
        maxlength="2000"
        :disabled="disabled"
        @input="onInput"
        @keydown.enter.exact.prevent="onEnter"
      />
      <button
        type="button"
        class="mascot-send-btn"
        :disabled="disabled || loading || !modelValue.trim()"
        aria-label="发送"
        @click="emit('send')"
      >
        <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
        <el-icon v-else><Promotion /></el-icon>
      </button>
    </div>

    <div class="mascot-input-divider" />

    <div class="mascot-input-footer">
      <div class="mascot-input-footer-left">
        <el-dropdown
          v-if="mode === 'drawing'"
          trigger="click"
          @command="(c) => emit('update:imageQuality', c)"
        >
          <span class="mascot-model-selector" role="button" tabindex="0">
            <img :src="activeImageOption?.icon" alt="" class="mascot-llm-ico">
            <span class="mascot-llm-meta mascot-llm-meta--inline">
              <span class="mascot-llm-txt">{{ activeImageOption?.label ?? '生图' }}</span>
              <span v-if="activeImageOption?.hint" class="mascot-llm-hint">{{ activeImageOption.hint }}</span>
            </span>
            <span class="mascot-llm-caret">▾</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="o in imageOptions"
                :key="o.id"
                :command="o.id"
                :disabled="o.vipOnly && !vip"
              >
                <span class="mascot-llm-row">
                  <img :src="o.icon" alt="" class="mascot-llm-ico">
                  <span class="mascot-llm-meta mascot-llm-meta--menu">
                    <span class="mascot-llm-txt">{{ o.label }}</span>
                    <span class="mascot-llm-hint">{{ o.hint }}</span>
                  </span>
                </span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown
          v-else-if="showModelPicker"
          trigger="click"
          @command="(c) => emit('update:llm', c)"
        >
          <span class="mascot-model-selector" role="button" tabindex="0">
            <img v-if="activeTextOption?.icon" :src="activeTextOption.icon" alt="" class="mascot-llm-ico">
            <span class="mascot-llm-meta mascot-llm-meta--inline">
              <span class="mascot-llm-txt">{{ activeTextOption?.label ?? '模型' }}</span>
              <span v-if="activeTextOption?.hint" class="mascot-llm-hint">{{ activeTextOption.hint }}</span>
            </span>
            <span class="mascot-llm-caret">▾</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="o in options"
                :key="o.id"
                :command="o.id"
              >
                <span class="mascot-llm-row">
                  <img :src="o.icon" alt="" class="mascot-llm-ico">
                  <span class="mascot-llm-meta mascot-llm-meta--menu">
                    <span class="mascot-llm-txt">{{ o.label }}</span>
                    <span v-if="o.hint" class="mascot-llm-hint">{{ o.hint }}</span>
                  </span>
                </span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button
          v-if="showPointsPayButton"
          type="button"
          class="mascot-points-pay-btn"
          :class="{ 'is-active': pointsPayActive }"
          @click="emit('toggle-points-pay')"
        >
          {{ pointsPayActive ? '已用萌币' : '使用萌币积分' }}
        </button>
      </div>
      <span class="mascot-counter" aria-live="polite">{{ charCount }} / 2000</span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { Loading, Promotion } from '@element-plus/icons-vue'
import { findImageQualityOption, findTextLlmOption } from '@/constants/aiModels'

const props = defineProps({
  modelValue: { type: String, default: '' },
  llm: { type: String, default: 'qwen-flash' },
  imageQuality: { type: String, default: 'normal' },
  options: { type: Array, default: () => [] },
  imageOptions: { type: Array, default: () => [] },
  mode: { type: String, default: 'chat' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '说点什么…' },
  vip: { type: Boolean, default: false },
  showModelPicker: { type: Boolean, default: true },
  estimatePoints: { type: Number, default: null },
  estimateLoading: { type: Boolean, default: false },
  estimateHint: { type: String, default: '' },
  showPointsPayButton: { type: Boolean, default: false },
  pointsPayActive: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'update:llm',
  'update:imageQuality',
  'send',
  'clear',
  'toggle-points-pay',
])

const textareaRef = ref(null)
const charCount = computed(() => (props.modelValue || '').length)
const activeTextOption = computed(() => findTextLlmOption(props.llm) || props.options.find(o => o.id === props.llm))
const activeImageOption = computed(() => findImageQualityOption(props.imageQuality) || props.imageOptions[0])

function resizeTextarea() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 120)}px`
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
  nextTick(resizeTextarea)
}

function onEnter() {
  if (!props.loading && props.modelValue.trim()) {
    emit('send')
  }
}

watch(() => props.modelValue, () => nextTick(resizeTextarea))
</script>

<style scoped>
.mascot-panel-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px 12px;
  background: var(--el-bg-color, #fff);
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  flex-shrink: 0;
}

.mascot-estimate-hint {
  margin: 0;
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
}

.mascot-estimate-hint strong {
  color: var(--el-text-color-primary, #303133);
  font-weight: 600;
}

.mascot-input-shell {
  position: relative;
  border-radius: 12px;
  background: var(--el-fill-color-blank, #fff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  padding: 8px 44px 8px 12px;
  min-height: 40px;
}

.mascot-input-box {
  display: block;
  width: 100%;
  min-height: 22px;
  max-height: 120px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--el-text-color-primary, #303133);
  font-size: 13px;
  line-height: 1.55;
  resize: none;
  overflow-y: hidden;
  outline: none;
  font-family: inherit;
}

.mascot-input-box::placeholder {
  color: var(--el-text-color-placeholder, #a8abb2);
}

.mascot-send-btn {
  position: absolute;
  right: 6px;
  bottom: 6px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mascot-send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.mascot-input-divider {
  height: 1px;
  background: var(--el-border-color-lighter, #ebeef5);
  margin: 0 2px;
}

.mascot-input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
}

.mascot-input-footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mascot-model-selector {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  font-size: 11px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  background: var(--el-fill-color-light, #f5f7fa);
  max-width: min(100%, 280px);
}

.mascot-llm-caret {
  opacity: 0.65;
  font-size: 10px;
  flex-shrink: 0;
}

.mascot-llm-ico {
  width: 16px;
  height: 16px;
  object-fit: contain;
  flex-shrink: 0;
}

.mascot-llm-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mascot-llm-meta--inline {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  min-width: 0;
}

.mascot-llm-meta--menu {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.mascot-llm-txt {
  font-weight: 500;
}

.mascot-llm-hint {
  font-size: 10px;
  opacity: 0.72;
}

.mascot-counter {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #a8abb2);
}

.mascot-points-pay-btn {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid rgba(255, 36, 66, 0.45);
  background: #fff;
  color: #ff2442;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.mascot-points-pay-btn.is-active {
  background: rgba(255, 36, 66, 0.1);
  border-color: #ff2442;
}
</style>
