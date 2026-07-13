<template>
  <div class="shell-main-stack shell-page-scroll">
    <div v-if="showCategoryNavigator" class="home-discovery-section">
      <nav class="home-discovery-nav" aria-label="首页板块导航">
        <button
          type="button"
          class="home-discovery-all"
          :class="{ 'is-active': currentBoardId === 0 }"
          @click="selectCategoryMenu('home')"
        >
          全部
        </button>

        <div
          v-for="item in categoriesWithId"
          :key="item.category.id"
          class="home-discovery-category"
          @mouseenter="openCategory(item.category.id)"
          @mouseleave="scheduleCloseCategory"
          @focusin="openCategory(item.category.id)"
          @focusout="handleCategoryFocusOut"
        >
          <button
            type="button"
            class="home-discovery-category-trigger"
            :class="{ 'is-active': activeCategoryId === item.category.id }"
            :aria-expanded="openCategoryId === item.category.id"
            aria-haspopup="menu"
            @click="toggleCategory(item.category.id)"
          >
            <span>{{ item.category.name }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>

          <div
            v-if="openCategoryId === item.category.id"
            class="home-discovery-board-menu"
            role="menu"
            :aria-label="`${item.category.name}细分板块`"
          >
            <button
              v-for="board in item.boardList || []"
              :key="board.id"
              type="button"
              class="home-discovery-board-option"
              :class="{ 'is-active': currentBoardId === board.id }"
              role="menuitem"
              @click="selectHomeBoard(item.category.id, board.id)"
            >
              {{ board.name }}
            </button>
            <span v-if="!(item.boardList || []).length" class="home-discovery-board-empty">暂无细分板块</span>
          </div>
        </div>
      </nav>
    </div>

    <main class="home-xhs-main home-xhs-main--feed" :class="{ 'home-xhs-main--with-hot': isHomeFeed }">
      <div v-if="showCheckinHomeStrip" class="checkin-home-strip animate-fade-up">
        <el-card
          class="checkin-home-card"
          :class="{ 'checkin-home-done': checkinSummary.todaySigned }"
          shadow="hover"
          :body-style="{ padding: '14px 18px', cursor: 'pointer', position: 'relative' }"
          @click="$router.push('/checkin')"
        >
          <el-button
            v-if="checkinSummary.todaySigned"
            class="checkin-home-close"
            type="info"
            text
            circle
            size="small"
            :icon="Close"
            aria-label="收起签到提示"
            @click.stop="dismissCheckinHomeStrip"
          />
          <div class="checkin-home-inner">
            <PawCoinIcon class="checkin-home-paw" />
            <div class="checkin-home-text">
              <div class="checkin-home-title">每日签到 · 萌币</div>
              <div class="checkin-home-meta">
                已攒 <strong>{{ checkinSummary.totalPoints ?? 0 }}</strong> 萌币
                · 连续 <strong>{{ checkinSummary.streakDays ?? 0 }}</strong> 天
                <el-tag v-if="checkinSummary.todaySigned" size="small" type="success" round class="checkin-home-tag">
                  今日已签
                </el-tag>
                <el-tag v-else size="small" type="warning" effect="plain" round class="checkin-home-tag">待签到</el-tag>
              </div>
            </div>
            <el-button
              v-if="!checkinSummary.todaySigned"
              type="primary"
              round
              size="small"
              @click.stop="$router.push('/checkin')"
            >
              去签到
            </el-button>
            <el-button v-else type="success" round size="small" disabled>
              <el-icon class="checkin-home-check"><CircleCheck /></el-icon>
              今日已签
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-if="loading" class="home-masonry home-masonry--loading">
        <div v-for="i in 8" :key="i" class="home-masonry-item">
          <el-skeleton animated :rows="6" class="skeleton-card" />
        </div>
      </div>

      <div v-if="!loading && (feedList.length || showRecommendationInterestMask)" class="recommendation-feed-stage">
        <div
          ref="masonryRef"
          class="home-masonry"
          :class="{ 'home-masonry--obscured': showRecommendationInterestMask }"
        >
          <div
            v-for="(col, colIdx) in masonryColumns"
            :key="'m-col-' + colIdx"
            class="home-masonry-column"
          >
            <div
              v-for="entry in col"
              :key="entry.article?.id"
              class="home-masonry-item"
            >
              <el-card
                class="note-card note-card--masonry"
                :body-style="{ padding: '0px' }"
                shadow="hover"
                @click="openArticle(entry, $event)"
              >
                <div class="note-cover note-cover--fluid">
                  <img
                    v-if="coverImageUrl(entry)"
                    class="note-cover-img"
                    :src="coverImageUrl(entry)"
                    :alt="entry.article?.title || ''"
                    loading="lazy"
                  />
                  <div
                    v-else
                    class="note-cover-placeholder"
                    :class="{ 'note-cover-placeholder--video': Number(entry.article?.mediaType) === 1 }"
                    :style="{
                      background: getRandomPastel(),
                      minHeight: placeholderMinHeight(entry.article?.id),
                    }"
                  >
                    <span class="cover-title">{{ (entry.article?.title || '').substring(0, 12) }}</span>
                  </div>
                  <div v-if="Number(entry.article?.mediaType) === 1" class="note-cover-play" aria-hidden="true" />
                </div>
                <div class="note-info">
                  <h3 class="note-title">{{ entry.article?.title }}</h3>
                  <div v-if="isRecommendationFeed && entry.recommendReason" class="recommendation-reason">
                    {{ entry.recommendReason }}
                  </div>
                  <div class="note-footer">
                    <div class="author">
                      <UserAvatarVip
                        :size="22"
                        :src="entry.user?.avatarUrl || defaultAvatar"
                        :vip-tier="Number(entry.user?.vipTier) || 0"
                        :vip-expire-at="entry.user?.vipExpireAt"
                      />
                      <span class="nickname">{{ entry.user?.nickname }}</span>
                      <FollowingBadge :from-following="!!entry.fromFollowing" />
                    </div>
                    <div class="likes">
                      <LikeCountIcon />
                      <span>{{ entry.article?.likeCount }}</span>
                    </div>
                    <el-dropdown
                      v-if="isRecommendationFeed && userStore.isLoggedIn"
                      trigger="click"
                      @command="hideRecommendedArticle(entry.article?.id)"
                      @click.stop
                    >
                      <button type="button" class="recommendation-card-menu" aria-label="调整推荐内容" @click.stop>
                        <el-icon><MoreFilled /></el-icon>
                      </button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="not-interested" :disabled="recommendationSaving">
                            不想看这篇
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>
              </el-card>
            </div>
          </div>
        </div>
        <section v-if="showRecommendationInterestMask" class="recommendation-interest-mask" aria-label="选择推荐兴趣">
          <div class="recommendation-interest-mask-card">
            <h2>选择感兴趣的板块</h2>
            <el-button type="primary" round @click="openRecommendationPreferences">选择兴趣</el-button>
          </div>
        </section>
      </div>

      <transition name="home-hot-collapse" mode="out-in">
        <aside v-if="isHomeFeed && !homeHotCollapsed" class="home-hot-floating" :class="{ 'is-checkin-visible': showCheckinHomeStrip }" aria-label="热帖榜">
        <div class="home-hot-floating-head">
          <div class="home-hot-floating-title">
            <el-icon><TrendCharts /></el-icon>
            <span>热帖榜</span>
          </div>
          <button type="button" class="home-hot-collapse-action" @click="toggleHomeHotCollapsed">点击收起</button>
        </div>
        <div v-if="homeHotLoading" class="home-hot-floating-loading">
          <el-skeleton v-for="item in 4" :key="item" animated :rows="1" />
        </div>
        <div v-else-if="homeHotList.length" class="home-hot-floating-list">
          <button
            v-for="entry in homeHotList"
            :key="entry.article?.id"
            type="button"
            class="home-hot-floating-item"
            @click="openArticle(entry, $event)"
          >
            <span class="home-hot-floating-rank" :class="{ 'is-top': Number(entry.rank) <= 3 }">
              {{ entry.rank }}
            </span>
            <img v-if="coverImageUrl(entry)" :src="coverImageUrl(entry)" :alt="entry.article?.title || ''" class="home-hot-floating-cover" />
            <span v-else class="home-hot-floating-cover home-hot-floating-cover--placeholder">热</span>
            <span class="home-hot-floating-copy">
              <strong>{{ entry.article?.title }}</strong>
              <small>{{ entry.article?.likeCount || 0 }} 赞 · {{ entry.article?.replyCount || 0 }} 评</small>
            </span>
          </button>
        </div>
        <el-empty v-else :image-size="42" description="暂无热帖" />
        <el-pagination
          v-if="homeHotTotal > homeHotPageSize"
          v-model:current-page="homeHotPage"
          class="home-hot-floating-pager"
          :total="homeHotTotal"
          :page-size="homeHotPageSize"
          layout="prev, pager, next"
          small
          background
          @current-change="fetchHomeHotList"
        />
        </aside>
        <button v-else-if="isHomeFeed" type="button" class="home-hot-collapsed-button" aria-label="展开热帖榜" @click="toggleHomeHotCollapsed">
          <el-icon><TrendCharts /></el-icon>
        </button>
      </transition>

      <div v-if="loading && feedList.length" class="home-feed-loading-more" aria-live="polite">
        <el-icon class="home-feed-loading-spin" :size="20"><Loading /></el-icon>
        <span>正在加载更多…</span>
      </div>

      <el-result
        v-if="!loading && feedError"
        class="home-feed-error"
        icon="error"
        :title="feedForbidden ? '暂时无法访问这部分内容' : '内容加载失败'"
        :sub-title="feedError"
      >
        <template #extra>
          <el-button type="primary" @click="fetchArticles(pageNum)">重新加载</el-button>
        </template>
      </el-result>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next, jumper"
          background
          @current-change="fetchArticles"
        />
      </div>

      <el-empty
        v-if="!loading && !feedError && !showRecommendationInterestMask && feedList.length === 0"
        description="这里还没有笔记哦"
      />
    </main>

    <el-dialog
      v-model="recommendationDialogVisible"
      class="recommendation-interest-dialog"
      title="兴趣卡片"
      width="min(620px, calc(100vw - 32px))"
      :close-on-click-modal="!recommendationSaving"
      :close-on-press-escape="!recommendationSaving"
    >
      <div class="recommendation-interest-groups">
        <section v-for="item in categoriesWithId" :key="item.category.id" class="recommendation-interest-group">
          <h3>{{ item.category.name }}</h3>
          <el-checkbox-group v-model="recommendationDraftBoardIds" :disabled="recommendationSaving">
            <el-checkbox v-for="board in item.boardList || []" :key="board.id" :value="Number(board.id)">
              {{ board.name }}
            </el-checkbox>
          </el-checkbox-group>
        </section>
      </div>
      <template #footer>
        <div class="recommendation-dialog-actions">
          <el-button class="recommendation-save-button" type="primary" :loading="recommendationSaving" @click="saveRecommendationPreferences">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
  <router-view />
