<template>
  <div class="article-ai-write">
    <el-popover
      v-model:visible="panelOpen"
      placement="bottom-end"
      :width="360"
      trigger="click"
      popper-class="article-ai-write-popper"
      @show="onPanelShow"
    >
      <template #reference>
        <button type="button" class="article-ai-write-trigger" :disabled="loading" title="AI 灵感">
          <el-icon class="article-ai-write-trigger-icon"><MagicStick /></el-icon>
        </button>
      </template>

      <div class="article-ai-write-panel">
        <button type="button" class="article-ai-write-close" aria-label="关闭" @click="panelOpen = false">×</button>
        <p class="article-ai-write-title">{{ panelTitle }}</p>
        <el-input
          v-model="prompt"
          type="textarea"
          :rows="3"
          maxlength="50"
          show-word-limit
          :placeholder="panelTitle === 'AI 灵感' ? '写下想表达的主题或角度' : '写下希望调整的方向'"
        />

        <div class="article-ai-write-actions">
          <el-button class="article-ai-write-generate" type="primary" :loading="loading" @click="runWrite">
            生成
          </el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup src="./ArticleAiWriteAssist.js"></script>

<style scoped lang="scss" src="./ArticleAiWriteAssist.scss"></style>
