<template>
  <el-dialog
    :model-value="visible"
    append-to-body
    class="mascot-related-dialog"
    width="min(920px, 94vw)"
    align-center
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="mascot-related-dialog__body">
      <button
        v-for="item in items"
        :key="item.article?.id"
        type="button"
        class="mascot-related-dialog__item"
        @click="openArticle(item.article?.id)"
      >
        <div class="mascot-related-dialog__cover" :style="coverStyle(item.article)">
          <img v-if="item.article?.coverImg" :src="item.article.coverImg" alt="">
        </div>
        <div class="mascot-related-dialog__main">
          <div class="mascot-related-dialog__title">{{ item.article?.title }}</div>
          <div class="mascot-related-dialog__snippet">{{ item.article?.content }}</div>
        </div>
        <div class="mascot-related-dialog__stats">
          <span>赞 {{ item.article?.likeCount ?? 0 }}</span>
          <span>评 {{ item.article?.replyCount ?? 0 }}</span>
          <span>藏 {{ item.article?.favoriteCount ?? 0 }}</span>
        </div>
        <div class="mascot-related-dialog__author">
          <img :src="item.author?.avatarUrl || DEFAULT_AVATAR" alt="">
          <span>{{ item.author?.nickname || '匿名用户' }}</span>
        </div>
      </button>
      <el-empty v-if="!items.length" description="暂无相关帖子" />
    </div>
  </el-dialog>
</template>

<script setup>
import { useMascotRelatedArticlesDialog } from '@scripts/components/mascot/MascotRelatedArticlesDialog'

const props = defineProps({
  visible: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'open-article'])
const { DEFAULT_AVATAR, coverStyle, openArticle } = useMascotRelatedArticlesDialog(props, emit)
</script>

<style scoped src="./MascotRelatedArticlesDialog.css"></style>
