<template>
  <!-- ArticleEdit 复用 ArticleCreate 的逻辑 -->
  <ArticleCreate />
</template>

<script setup>
import { useArticleEdit } from '@scripts/views/ArticleEdit'

const {
  ArticleCreate,
} = useArticleEdit()
</script>

<style scoped src='@/assets/styles/article.css'></style>
