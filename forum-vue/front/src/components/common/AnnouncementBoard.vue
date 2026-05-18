<template>
  <el-dialog
    v-model="visible"
    width="900px"
    class="pro-announcement-dialog"
    :show-close="false"
    destroy-on-close
    append-to-body
  >
    <div class="announcement-header">
      <el-icon :size="24"><Bell /></el-icon>
      <span class="header-title">公告中心</span>
    </div>

    <div v-if="loading" class="announcement-loading">
      加载公告中…
    </div>
    <div v-else-if="!notices.length" class="announcement-empty">
      暂无已发布公告，请稍后再来查看。
    </div>

    <div v-else class="announcement-body-shell">
      <el-tabs v-model="activeTab" tab-position="left" class="announcement-tabs">
      <el-tab-pane v-for="n in notices" :key="n.id" :name="String(n.id)">
        <template #label>
          <div class="tab-label">
            <el-icon><component :is="iconForKind(n.noticeKind)" /></el-icon>
            <span class="tab-label-text">{{ n.title }}</span>
          </div>
        </template>

        <div v-if="current && String(current.id) === String(n.id)" class="content-view" :class="{ 'content-plain': !isHeroTemplate }">
          <div class="content-text">
            <h2 class="view-title">{{ n.title }}</h2>
            <p v-if="n.subtitle" class="view-subtitle">{{ n.subtitle }}</p>
            <p v-if="n.updateTime" class="view-updated">更新于 {{ n.updateTime }}</p>

            <div v-if="featureRows.length" class="feature-list">
              <div v-for="(feat, idx) in featureRows" :key="idx" class="feature-item">
                <el-tag :type="feat.tagType" size="small" effect="dark" round class="status-tag">
                  {{ feat.label }}
                </el-tag>
                <span class="feature-desc">{{ feat.text }}</span>
              </div>
            </div>

            <div class="markdown-body announcement-md" v-html="mdHtml" />
          </div>

          <div v-if="isHeroTemplate && coverSrc" class="content-media">
            <el-image :src="coverSrc" fit="cover" class="view-img" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
    </div>

    <template #footer>
      <div class="announcement-footer">
        <el-button type="primary" round class="explore-btn" @click="visible = false">
          开始探索之旅
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { useAnnouncementBoard } from '@scripts/components/common/AnnouncementBoard'

const {
  Bell,
  activeTab,
  coverSrc,
  current,
  featureRows,
  iconForKind,
  isHeroTemplate,
  loading,
  mdHtml,
  notices,
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

.tab-label-text {
  display: inline-block;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.content-view.content-plain {
  flex-direction: column;
}

.content-plain .content-text {
  max-width: 100%;
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

.announcement-md :deep(ul),
.announcement-md :deep(ol) {
  padding-left: 1.25em;
}

.announcement-md :deep(pre) {
  padding: 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  overflow: auto;
}

.announcement-md-empty {
  margin: 0;
  color: var(--el-text-color-placeholder);
}
</style>
