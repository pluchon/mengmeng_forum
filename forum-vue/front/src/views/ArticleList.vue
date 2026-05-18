<template>
  <div class="home-container">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>{{ boardName }} 版块的帖子</span>
          <el-button type="primary" @click="$router.push('/article/create')">发帖</el-button>
        </div>
      </template>
      <div v-if="loading">
        <el-skeleton :rows="5" animated />
      </div>
      <template v-else>
        <el-empty v-if="articleList.length === 0" description="暂无帖子" />
        <el-card
          v-for="item in articleList"
          :key="item.article.id"
          class="article-card"
          shadow="hover"
          @click="$router.push(`/article/${item.article.id}`)"
        >
          <div class="article-card-inner">
            <el-avatar :src="item.user?.avatarUrl" :size="40" />
            <div class="article-info">
              <div class="article-title">{{ item.article.title }}</div>
              <div class="article-meta">
                <span>{{ item.user?.nickname }}</span>
                <span>{{ item.article.createTime }}</span>
              </div>
            </div>
          </div>
        </el-card>
        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="page"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next"
          background
          style="margin-top: 16px; justify-content: center"
          @current-change="fetch"
        />
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { useArticleList } from '@scripts/views/ArticleList'

const {
  articleList,
  boardName,
  fetch,
  loading,
  page,
  pageSize,
  total,
} = useArticleList()
</script>

<style scoped src="@/assets/styles/article.css"></style>
