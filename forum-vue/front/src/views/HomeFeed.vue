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
            <span>{{ categoryTriggerLabel(item) }}</span>
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

    <main class="home-xhs-main home-xhs-main--feed">
      <div v-if="showCheckinHomeStrip" class="checkin-home-strip animate-fade-up">
        <el-card
          class="checkin-home-card"
          :class="{ 'checkin-home-done': checkinSummary.todaySigned }"
          shadow="hover"
          :body-style="{ padding: '12px 18px', cursor: 'pointer' }"
          @click="$router.push('/checkin')"
        >
          <div class="checkin-home-inner">
            <strong class="checkin-home-title">每日签到</strong>
            <span class="checkin-home-status" :class="{ 'is-done': checkinSummary.todaySigned }">
              {{ checkinSummary.todaySigned ? '已签到' : '待签到' }}
            </span>
            <span class="checkin-home-streak">
              连续 <strong>{{ checkinSummary.streakDays ?? 0 }}</strong> 天
            </span>
            <el-button
              class="checkin-home-action"
              round
              size="small"
              :disabled="checkinSummary.todaySigned"
              @click.stop="$router.push('/checkin')"
            >
              {{ checkinSummary.todaySigned ? '已完成' : '去签到' }}
            </el-button>
            <button
              type="button"
              class="checkin-home-close"
              aria-label="关闭签到提醒"
              @click.stop.prevent="handleDismissCheckin"
            >
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </el-card>
      </div>

      <div v-if="loading" class="home-masonry home-masonry--loading">
        <div v-for="i in 8" :key="i" class="home-masonry-item">
          <el-skeleton animated :rows="6" class="skeleton-card" />
        </div>
      </div>

      <div v-if="!loading && (feedList.length || showRecommendationInterestMask)" class="recommendation-feed-stage">
        <section v-if="showRecommendationInterestMask" class="recommendation-interest-prompt" aria-label="选择内容偏好">
          <div class="recommendation-interest-prompt__copy">
            <span class="recommendation-interest-prompt__eyebrow">为你定制</span>
            <h2>内容偏好</h2>
            <p>选择几个内容方向，系统也会参考你的收藏、回复和关注。</p>
          </div>
          <el-button type="primary" round @click="openRecommendationPreferences">设置偏好</el-button>
        </section>
        <div
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
                :class="{ 'note-card--question': isQuestionArticle(entry.article) }"
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
                <div
                  class="note-info"
                  :class="{
                    'note-info--question': isQuestionArticle(entry.article),
                    'note-info--resolved': isQuestionArticle(entry.article) && Number(entry.article?.questionStatus) === 1,
                  }"
                >
                  <div
                    v-if="isQuestionArticle(entry.article)"
                    class="question-card-meta"
                  >
                    <span
                      class="question-card-status"
                      :class="questionStatusClass(entry.article?.questionStatus)"
                    >
                      <span class="question-card-status__dot" />
                      {{ questionStatusLabel(entry.article?.questionStatus) }}
                    </span>
                  </div>
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
                      <span class="nickname" :title="entry.user?.nickname">{{ formatCardNickname(entry.user?.nickname) }}</span>
                    </div>
                    <div v-if="isQuestionArticle(entry.article)" class="question-answer-count">
                      <span>{{ entry.article?.replyCount || 0 }}回答</span>
                    </div>
                    <div v-else class="likes">
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
            <span class="home-hot-floating-copy">
              <strong>{{ entry.article?.title }}</strong>
              <small>{{ entry.article?.likeCount || 0 }} 赞 · {{ entry.article?.replyCount || 0 }} 评</small>
            </span>
            <span
              v-if="entry.trendDirection === 'UP'"
              class="home-hot-trend is-up"
              aria-label="热度上升"
              title="热度上升"
            >↑</span>
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
        <button
          v-else-if="isHomeFeed"
          type="button"
          class="home-hot-collapsed-button"
          :class="{ 'is-checkin-visible': showCheckinHomeStrip }"
          aria-label="展开热帖榜"
          @click="toggleHomeHotCollapsed"
        >
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
        description="这里还没有帖子哦"
      />
    </main>

    <InterestPreferenceDialog
      v-model:visible="recommendationDialogVisible"
      v-model:board-ids="recommendationDraftBoardIds"
      :categories="categoriesWithId"
      :saving="recommendationSaving"
      @save="saveRecommendationPreferences"
    />
  </div>
  <router-view />
</template>

<script setup src="./HomeFeed.js"></script>
<style scoped lang="scss" src="./HomeFeed.scss"></style>
