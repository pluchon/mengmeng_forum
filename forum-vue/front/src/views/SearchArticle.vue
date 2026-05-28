<template>
  <div class="red-home-container">
    <div class="main-content">
      <el-card
        v-if="bannerText"
        class="animate-fade-up search-hint-card"
        shadow="never"
        :body-style="{ padding: '14px 16px' }"
      >
        <div class="search-banner" :class="{ rag: source === 'rag' }">
          {{ bannerText }}
        </div>
      </el-card>

      <div v-loading="loading" class="search-result-list">
        <el-card
          v-for="item in records"
          :key="item.article?.id"
          class="result-card animate-fade-up"
          shadow="hover"
          @click="openArticle(item)"
        >
          <div class="result-title">{{ item.article?.title }}</div>
          <div class="result-meta">
            <span class="author">{{ item.user?.nickname || '匿名用户' }}</span>
            <span class="dot">·</span>
            <span>{{ formatForumDateTimeShanghai(item.article?.createTime) }}</span>
          </div>
        </el-card>

        <el-empty v-if="!loading && records.length === 0 && hasSearched" description="没有找到相关帖子" />
      </div>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next, jumper"
          background
          @current-change="doSearch"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useSearchArticle } from '@scripts/views/SearchArticle'
import { formatForumDateTimeShanghai } from '@/utils/datetime'

const {
  bannerText,
  doSearch,
  hasSearched,
  loading,
  openArticle,
  pageNum,
  pageSize,
  records,
  source,
  total,
} = useSearchArticle()
</script>

<style scoped>
.search-hint-card {
  margin-bottom: 0;
}

.search-banner {
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 13px;
  color: #4e5969;
  background: rgba(0, 0, 0, 0.03);
}

.search-banner.rag {
  background: rgba(255, 36, 66, 0.08);
  color: #a61b29;
}

.search-result-list {
  margin-top: 12px;
}

.result-card {
  margin-top: 10px;
  border-radius: 14px;
}

.result-title {
  font-weight: 800;
  font-size: 15px;
  color: #1d2129;
  line-height: 1.3;
}

.result-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #86909c;
}

.dot {
  margin: 0 6px;
}
</style>
