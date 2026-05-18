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
          :key="item.id"
          class="result-card user-card animate-fade-up"
          shadow="hover"
          @click="openProfile(item)"
        >
          <div class="user-row">
            <el-avatar :size="48" :src="item.avatarUrl || DEFAULT_AVATAR" />
            <div class="user-text">
              <div class="user-name">{{ item.nickname || '未设置昵称' }}</div>
              <div v-if="item.remark" class="user-remark">{{ item.remark }}</div>
            </div>
          </div>
        </el-card>

        <el-empty v-if="!loading && records.length === 0 && hasSearched" description="没有找到相关用户" />
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
import { DEFAULT_AVATAR } from '@/utils/constants'
import { useSearchUser } from '@scripts/views/SearchUser'

const {
  bannerText,
  doSearch,
  hasSearched,
  loading,
  openProfile,
  pageNum,
  pageSize,
  records,
  source,
  total,
} = useSearchUser()
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
  background: rgba(64, 158, 255, 0.1);
  color: #1d5fbf;
}

.search-result-list {
  margin-top: 12px;
}

.result-card {
  margin-top: 10px;
  border-radius: 14px;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-text {
  min-width: 0;
  flex: 1;
}

.user-name {
  font-weight: 700;
  font-size: 15px;
  color: #1d2129;
  line-height: 1.3;
}

.user-remark {
  margin-top: 6px;
  font-size: 13px;
  color: #86909c;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
