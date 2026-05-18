<template>
  <div class="mascot-panel-input" :class="{ 'mascot-panel-input--drawing': mode === 'drawing' }">
    <p v-if="estimatePoints != null" class="mascot-estimate-hint">
      预估消耗约 <strong>{{ estimatePoints }}</strong> 积分
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
            <img :src="drawingIcon" alt="" class="mascot-llm-ico">
            <span>{{ imageQuality === 'premium' ? '进阶 · gpt-image-2' : '普通 · z-image-turbo' }}</span>
            <span class="mascot-llm-caret">▾</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="normal">
                <span class="mascot-llm-row">
                  <img :src="iconQwen" alt="" class="mascot-llm-ico">
                  <span>普通 · z-image-turbo（10 积分/张）</span>
                </span>
              </el-dropdown-item>
              <el-dropdown-item command="premium" :disabled="!vip">
                <span class="mascot-llm-row">
                  <img :src="iconOpenai" alt="" class="mascot-llm-ico">
                  <span>进阶 · gpt-image-2（10 积分/张）</span>
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
            <img v-if="activeOption?.icon" :src="activeOption.icon" alt="" class="mascot-llm-ico">
            <span>{{ activeOption?.label ?? '模型' }}</span>
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
      </div>
      <span class="mascot-counter" aria-live="polite">{{ charCount }} / 2000</span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { Loading, Promotion } from '@element-plus/icons-vue'
import iconQwen from '@/assets/svg/qwen-color.svg'
import iconOpenai from '@/assets/svg/openai.svg'

const props = defineProps({
  modelValue: { type: String, default: '' },
  llm: { type: String, default: 'qwen-flash' },
  imageQuality: { type: String, default: 'normal' },
  options: { type: Array, default: () => [] },
  mode: { type: String, default: 'writing' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '说点什么…' },
  vip: { type: Boolean, default: false },
  showModelPicker: { type: Boolean, default: true },
  estimatePoints: { type: Number, default: null },
  estimateLoading: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'update:llm',
  'update:imageQuality',
  'send',
  'clear',
])

const textareaRef = ref(null)
const charCount = computed(() => (props.modelValue || '').length)
const activeOption = computed(() => props.options.find(o => o.id === props.llm))
const drawingIcon = computed(() => (props.imageQuality === 'premium' ? iconOpenai : iconQwen))

function resizeTextarea() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 100)}px`
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
  background: linear-gradient(165deg, rgba(72, 56, 140, 0.55) 0%, rgba(42, 32, 88, 0.72) 100%);
  border-top: 1px solid rgba(167, 139, 250, 0.22);
}

.mascot-estimate-hint {
  margin: 0;
  font-size: 11px;
  color: rgba(220, 210, 255, 0.75);
}

.mascot-estimate-hint strong {
  color: #e9b4ff;
  font-weight: 600;
}

.mascot-input-shell {
  position: relative;
  border-radius: 14px;
  background: rgba(30, 22, 62, 0.65);
  border: 1px solid rgba(167, 139, 250, 0.28);
  padding: 10px 44px 10px 12px;
  min-height: 44px;
}

.mascot-input-box {
  display: block;
  width: 100%;
  min-height: 24px;
  max-height: 100px;
  padding: 0;
  border: none;
  background: transparent;
  color: #f3eeff;
  font-size: 13px;
  line-height: 1.55;
  resize: none;
  outline: none;
  font-family: inherit;
}

.mascot-input-box::placeholder {
  color: rgba(200, 190, 235, 0.45);
}

.mascot-send-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(145deg, #c084fc, #7c3aed);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(124, 58, 237, 0.45);
}

.mascot-send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.mascot-input-divider {
  height: 1px;
  background: rgba(167, 139, 250, 0.22);
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

.mascot-tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: rgba(210, 200, 245, 0.75);
  cursor: pointer;
  font-size: 14px;
}

.mascot-tool-btn:hover:not(:disabled) {
  background: rgba(167, 139, 250, 0.12);
}

.mascot-tool-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.mascot-model-selector {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid rgba(167, 139, 250, 0.35);
  font-size: 11px;
  color: rgba(220, 210, 255, 0.88);
  cursor: pointer;
  background: rgba(40, 30, 80, 0.45);
}

.mascot-llm-caret {
  opacity: 0.65;
  font-size: 10px;
}

.mascot-llm-ico {
  width: 16px;
  height: 16px;
  object-fit: contain;
}

.mascot-llm-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
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
  color: rgba(200, 190, 235, 0.5);
}
</style>