</template>

<script setup>
defineOptions({ name: 'HomeFeed' })

import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Loading, MoreFilled, TrendCharts } from '@element-plus/icons-vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import FollowingBadge from '@/components/common/FollowingBadge.vue'
import { useBoardStore } from '@/stores/board'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useHomeMasonry } from '@/composables/useHomeMasonry'
import { restoreFeedScroll } from '@/utils/feedScrollRestore'
import { captureFeedCardOrigin, captureFeedOpenFrom } from '@/utils/feedNavigation'

const route = useRoute()
const router = useRouter()
const boardStore = useBoardStore()

const {
  CircleCheck,
  Close,
  articleList,
  activeCategoryId,
  categoriesWithId,
  checkinSummary,
  coverImageUrl,
  currentBoardId,
  defaultAvatar,
  dismissCheckinHomeStrip,
  feedError,
  feedForbidden,
  ensureHomeFeedLoaded,
  fetchArticles,
  fetchHomeHotList,
  getRandomPastel,
  hideRecommendedArticle,
  homeHotList,
  homeHotTotal,
  homeHotLoading,
  homeHotCollapsed,
  homeHotPage,
  homeHotPageSize,
  isHomeFeed,
  isRecommendationFeed,
  loading,
  pageNum,
  pageSize,
  placeholderMinHeight,
  selectCategoryMenu,
  selectHomeBoard,
  showCategoryNavigator,
  showCheckinHomeStrip,
  total,
  toggleHomeHotCollapsed,
  openRecommendationPreferences,
  recommendationDialogVisible,
  recommendationDraftBoardIds,
  recommendationSaving,
  saveRecommendationPreferences,
  showRecommendationInterestMask,
  userStore,
} = useHomeShellContext()

