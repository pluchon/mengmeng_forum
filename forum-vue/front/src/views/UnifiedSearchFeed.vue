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
    </nav>

    <main
      v-if="searchTab === 'article'"
      class="home-xhs-main home-xhs-main--feed"
      :class="{ 'home-xhs-main--loading': loading }"
    >
      <div
        v-if="preferAiRag && loading"
        class="unified-search-ai-loading"
        role="status"
        aria-live="polite"
        aria-label="AI 检索中"
      >
        <span class="unified-search-ai-star unified-search-ai-star--one" aria-hidden="true">✦</span>
        <span class="unified-search-ai-star unified-search-ai-star--two" aria-hidden="true">✧</span>
        <span class="unified-search-ai-star unified-search-ai-star--three" aria-hidden="true">✦</span>
        <span class="unified-search-ai-star unified-search-ai-star--four" aria-hidden="true">✧</span>
        <strong>AI 检索中......</strong>
      </div>

      <div
        v-else-if="loading"
        class="unified-search-spin-loading"
        role="status"
        aria-live="polite"
        aria-label="搜索中"
      >
        <span class="unified-search-spin" aria-hidden="true" />
      </div>

      <div
        v-else-if="feedList.length"
        class="recommendation-feed-stage"
      >
        <Masonry
          :items="masonryCards"
          :column-width="220"
          :gap="16"
          :max-columns="6"
          :default-item-height="320"
          :reload-key="masonryReloadKey"
          :duration="0.72"
          ease="power3.out"
          :stagger="0.05"
          animate-from="bottom"
        >
          <template #default="{ item }">
            <SearchArticleCard :entry="item.entry" @open="openArticle" />
          </template>
        </Masonry>
      </div>

      <div v-if="!loading && total > pageSize" class="pagination-wrap">
        <AppPagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          @current-change="runSearch"
        />
      </div>

      <div
        v-if="!loading && keyword && hasSearched && !feedList.length"
        class="unified-search-empty"
      >
        <img :src="articleNotFoundImageUrl" alt="没有找到相关帖子">
        <p>没有找到相关帖子呀……</p>
      </div>
    </main>

    <main
      v-else
      class="home-xhs-main unified-search-users"
      :class="{ 'home-xhs-main--loading': loading }"
    >
      <div
        v-if="preferAiRag && loading"
        class="unified-search-ai-loading"
        role="status"
        aria-live="polite"
        aria-label="AI 检索中"
      >
        <span class="unified-search-ai-star unified-search-ai-star--one" aria-hidden="true">✦</span>
        <span class="unified-search-ai-star unified-search-ai-star--two" aria-hidden="true">✧</span>
        <span class="unified-search-ai-star unified-search-ai-star--three" aria-hidden="true">✦</span>
        <span class="unified-search-ai-star unified-search-ai-star--four" aria-hidden="true">✧</span>
        <strong>AI 检索中......</strong>
      </div>
      <div
        v-else-if="loading"
        class="unified-search-spin-loading"
        role="status"
        aria-live="polite"
        aria-label="搜索中"
      >
        <span class="unified-search-spin" aria-hidden="true" />
      </div>
      <div v-else class="unified-search-user-list">
        <SearchUserRow
          v-for="user in userRecords"
          :key="user.id"
          :user="user"
          :saving="isFollowSaving(user.id)"
          :is-self="Number(user.id) === Number(userStore.id)"
          @open="openUser"
          @toggle-follow="toggleUserFollow"
        />
        <div
          v-if="keyword && hasSearched && !userRecords.length"
          class="unified-search-empty unified-search-empty--user"
        >
          <img :src="userNotFoundImageUrl" alt="没有找到相关用户">
          <p>没有找到相关用户呀……</p>
        </div>
      </div>
      <div v-if="!loading && total > pageSize" class="pagination-wrap">
        <AppPagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          @current-change="runSearch"
        />
      </div>
    </main>

    <el-empty v-if="!keyword" description="在顶部输入关键词开始综合搜索" class="unified-search-idle" />
  </div>
</template>

<script setup src="@scripts/views/UnifiedSearchFeed.js"></script>
<style scoped lang="scss" src="./UnifiedSearchFeed.scss"></style>
