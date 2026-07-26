<template>
  <section v-if="workspaceId" class="ai-workspace-history">
    <div class="ai-workspace-history-head">
      <span class="ai-workspace-history-title">创作版本</span>
      <el-button text size="small" :loading="loading" @click="loadVersions">刷新</el-button>
    </div>
    <div v-if="loading" class="ai-workspace-history-state">正在加载版本…</div>
    <div v-else-if="noPermission" class="ai-workspace-history-state">没有查看此工作区的权限</div>
    <div v-else-if="error" class="ai-workspace-history-state ai-workspace-history-state--error">{{ error }}</div>
    <div v-else-if="!versions.length" class="ai-workspace-history-state">暂无版本</div>
    <div v-else class="ai-workspace-history-list">
      <button
        v-for="version in versions"
        :key="version.id"
        type="button"
        class="ai-workspace-history-item"
        :class="{ 'is-selected': version.selected }"
        :disabled="selectingId === version.id"
        @click="selectVersion(version)"
      >
        <span>{{ version.artifactType }} · v{{ version.versionNo }}</span>
        <span>{{ version.selected ? '已采用' : '采用' }}</span>
      </button>
    </div>
  </section>
</template>

<script setup src="./AiWorkspaceHistory.js"></script>

<style scoped lang="scss" src="./AiWorkspaceHistory.scss"></style>
