<template>
  <div class="creative-center-page shell-page-scroll animate-fade-in" v-loading="loading">
    <div class="creative-center-inner">
      <header class="creative-center-header">
        <div>
          <h1 class="creative-center-title">创作中心</h1>
          <p class="creative-center-desc">管理你的灵感与创作</p>
        </div>
        <button type="button" class="creative-center-create-btn" @click="goCreatePost">
          <el-icon :size="14"><Plus /></el-icon>
          发表新帖
        </button>
      </header>

      <div class="creative-stats-grid">
        <div class="creative-stat-card">
          <div class="creative-stat-head">
            <span class="creative-stat-label">总发帖数</span>
            <el-icon class="creative-stat-icon"><Document /></el-icon>
          </div>
          <div class="creative-stat-value">{{ totalPosts }}</div>
          <div class="creative-stat-sub">本月 +{{ monthNewPosts }}</div>
        </div>
        <div class="creative-stat-card">
          <div class="creative-stat-head">
            <span class="creative-stat-label">总点赞数</span>
            <el-icon class="creative-stat-icon"><Star /></el-icon>
          </div>
          <div class="creative-stat-value creative-stat-value--pink">{{ totalLikes }}</div>
          <div class="creative-stat-sub">本月 +{{ monthNewLikes }}</div>
        </div>
        <div class="creative-stat-card">
          <div class="creative-stat-head">
            <span class="creative-stat-label">总阅读数</span>
            <el-icon class="creative-stat-icon"><View /></el-icon>
          </div>
          <div class="creative-stat-value creative-stat-value--blue">{{ totalReads }}</div>
          <div class="creative-stat-sub">本月 +{{ monthNewReads }}</div>
        </div>
        <div class="creative-stat-card">
          <div class="creative-stat-head">
            <span class="creative-stat-label">粉丝数</span>
            <el-icon class="creative-stat-icon"><User /></el-icon>
          </div>
          <div class="creative-stat-value">0</div>
          <div class="creative-stat-sub">暂无新增</div>
        </div>
      </div>

      <section class="creative-trend-card">
        <h2 class="creative-sec-title">
          <el-icon class="creative-sec-title-icon creative-sec-title-icon--pink"><TrendCharts /></el-icon>
          近 30 天阅读 / 点赞趋势
        </h2>
        <EChart v-if="trendChartOption" class="creative-trend-echart" :option="trendChartOption" />
      </section>

      <section class="creative-posts-card">
        <div class="creative-posts-toolbar">
          <h2 class="creative-sec-title" style="margin: 0">
            <img src="@/assets/svg/文章.svg" alt="" class="creative-sec-title-icon" width="14" height="14" />
            帖子管理
          </h2>
          <div class="creative-posts-toolbar-right">
            <el-select
              v-model="statusFilter"
              class="creative-status-select"
              size="small"
              placeholder="全部状态"
            >
              <el-option
                v-for="opt in STATUS_FILTER_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <div class="creative-search-wrap" :class="{ 'is-vip': isVipMember }">
              <el-input
                v-model="keyword"
                class="creative-search"
                size="small"
                clearable
                placeholder="搜索帖子..."
              />
            </div>
          </div>
        </div>
        <hr class="creative-posts-divider" />

        <div class="creative-posts-head">
          <span>标题</span>
          <span>状态</span>
          <span>互动</span>
          <span>日期</span>
          <span>操作</span>
        </div>

        <template v-if="pagedArticles.length">
          <div v-for="row in pagedArticles" :key="row.article.id" class="creative-post-row">
            <router-link
              :to="postLink(row)"
              class="creative-post-title"
              :class="postTitleClass(row)"
            >
              {{ postTitle(row) }}
            </router-link>
            <div class="creative-post-status">
              <el-tooltip v-if="row.article.state === 1" content="已下架" placement="top">
                <img
                  :src="articleStatusMeta(ARTICLE_STATUS.PUBLISHED).icon"
                  alt="已下架"
                  class="creative-post-status-icon"
                />
              </el-tooltip>
              <el-tooltip
                v-else
                :content="articleStatusMeta(row.article.status).tip"
                placement="top"
              >
                <img
                  :src="articleStatusMeta(row.article.status).icon"
                  :alt="articleStatusMeta(row.article.status).tip"
                  class="creative-post-status-icon"
                />
              </el-tooltip>
            </div>
            <div class="creative-post-interact">
              <template v-if="interactDisplay(row)">
                <span class="creative-post-interact-item">
                  <el-icon :size="12"><View /></el-icon>
                  {{ interactDisplay(row).reads }}
                </span>
                <span class="creative-post-interact-item">
                  <el-icon :size="12"><Star /></el-icon>
                  {{ interactDisplay(row).likes }}
                </span>
              </template>
              <span v-else>—</span>
            </div>
            <div class="creative-post-date">{{ formatDate(row.article.createTime) }}</div>
            <div class="creative-post-actions">
              <el-tooltip :content="editTip(row)" placement="top">
                <router-link
                  :to="editTargetPath(row)"
                  class="creative-post-action-btn"
                  :aria-label="editTip(row)"
                >
                  <img :src="editIconUrl" alt="" />
                </router-link>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <button
                  type="button"
                  class="creative-post-action-btn"
                  aria-label="删除"
                  @click="handleDelete(row.article.id)"
                >
                  <img :src="deleteIconUrl" alt="" />
                </button>
              </el-tooltip>
            </div>
          </div>
        </template>
        <div v-else class="creative-posts-empty">暂无帖子</div>

        <div v-if="listTotal > LIST_PAGE_SIZE" class="creative-posts-pagination">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="LIST_PAGE_SIZE"
            layout="prev, pager, next"
            :total="listTotal"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { Document, TrendCharts, User } from '@element-plus/icons-vue'
import EChart from '@/components/common/EChart.vue'
import { useCreativeCenter } from '@scripts/views/CreativeCenter'
import { ARTICLE_STATUS } from '@/utils/articleStatus'

const LIST_PAGE_SIZE = 10

const {
  Plus,
  Star,
  View,
  STATUS_FILTER_OPTIONS,
  articleStatusMeta,
  deleteIconUrl,
  editIconUrl,
  editTargetPath,
  editTip,
  formatDate,
  handleDelete,
  interactDisplay,
  isVipMember,
  keyword,
  listTotal,
  loading,
  monthNewLikes,
  monthNewPosts,
  monthNewReads,
  pageNum,
  pagedArticles,
  postLink,
  postTitle,
  postTitleClass,
  statusFilter,
  totalLikes,
  totalPosts,
  totalReads,
  trendChartOption,
  goCreatePost,
} = useCreativeCenter()
</script>

<style scoped src="@/assets/styles/creative-center.css"></style>
