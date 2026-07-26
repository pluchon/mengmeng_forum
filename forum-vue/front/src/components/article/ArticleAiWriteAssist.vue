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
        <button type="button" class="article-ai-write-trigger" :disabled="loading">
          <el-icon class="article-ai-write-trigger-icon"><MagicStick /></el-icon>
          AI 写作
        </button>
      </template>

      <div class="article-ai-write-panel">
        <p class="article-ai-write-title">AI 辅助写作</p>
        <p class="article-ai-write-hint">
          将按当前{{ editorMode === 'markdown' ? ' Markdown ' : '富文本' }}模式生成正文并填入编辑器
        </p>

        <label class="article-ai-write-label">模型</label>
        <el-select v-model="selectedRoute" class="article-ai-write-select" size="small">
          <el-option
            v-for="opt in llmOptions"
            :key="opt.id"
            :label="opt.label"
            :value="opt.id"
          >
            <div class="article-ai-write-opt">
              <img :src="opt.icon" alt="" class="article-ai-write-opt-icon" />
              <span>{{ opt.label }}</span>
              <span class="article-ai-write-opt-hint">{{ opt.hint }}</span>
            </div>
          </el-option>
        </el-select>

        <label class="article-ai-write-label">写作要求</label>
        <el-input
          v-model="prompt"
          type="textarea"
          :rows="4"
          maxlength="800"
          show-word-limit
          placeholder="例如：写一篇关于 Spring Boot 自动配置原理的教程，分章节、语气友好"
        />

        <div class="article-ai-write-actions">
          <el-button size="small" @click="panelOpen = false">取消</el-button>
          <el-button type="primary" size="small" :loading="loading" @click="runWrite">
            生成并填入
          </el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup src="./ArticleAiWriteAssist.js"></script>

<style scoped lang="scss" src="./ArticleAiWriteAssist.scss"></style>
