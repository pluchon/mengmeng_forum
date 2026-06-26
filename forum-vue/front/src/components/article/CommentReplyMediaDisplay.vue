<template>
  <div v-if="mediaList?.length" class="comment-media-row">
    <template v-for="(item, idx) in mediaList" :key="`${item.mediaUrl}-${idx}`">
      <el-image
        v-if="isImageItem(item)"
        :src="item.mediaUrl"
        :preview-src-list="imagePreviewUrls"
        :initial-index="imageIndexMap[idx]"
        preview-teleported
        fit="contain"
        class="comment-media-item comment-media-item--image"
      />
      <div
        v-else
        class="comment-media-item comment-media-item--emoji"
        @click="onEmojiClick(item)"
      >
        <img :src="item.mediaUrl" alt="" class="comment-media-img">
        <img :src="emojiPackIconUrl" alt="" class="comment-media-emoji-badge" aria-hidden="true">
      </div>
    </template>
  </div>
</template>

<script setup src="@scripts/components/article/CommentReplyMediaDisplay.js"></script>
<style scoped src="./CommentReplyMediaDisplay.css"></style>
