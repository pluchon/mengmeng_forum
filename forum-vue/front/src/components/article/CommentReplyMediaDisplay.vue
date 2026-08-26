<template>
  <div v-if="mediaList?.length" class="comment-media-wrap">
    <button
      v-if="canScrollLeft"
      type="button"
      class="comment-media-nav comment-media-nav--left"
      aria-label="向左查看"
      @click.stop="scrollByDir(-1)"
    >
      <el-icon :size="14"><ArrowLeft /></el-icon>
    </button>
    <div
      ref="trackRef"
      class="comment-media-row"
      :class="{
        'has-fade-left': canScrollLeft,
        'has-fade-right': canScrollRight,
      }"
      @scroll.passive="updateScrollState"
    >
      <template v-for="(item, idx) in mediaList" :key="`${item.mediaUrl}-${idx}`">
        <el-image
          v-if="isImageItem(item)"
          :src="item.mediaUrl"
          :preview-src-list="imagePreviewUrls"
          :initial-index="imageIndexMap[idx]"
          preview-teleported
          fit="contain"
          class="comment-media-item comment-media-item--image"
          @load="updateScrollState"
        />
        <CommentShopEmojiPopover
          v-else
          :media-url="item.mediaUrl"
          :shop-id="item.shopId"
          @open-shop="(id) => emit('open-shop', id)"
        />
      </template>
    </div>
    <button
      v-if="canScrollRight"
      type="button"
      class="comment-media-nav comment-media-nav--right"
      aria-label="向右查看"
      @click.stop="scrollByDir(1)"
    >
      <el-icon :size="14"><ArrowRight /></el-icon>
    </button>
  </div>
</template>

<script setup src="@scripts/components/article/CommentReplyMediaDisplay.js"></script>
<style scoped lang="scss" src="./CommentReplyMediaDisplay.scss"></style>
