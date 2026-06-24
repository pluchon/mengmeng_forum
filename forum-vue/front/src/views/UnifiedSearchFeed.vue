<template>
  <div class="shell-main-stack shell-page-scroll unified-search-feed">
    <nav v-if="keyword" class="unified-search-nav" aria-label="搜索结果分类">
      <button
        type="button"
        class="unified-search-nav__btn"
        :class="{ 'is-active': searchTab === 'article' }"
        @click="setSearchTab('article')"
      >
        帖子
      </button>
      <button
        type="button"
        class="unified-search-nav__btn"
        :class="{ 'is-active': searchTab === 'user' }"
        @click="setSearchTab('user')"
      >
        用户
      </button>
      <span v-if="bannerText" class="unified-search-nav__hint">{{ bannerText }}</span>
    </nav>

    <main v-if="searchTab === 'article'" class="home-xhs-main home-xhs-main--feed">
      <div v-if="loading" class="home-masonry home-masonry--loading">
        <div v-for="i in 8" :key="i" class="home-masonry-item">
          <el-skeleton animated :rows="6" class="skeleton-card" />
        </div>
      </div>

      <div v-else-if="feedList.length" ref="masonryRef" class="home-masonry">
        <div
          v-for="(col, colIdx) in masonryColumns"
          :key="'s-col-' + colIdx"
          class="home-masonry-column"
        >
          <div
            v-for="entry in col"
            :key="entry.article?.id"
            class="home-masonry-item"
          >
            <el-card
              class="note-card note-card--masonry animate-fade-up"
              :body-style="{ padding: '0px' }"
              shadow="hover"
              @click="$router.push(`/article/${entry.article.id}`)"
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
                <div class="note-footer">
                  <div class="author">
                    <UserAvatarVip
                      :size="22"
                      :src="entry.user?.avatarUrl || defaultAvatar"
                      :vip-tier="Number(entry.user?.vipTier) || 0"
                      :vip-expire-at="entry.user?.vipExpireAt"
                    />
                    <span class="nickname">{{ entry.user?.nickname }}</span>
                  </div>
                  <div class="likes">
                    <LikeCountIcon />
                    <span>{{ entry.article?.likeCount }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </div>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next"
          background
          @current-change="runSearch"
        />
      </div>

      <el-empty
        v-if="!loading && keyword && hasSearched && !feedList.length"
        description="未检索到对应的帖子"
      />
    </main>

    <main v-else class="home-xhs-main unified-search-users">
      <div v-loading="loading" class="unified-search-user-list">
        <div
          v-for="u in userRecords"
          :key="u.id"
          class="unified-search-user-row"
          role="button"
          tabindex="0"
          @click="$router.push(`/profile/${u.id}`)"
          @keydown.enter.prevent="$router.push(`/profile/${u.id}`)"
        >
          <UserAvatarVip
            :size="48"
            :src="u.avatarUrl || defaultAvatar"
            :vip-tier="Number(u.vipTier) || 0"
            :vip-expire-at="u.vipExpireAt"
          />
          <div class="unified-search-user-meta">
            <div class="unified-search-user-name">{{ u.nickname || u.username }}</div>
          </div>
        </div>
        <el-empty v-if="!loading && keyword && hasSearched && !userRecords.length" description="未检索到对应的用户" />
      </div>
      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next"
          background
          @current-change="runSearch"
        />
      </div>
    </main>

    <el-empty v-if="!keyword" description="在顶部输入关键词开始综合搜索" class="unified-search-idle" />
  </div>
</template>

<script setup>
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useUnifiedSearchFeed } from '@scripts/views/UnifiedSearchFeed'

const {
  bannerText,
  defaultAvatar,
  feedList,
  getRandomPastel,
  coverImageUrl,
  hasSearched,
  keyword,
  loading,
  masonryColumns,
  masonryRef,
  pageNum,
  pageSize,
  placeholderMinHeight,
  runSearch,
  searchTab,
  setSearchTab,
  total,
  userRecords,
} = useUnifiedSearchFeed()
</script>

<style scoped>
.unified-search-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 4px 12px;
  position: sticky;
  top: 0;
  z-index: 5;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.unified-search-nav__btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 16px;
  background: #fff;
  font-size: 13px;
  font-weight: 700;
  color: #4e5969;
  cursor: pointer;
}

.unified-search-nav__btn.is-active {
  background: #1d2129;
  color: #fff;
  border-color: #1d2129;
}

.unified-search-nav__hint {
  margin-left: auto;
  font-size: 12px;
  color: #86909c;
}

.unified-search-user-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0 16px;
}

.unified-search-user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}

.unified-search-user-name {
  font-weight: 800;
  color: #1d2129;
}

.unified-search-user-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #86909c;
}

.unified-search-idle {
  margin-top: 48px;
}
</style>
