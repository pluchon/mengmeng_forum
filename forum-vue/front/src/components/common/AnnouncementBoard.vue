<template>
  <el-dialog
    v-model="visible"
    width="min(1080px, 94vw)"
    class="pro-announcement-dialog activity-center-dialog"
    :show-close="false"
    destroy-on-close
    append-to-body
  >
    <button type="button" class="activity-center-close" aria-label="关闭" @click="visible = false">
      <el-icon :size="18"><Close /></el-icon>
    </button>

    <div class="activity-center-header">
      <span class="activity-center-title">公告与活动中心</span>
    </div>

    <div v-if="loading" class="announcement-loading">
      加载中…
    </div>
    <div v-else-if="!notices.length" class="announcement-empty">
      暂无已发布公告或活动。
    </div>

    <div v-else class="activity-center-body">
      <el-scrollbar class="activity-center-list-scroll">
        <button
          v-for="n in notices"
          :key="n.id"
          type="button"
          class="activity-center-list-item"
          :class="{ 'is-active': String(n.id) === activeTab }"
          @click="selectNotice(n)"
        >
          <span
            class="activity-kind-tag"
            :class="Number(n.noticeKind) === 1 ? 'is-event' : 'is-notice'"
          >
            {{ Number(n.noticeKind) === 1 ? '活动' : '公告' }}
          </span>
          <span class="activity-list-text">
            <span class="activity-list-title">{{ n.title }}</span>
            <span v-if="n.updateTime" class="activity-list-updated">更新于 {{ n.updateTime }}</span>
          </span>
        </button>
      </el-scrollbar>

      <div v-if="current" class="activity-center-content">
        <h2 class="view-title">{{ current.title }}</h2>
        <p v-if="current.subtitle" class="view-subtitle">{{ current.subtitle }}</p>
        <p v-if="current.updateTime" class="view-updated">更新于 {{ current.updateTime }}</p>

        <div v-if="featureRows.length" class="feature-list">
          <div v-for="(feat, idx) in featureRows" :key="idx" class="feature-item">
            <el-tag :type="feat.tagType" size="small" effect="dark" round class="status-tag">
              {{ feat.label }}
            </el-tag>
            <span class="feature-desc">{{ feat.text }}</span>
          </div>
        </div>

        <div class="markdown-body announcement-md" v-html="mdHtml" />

        <div v-if="isHeroTemplate && coverSrc" class="content-media activity-cover">
          <el-image :src="coverSrc" fit="cover" class="view-img" />
        </div>
      </div>
      <div v-else class="activity-center-content activity-center-content--empty">
        请选择左侧条目查看详情
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'
import { useAnnouncementBoard } from '@scripts/components/common/AnnouncementBoard'

const {
  activeTab,
  coverSrc,
  current,
  featureRows,
  isHeroTemplate,
  loading,
  mdHtml,
  notices,
  selectNotice,
  show,
  visible,
} = useAnnouncementBoard()

defineExpose({ show })
</script>

<style scoped>
.announcement-loading,
.announcement-empty {
  padding: 32px 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.activity-center-content--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}

.activity-cover {
  margin-top: 16px;
}

.announcement-md {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
}

.announcement-md :deep(h1),
.announcement-md :deep(h2),
.announcement-md :deep(h3) {
  margin: 0.75em 0 0.4em;
  font-weight: 600;
}

.announcement-md :deep(p) {
  margin: 0.5em 0;
}
</style>
