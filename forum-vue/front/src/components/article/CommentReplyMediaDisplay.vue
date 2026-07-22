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
      <CommentShopEmojiPopover
        v-else
        :media-url="item.mediaUrl"
        :shop-id="item.shopId"
        @open-shop="(id) => emit('open-shop', id)"
      />
    </template>
  </div>
</template>

<script setup src="@scripts/components/article/CommentReplyMediaDisplay.js"></script>
<style scoped lang="scss" src="./CommentReplyMediaDisplay.scss"></style>
