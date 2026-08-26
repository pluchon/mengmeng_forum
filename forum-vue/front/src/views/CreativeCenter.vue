<template>
  <div class="creative-center-page shell-page-scroll animate-fade-in" v-loading="loading">
    <div class="creative-center-inner">
      <header class="creative-center-header">
        <div class="creative-center-heading">
          <span class="creative-center-heading-icon"><img :src="creationCenterIconUrl" alt="" /></span>
          <h1 class="creative-center-title">创作中心</h1>
        </div>
        <button type="button" class="creative-center-create-btn" @click="goCreatePost">
          <el-icon><Plus /></el-icon>
          发表新帖
        </button>
      </header>

      <section class="creative-center-hero">
        <div class="creative-center-hero-copy">
          <h2>今天想分享什么？</h2>
          <p>灵感不用很完整，先从一张图或一句话开始</p>
        </div>
        <div class="creative-center-hero-illustration" aria-hidden="true"></div>
      </section>

      <section class="creative-dashboard">
        <BorderGlow
          class="creative-card-glow"
          :animated="insightLoading"
          :edge-sensitivity="30"
          glow-color="320 84 72"
          background-color="#ffffff"
          :border-radius="18"
          :glow-radius="42"
          :glow-intensity="1.1"
          :cone-spread="28"
          :sweep-speed="100"
          :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
        >
          <div class="creative-trend-card">
            <div class="creative-panel-head">
              <div class="creative-insight-heading">
                <h2 class="creative-panel-title">
                  <span class="creative-panel-title-icon is-insight"><el-icon><TrendCharts /></el-icon></span>
                  {{ insightPage < 4 ? activeInsightPage?.title : 'AI 创作小结' }}
                </h2>
                <div class="creative-insight-period" aria-label="AI总结周期">
                  <button
                    v-for="option in INSIGHT_PERIOD_OPTIONS"
                    :key="option.value"
                    type="button"
                    :class="{ 'is-active': insightPeriod === option.value }"
                    :disabled="insightLoading"
                    @click="selectInsightPeriod(option.value)"
                  >{{ option.label }}</button>
                </div>
              </div>
            </div>
            <div class="creative-insight-stage">
              <div v-if="insightDataLoading" class="creative-trend-loading">正在整理趋势...</div>
              <template v-else-if="insightPage < 4">
                <EChart v-if="insightChartOption" class="creative-insight-chart" :option="insightChartOption" />
              </template>
              <div v-else-if="insightLoading" class="creative-insight-loading" aria-live="polite">
                <div class="creative-insight-orbit" aria-hidden="true">
                  <i></i><i></i><i></i>
                  <el-icon><MagicStick /></el-icon>
                </div>
                <div class="creative-insight-loading-mask">
                  <strong>AI 正在整理创作足迹...</strong>
                  <span>正在回望{{ insightPeriodLabel }}的数据变化</span>
                </div>
              </div>
              <div v-else-if="currentInsight" class="creative-insight-result">
                <div class="creative-insight-summary">
                  <span class="creative-insight-kicker">小结</span>
                  <h3>{{ currentInsight.headline }}</h3>
                  <p>{{ currentInsight.overview }}</p>
                </div>
                <div class="creative-insight-notes">
                  <span>亮点</span>
                  <ul>
                    <li v-for="item in (currentInsight.highlights || [currentInsight.highlight]).filter(Boolean)" :key="item">{{ item }}</li>
                  </ul>
                </div>
              </div>
              <button v-else type="button" class="creative-insight-prompt" @click="requestCreatorInsight">
                <span class="creative-insight-starlight" aria-hidden="true">
                  <i v-for="index in 9" :key="index"></i>
                </span>
                <strong>使用 AI 总结{{ insightPeriodLabel }}的创作数据</strong>
              </button>
            </div>
            <div class="creative-insight-nav">
              <button type="button" aria-label="上一页" @click="moveInsightPage(-1)"><el-icon><ArrowLeft /></el-icon></button>
              <button
                v-for="index in 5"
                :key="index"
                type="button"
                class="creative-insight-dot"
                :class="{ 'is-active': insightPage === index - 1 }"
                :aria-label="`第${index}页`"
                @click="selectInsightPage(index - 1)"
              />
              <button type="button" aria-label="下一页" @click="moveInsightPage(1)"><el-icon><ArrowRight /></el-icon></button>
            </div>
          </div>
        </BorderGlow>

        <aside class="creative-data-garden">
          <div class="creative-panel-head">
            <h2 class="creative-panel-title">
              <span class="creative-panel-title-icon is-garden"><el-icon><MagicStick /></el-icon></span>
              数据花园
            </h2>
          </div>
          <div class="creative-data-garden-list">
            <div v-for="item in dataGardenItems" :key="item.label" class="creative-data-garden-item">
              <div class="creative-data-garden-label" :class="`is-${item.kind}`">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </div>
              <span v-if="item.monthIncrease > 0" class="creative-data-garden-change" :class="`is-${item.changeTone}`">
                <el-icon><component :is="item.changeIcon" /></el-icon>
                <span>本月 +{{ item.monthIncrease }}</span>
              </span>
              <strong :class="`is-${item.kind}`">{{ item.value }}</strong>
            </div>
          </div>
        </aside>
      </section>

      <BorderGlow
        class="creative-card-glow creative-card-glow--posts"
        :animated="listLoading && searchMode === 'ai'"
        :edge-sensitivity="30"
        glow-color="320 84 72"
        background-color="#ffffff"
        :border-radius="18"
        :glow-radius="42"
        :glow-intensity="1.1"
        :cone-spread="28"
        :sweep-speed="100"
        :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
      >
        <section
          class="creative-posts-card"
          v-loading="listLoading && searchMode !== 'ai'"
        >
          <div class="creative-posts-toolbar">
            <h2 class="creative-panel-title">
              <span class="creative-panel-title-icon is-posts"><el-icon><ChatDotRound /></el-icon></span>
              帖子整理台
            </h2>
            <div class="creative-posts-toolbar-right">
              <el-select v-model="statusFilter" class="creative-status-select" size="small" placeholder="已发布">
                <el-option
                  v-for="opt in STATUS_FILTER_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <div class="creative-search-wrap" :class="{ 'is-vip': isVipMember, 'is-ai': searchMode === 'ai' }">
                <el-input
                  v-model="keyword"
                  class="creative-search"
                  size="small"
                  clearable
                  placeholder="找找你的帖子"
                  :disabled="listLoading && searchMode === 'ai'"
                >
                  <template #prefix>
                    <button
                      type="button"
                      class="creative-search-ai-mode"
                      :class="{ 'is-ai': searchMode === 'ai' }"
                      :disabled="listLoading && searchMode === 'ai'"
                      :aria-label="searchMode === 'ai' ? '切换到普通搜索' : '切换到AI搜索'"
                      @click.stop="toggleSearchMode"
                    >AI</button>
                    <el-icon class="creative-search-prefix-icon"><Search /></el-icon>
                  </template>
                </el-input>
              </div>
            </div>
          </div>

          <div class="creative-posts-body">
            <div
              v-if="listLoading && searchMode === 'ai'"
              class="creative-ai-search-mask"
              role="status"
              aria-live="polite"
            >
              <span class="creative-ai-search-star creative-ai-search-star--one" aria-hidden="true">✦</span>
              <span class="creative-ai-search-star creative-ai-search-star--two" aria-hidden="true">✧</span>
              <span class="creative-ai-search-star creative-ai-search-star--three" aria-hidden="true">✦</span>
              <span class="creative-ai-search-star creative-ai-search-star--four" aria-hidden="true">✧</span>
              <strong>AI 正在帮你找帖…</strong>
            </div>
            <div v-if="pagedArticles.length" class="creative-post-list">
              <article v-for="row in pagedArticles" :key="row.article.id" class="creative-post-item" @click="openArticle(row)">
                <div class="creative-post-cover">
                  <img v-if="postCoverUrl(row)" :src="postCoverUrl(row)" :alt="postTitle(row)" />
                  <el-icon v-else><Picture /></el-icon>
                </div>
                <div class="creative-post-item-main">
                  <h3 class="creative-post-title" :class="postTitleClass(row)">{{ postTitle(row) }}</h3>
                  <div class="creative-post-meta">
                    <template v-if="postMetrics(row)">
                      <span class="creative-post-metric"><el-icon><View /></el-icon>{{ postMetrics(row).reads }}</span>
                      <span class="creative-post-metric"><LikeCountIcon class="creative-post-metric-icon" />{{ postMetrics(row).likes }}</span>
                      <span class="creative-post-metric"><el-icon><Star /></el-icon>{{ postMetrics(row).favorites }}</span>
                    </template>
                    <span v-else>草稿未发布</span>
                    <i class="creative-post-meta-divider"></i>
                    <span>{{ formatShortDate(row.article.createTime) }}</span>
                  </div>
                </div>
                <div class="creative-post-status" :class="`is-${postStatus(row).tone}`">{{ postStatus(row).label }}</div>
                <div class="creative-post-actions">
                  <el-tooltip :content="editTip(row)" placement="top">
                  <router-link :to="editTargetPath(row)" class="creative-post-action-btn" :aria-label="editTip(row)" @click.stop>
                    <img :src="editIconUrl" alt="" />
                  </router-link>
                </el-tooltip>
                  <template v-if="deletingArticleId === row.article.id">
                    <button type="button" class="creative-post-delete-confirm is-confirm" aria-label="确定删除" @click.stop="confirmDelete(row.article.id)">
                      ✓ 确定
                    </button>
                    <button type="button" class="creative-post-delete-confirm" aria-label="取消删除" @click.stop="cancelDelete">
                      ✕ 取消
                    </button>
                  </template>
                  <el-tooltip v-else content="删除" placement="top">
                    <button type="button" class="creative-post-action-btn" aria-label="删除" @click.stop="requestDelete(row.article.id)">
                      <img :src="deleteIconUrl" alt="" />
                    </button>
                  </el-tooltip>
                </div>
              </article>
            </div>
            <div v-else class="creative-posts-empty">
              <img :src="creativePostsEmptyImageUrl" alt="暂无帖子" />
              <p>暂无此类型帖子......</p>
            </div>
          </div>

          <div class="creative-posts-pagination">
            <AppPagination
              v-model:current-page="pageNum"
              :page-size="LIST_PAGE_SIZE"
              :total="listTotal"
            />
          </div>
        </section>
      </BorderGlow>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'CreativeCenter' })
