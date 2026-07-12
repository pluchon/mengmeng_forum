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
          @mouseleave="closeCategory"
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
            <div class="home-discovery-board-menu-head">{{ item.category.name }}</div>
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

    <main class="home-xhs-main home-xhs-main--feed">
      <section v-if="isRecommendationFeed" class="recommendation-intro" aria-label="为你推荐设置">
        <div class="recommendation-intro-copy">
          <span class="recommendation-intro-eyebrow">FOR YOU</span>
          <h2>为你推荐</h2>
          <p v-if="isPersonalizedRecommendation">由你的兴趣、关注和社区热度共同决定</p>
          <p v-else-if="userStore.isLoggedIn">选几个感兴趣的板块，让内容更贴近你</p>
          <p v-else>登录后可选择兴趣，获得更贴近你的内容</p>
        </div>
        <el-button class="recommendation-manage-btn" plain @click="openRecommendationPreferences">
          {{ hasRecommendationInterests ? '管理兴趣' : '选择兴趣' }}
        </el-button>
      </section>

      <div v-if="showCheckinHomeStrip && !isHotFeed" class="checkin-home-strip animate-fade-up">
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

      <div
        v-show="!loading && feedList.length"
        ref="masonryRef"
        class="home-masonry"
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

      <div v-if="!isHotFeed && total > pageSize" class="pagination-wrap">
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
        v-if="!loading && !feedError && feedList.length === 0"
        :description="isHotFeed ? '暂无热帖' : '这里还没有笔记哦'"
      />
    </main>

    <el-dialog
      v-model="recommendationDialogVisible"
      class="recommendation-interest-dialog"
      title="让推荐更懂你"
      width="min(620px, calc(100vw - 32px))"
      :close-on-click-modal="!recommendationSaving"
      :close-on-press-escape="!recommendationSaving"
    >
      <p class="recommendation-dialog-tip">选择 3～8 个细分板块效果更好；也可以暂时跳过，之后随时回来调整。</p>
      <div class="recommendation-dialog-switch">
        <span>
          <strong>个性化推荐</strong>
          <small>关闭后将只展示公开最新与热帖</small>
        </span>
        <el-switch v-model="recommendationDraftEnabled" :disabled="recommendationSaving" />
      </div>
      <div class="recommendation-interest-groups" :class="{ 'is-disabled': !recommendationDraftEnabled }">
        <section v-for="item in categoriesWithId" :key="item.category.id" class="recommendation-interest-group">
          <h3>{{ item.category.name }}</h3>
          <el-checkbox-group v-model="recommendationDraftBoardIds" :disabled="!recommendationDraftEnabled || recommendationSaving">
            <el-checkbox v-for="board in item.boardList || []" :key="board.id" :value="Number(board.id)">
              {{ board.name }}
            </el-checkbox>
          </el-checkbox-group>
        </section>
      </div>
      <template #footer>
        <div class="recommendation-dialog-actions">
          <el-button text :disabled="recommendationSaving" @click="skipRecommendationPreferences">暂时跳过</el-button>
          <el-button text type="danger" :disabled="recommendationSaving" @click="resetRecommendationPreferences">清空设置</el-button>
          <el-button type="primary" :loading="recommendationSaving" @click="saveRecommendationPreferences">保存设置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
  <router-view />
</template>

<script setup>
defineOptions({ name: 'HomeFeed' })

import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Loading, MoreFilled } from '@element-plus/icons-vue'
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
  getRandomPastel,
  hotFeedList,
  hasRecommendationInterests,
  hideRecommendedArticle,
  isHotFeed,
  isPersonalizedRecommendation,
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
  openRecommendationPreferences,
  recommendationDialogVisible,
  recommendationDraftBoardIds,
  recommendationDraftEnabled,
  recommendationSaving,
  resetRecommendationPreferences,
  saveRecommendationPreferences,
  skipRecommendationPreferences,
  userStore,
} = useHomeShellContext()

const feedList = computed(() => (isHotFeed.value ? hotFeedList.value : articleList.value))

const openCategoryId = ref(null)

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
  openCategoryId.value = categoryId
}

function closeCategory() {
  openCategoryId.value = null
}

function toggleCategory(categoryId) {
  openCategoryId.value = openCategoryId.value === categoryId ? null : categoryId
}

function handleCategoryFocusOut(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  closeCategory()
}

onMounted(async () => {
  if (boardStore.categoryList.length === 0) await boardStore.fetchCategoryList()
  await ensureHomeFeedLoaded()
})

onActivated(() => {
  restoreFeedScroll()
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
