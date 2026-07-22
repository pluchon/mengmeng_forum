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

    <main v-if="searchTab === 'article'" class="home-xhs-main home-xhs-main--feed">
      <div v-if="loading" class="home-masonry home-masonry--loading">
        <div v-for="i in 8" :key="i" class="home-masonry-item">
          <el-skeleton animated :rows="6" class="skeleton-card" />
        </div>
      </div>

      <div v-else-if="feedList.length" ref="masonryRef" class="home-masonry">
        <div
          v-for="(col, colIdx) in masonryColumns"
          :key="`search-column-${colIdx}`"
          class="home-masonry-column"
        >
          <div v-for="entry in col" :key="entry.article?.id" class="home-masonry-item">
            <SearchArticleCard :entry="entry" @open="openArticle" />
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
        <SearchUserRow
          v-for="user in userRecords"
          :key="user.id"
          :user="user"
          :saving="isFollowSaving(user.id)"
          :is-self="Number(user.id) === Number(userStore.id)"
          @open="openUser"
          @toggle-follow="toggleUserFollow"
        />
        <el-empty
          v-if="!loading && keyword && hasSearched && !userRecords.length"
          description="未检索到对应的用户"
        />
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

<script setup src="@scripts/views/UnifiedSearchFeed.js"></script>
<style scoped lang="scss" src="./UnifiedSearchFeed.scss"></style>