import {
  ArrowLeft,
  ArrowRight,
  ChatDotRound,
  MagicStick,
  Picture,
  Plus,
  Search,
  Star,
  StarFilled,
  TrendCharts,
  User,
  View,
} from '@element-plus/icons-vue'
import BorderGlow from '@/components/common/BorderGlow.vue'
import EChart from '@/components/common/EChart.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useCreativeCenter } from '@scripts/views/CreativeCenter'
import creationCenterIconUrl from '@/assets/svg/11_creation_center.svg?url'
import creativePostsEmptyImageUrl from '@/assets/images/search_chat_empty.png'

const LIST_PAGE_SIZE = 6

const {
  INSIGHT_PERIOD_OPTIONS,
  STATUS_FILTER_OPTIONS,
  dataGardenItems,
  currentInsight,
  deletingArticleId,
  deleteIconUrl,
  editIconUrl,
  editTargetPath,
  editTip,
  formatShortDate,
  goCreatePost,
  insightLoading,
  insightDataLoading,
  insightPage,
  activeInsightPage,
  insightChartOption,
  selectInsightPage,
  moveInsightPage,
  insightPeriod,
  insightPeriodLabel,
  cancelDelete,
  confirmDelete,
  isVipMember,
  keyword,
  searchMode,
  listTotal,
  loading,
  listLoading,
  pageNum,
  pagedArticles,
  postStatus,
  openArticle,
  requestCreatorInsight,
  requestDelete,
  postCoverUrl,
  postMetrics,
  postTitle,
  postTitleClass,
  statusFilter,
  selectInsightPeriod,
  toggleSearchMode,
} = useCreativeCenter({ Picture, View, Star, TrendCharts, User })
</script>

<style scoped src="@/assets/styles/creative-center.css"></style>
