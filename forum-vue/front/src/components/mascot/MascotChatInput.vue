<template>
  <div class="mascot-panel-input">
    <div class="mascot-input-row">
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
      </div>
      <button
        type="button"
        class="mascot-send-btn"
        :disabled="disabled || loading || !modelValue.trim()"
        aria-label="发送"
        @click="emit('send')"
      >
        <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
        <el-icon v-else><Promotion /></el-icon>
        <span>发送</span>
      </button>
    </div>

    <div class="mascot-input-divider" />

    <div class="mascot-input-footer">
      <div class="mascot-input-footer-left">
        <el-dropdown
          trigger="click"
          :disabled="imageGenerating"
          @command="(c) => emit('update:imageQuality', c)"
        >
          <span
            class="mascot-model-selector"
            :class="{ 'is-disabled': imageGenerating }"
            role="button"
            tabindex="0"
          >
            <span class="mascot-model-selector__label">生图模型</span>
            <img v-if="activeImageOption?.icon" :src="activeImageOption.icon" alt="" class="mascot-model-selector__icon">
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

        <div class="mascot-context-control">
          <span class="mascot-context-control__label">上下文</span>
          <span class="mascot-context-control__track" aria-hidden="true">
            <span class="mascot-context-control__progress" :style="{ width: `${contextPercent}%` }" />
          </span>
          <span class="mascot-context-control__count">{{ contextUsageLabel }}</span>
          <button
            type="button"
            class="mascot-context-control__compress"
            :disabled="disabled || loading || contextCompressing || !contextAvailable"
            title="压缩上下文"
            aria-label="压缩上下文"
            @click="emit('compress-context')"
          >
            <span
              class="mascot-context-control__compact-icon"
              :class="{ 'is-loading': contextCompressing }"
              aria-hidden="true"
            />
          </button>
          <button
            type="button"
            class="mascot-context-control__memory"
            :disabled="disabled || loading"
            title="查看记忆"
            aria-label="查看记忆"
            @click="emit('open-memory')"
          >
            <span class="mascot-context-control__memory-icon" aria-hidden="true" />
          </button>
        </div>
      </div>
      <span class="mascot-generation-hint">{{ generationHint }}</span>
      <span class="mascot-counter" aria-live="polite">{{ charCount }} / 2000</span>
    </div>
  </div>
</template>

<script setup src="@/scripts/components/mascot/MascotChatInput.js"></script>

<style scoped src="./MascotChatInput.css"></style>
