<template>
  <el-popover
    v-model:visible="visible"
    placement="top-start"
    :width="250"
    trigger="manual"
    :teleported="true"
    :popper-style="{ zIndex: 4200 }"
    popper-class="comment-shop-emoji-popper"
    @show="loadDetail"
  >
    <template #reference>
      <button
        type="button"
        class="comment-media-item comment-media-item--emoji"
        aria-label="查看表情"
        :aria-expanded="visible"
        @click.stop="togglePreview"
      >
        <img :src="mediaUrl" alt="" class="comment-media-img">
      </button>
    </template>

    <div class="comment-shop-emoji-popover">
      <img :src="mediaUrl" alt="" class="comment-shop-emoji-popover__preview">
      <div v-if="loading" class="comment-shop-emoji-popover__state">加载中…</div>
      <div
        v-else-if="errorText"
        class="comment-shop-emoji-popover__state"
        :class="offlineNotice ? 'is-offline' : 'is-error'"
      >{{ errorText }}</div>
      <button
        v-else-if="detail"
        type="button"
        class="comment-shop-emoji-popover__pack"
        @click.stop="openShop"
      >
        <img :src="detail.coverUrl || detail.imageUrls?.[0]" alt="" class="comment-shop-emoji-popover__cover">
        <span>{{ detail.name || '查看所属表情包' }}</span>
      </button>
      <div v-else class="comment-shop-emoji-popover__state">暂无表情包信息</div>
    </div>
  </el-popover>
</template>

<script setup src="@scripts/components/article/CommentShopEmojiPopover.js"></script>
<style scoped lang="scss" src="./CommentShopEmojiPopover.scss"></style>