const feedList = computed(() => articleList.value)

const openCategoryId = ref(null)
let categoryCloseTimer = null

const { containerRef: masonryRef, columns: masonryColumns } = useHomeMasonry(feedList, {
  columnWidth: 220,
  gap: 16,
})

function openArticle(entry, event) {
  const id = entry?.article?.id
  if (!id) return
  const card = event?.currentTarget?.closest?.('.home-masonry-item') || event?.currentTarget
  if (card) captureFeedCardOrigin(id, card)
  captureFeedOpenFrom(route.path)
  router.push(`/article/${id}`)
}

function openCategory(categoryId) {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
  openCategoryId.value = categoryId
}

function scheduleCloseCategory() {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
  categoryCloseTimer = window.setTimeout(() => {
    openCategoryId.value = null
  }, 180)
}

function toggleCategory(categoryId) {
  openCategoryId.value = openCategoryId.value === categoryId ? null : categoryId
}

function handleCategoryFocusOut(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  scheduleCloseCategory()
}

onMounted(async () => {
  if (boardStore.categoryList.length === 0) await boardStore.fetchCategoryList()
  await ensureHomeFeedLoaded()
})

onActivated(() => {
  restoreFeedScroll()
})

onUnmounted(() => {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
})
</script>

<style scoped>
.shell-main-stack {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
