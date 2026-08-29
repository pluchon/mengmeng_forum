<template>
  <div class="shell-main-stack shell-page-scroll home-feed-page">
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

    <main
      class="home-xhs-main home-xhs-main--feed"
      :class="{
        'home-xhs-main--load-error': !loading && !!feedError,
        'home-xhs-main--loading': loading && !feedList.length,
      }"
    >
      <div
        v-if="!loading && feedList.length"
        class="recommendation-feed-stage"
      >
        <Masonry
          :items="masonryCards"
          :column-width="220"
          :max-columns="6"
          :gap="16"
          :reload-key="masonryReloadKey"
          :duration="0.72"
          ease="power3.out"
          :stagger="0.05"
          animate-from="bottom"
        >
          <template #default="{ item }">
            <el-card
              class="note-card note-card--masonry"
              :class="cardTypeClass(item.article)"
              :style="cardOutlineStyle(item)"
              :body-style="{ padding: '0px' }"
              shadow="never"
              @click="openArticle(item.entry, $event)"
            >
              <div
                class="note-cover note-cover--fluid"
                :class="{ 'is-aspect-locked': !!coverAspectById[item.article?.id] }"
                :style="coverAspectStyle(item.article?.id)"
              >
                <img
                  v-if="displayCoverUrl(item.entry)"
                  class="note-cover-img"
                  :class="{ 'is-loaded': coverLoadedById[item.article?.id] }"
                  :src="displayCoverUrl(item.entry)"
                  alt=""
                  loading="lazy"
                  @load="onCoverLoad(item.article?.id, item.entry, $event)"
                  @error="onCoverError(item.article?.id)"
                />
                <div
                  v-else
                  class="note-cover-placeholder"
                  :class="{ 'note-cover-placeholder--video': isVideoArticle(item.article) }"
                  :style="{
                    background: getRandomPastel(),
                    minHeight: placeholderMinHeight(item.article?.id),
                  }"
                >
                  <span class="cover-title">{{ (item.article?.title || '').substring(0, 12) }}</span>
                </div>
                <div
                  v-if="isVideoArticle(item.article)"
                  class="note-cover-play"
                  aria-hidden="true"
                />
                <span
                  v-if="isVideoArticle(item.article) && videoDurationLabel(item.article?.id)"
                  class="note-cover-badge"
                >
                  {{ videoDurationLabel(item.article?.id) }}
                </span>
                <span
                  v-else-if="!isVideoArticle(item.article) && !isQuestionArticle(item.article) && Number(item.entry?.imageCount) > 1"
                  class="note-cover-badge note-cover-badge--images"
                >
                  <el-icon class="note-cover-badge__icon"><Picture /></el-icon>
                  {{ item.entry?.imageCount }}
                </span>
              </div>
              <div
                class="note-info"
                :class="{
                  'note-info--question': isQuestionArticle(item.article),
                  'note-info--resolved': isQuestionArticle(item.article) && Number(item.article?.questionStatus) === 1,
                  'note-info--waiting': isQuestionArticle(item.article) && Number(item.article?.questionStatus) !== 1,
                }"
              >
                <div
                  v-if="isQuestionArticle(item.article)"
                  class="note-title-row"
                >
                  <h3 class="note-title">{{ item.article?.title }}</h3>
                  <span
                    class="question-card-status"
                    :class="questionStatusClass(item.article?.questionStatus)"
                  >
                    <span class="question-card-status__dot" />
                    {{ questionStatusLabel(item.article?.questionStatus) }}
                  </span>
                </div>
                <h3 v-else class="note-title">{{ item.article?.title }}</h3>
                <p
                  v-if="isRecommendationFeed && item.entry?.reasonMessage"
                  class="note-recommend-reason"
                >
                  {{ item.entry.reasonMessage }}
                </p>
                <div class="note-footer">
                  <div class="author">
                    <UserAvatarVip
                      :size="22"
                      :src="ossAvatarUrl(item.entry?.user?.avatarUrl) || defaultAvatar"                    />
                    <span class="nickname" :title="item.entry?.user?.nickname || '匿名用户'">{{ formatCardNickname(item.entry?.user?.nickname || '匿名用户') }}</span>
                  </div>
                  <div class="likes">
                    <LikeCountIcon />
                    <span>{{ item.article?.likeCount || 0 }}</span>
                  </div>
                </div>
              </div>
              <div v-if="isNotInterestedArticle(item.article?.id)" class="note-card-not-interested-mask">
                不感兴趣
              </div>
            </el-card>
          </template>
        </Masonry>
      </div>

      <div v-if="loading && feedList.length" class="home-feed-loading-more" aria-live="polite">
        <el-icon class="home-feed-loading-spin" :size="20"><Loading /></el-icon>
        <span>正在加载更多…</span>
      </div>

      <section
        v-if="!loading && feedError"
        class="home-feed-load-error"
        role="alert"
        aria-live="polite"
      >
        <img
          class="home-feed-load-error__art"
          :src="feedLoadErrorArt"
          alt=""
          width="264"
          height="208"
          decoding="async"
        />
        <h2 class="home-feed-load-error__title">
          {{ feedLoadErrorTitle }}
        </h2>
        <button
          type="button"
          class="home-feed-load-error__retry"
          @click="fetchArticles(pageNum)"
        >
          <el-icon class="home-feed-load-error__retry-icon"><Refresh /></el-icon>
          <span>重新加载</span>
        </button>
      </section>

      <div class="pagination-wrap">
        <AppPagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          :pager-count="7"
          :show-jumper="true"
          @current-change="fetchArticles"
        />
      </div>

      <div
        v-if="!loading && !feedError && feedList.length === 0"
        class="home-feed-empty"
      >
        <img :src="boardEmptyImageUrl" alt="该板块暂无帖子">
        <p>该板块暂无帖子</p>
      </div>
    </main>

  </div>
</template>

<script setup src="./HomeFeed.js"></script>
<style scoped lang="scss" src="./HomeFeed.scss"></style>
