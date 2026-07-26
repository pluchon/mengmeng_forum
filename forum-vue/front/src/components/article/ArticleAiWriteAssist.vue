<template>
  <button
    type="button"
    class="article-ai-write-trigger"
    :disabled="loading"
    title="AI 润色"
    aria-label="AI 润色"
    @click="runPolish"
  >
    <el-icon class="article-ai-write-trigger-icon"><MagicStick /></el-icon>
  </button>
</template>

<script setup src="./ArticleAiWriteAssist.js"></script>

<style scoped lang="scss" src="./ArticleAiWriteAssist.scss"></style>
