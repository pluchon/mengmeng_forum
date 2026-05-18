<template>
  <div class="editor-page">
    <el-card class="editor-card animate-fade-up audit-card" shadow="never">
      <div class="audit-inner">
        <el-icon class="audit-spin" :size="48" color="var(--el-color-primary)"><LoadingIcon /></el-icon>
        <h2 class="audit-title">内容审核中</h2>
        <p class="audit-desc">帖子已提交审核，系统正在处理正文与图片。请稍候，页面会自动跳转。</p>
        <p v-if="taskIdHint" class="audit-meta">任务编号：{{ taskIdHint }}</p>
        <p class="audit-meta">当前状态：{{ statusText }}</p>
        <el-button type="primary" plain @click="$router.replace('/')">
          <el-icon class="el-icon--left"><House /></el-icon>
          返回主页
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { useArticleAuditPending } from '@scripts/views/ArticleAuditPending'

import { House } from '@element-plus/icons-vue'

const { LoadingIcon, statusText, taskIdHint } = useArticleAuditPending()
</script>

<style scoped>
.audit-card {
  max-width: 520px;
  margin: 48px auto;
}
.audit-inner {
  text-align: center;
  padding: 24px 12px 8px;
}
.audit-title {
  margin: 16px 0 8px;
  font-size: 20px;
}
.audit-desc {
  color: #4e5969;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 12px;
}
.audit-meta {
  font-size: 12px;
  color: #86909c;
  margin: 6px 0;
}
.audit-spin {
  animation: audit-rotate 1.1s linear infinite;
}
@keyframes audit-rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>

<style scoped src="@/assets/styles/editor.css"></style>
