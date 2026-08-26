<template>
  <div class="comment-expandable-text">
    <template v-if="!needsCollapse">
      <div
        v-if="renderHtml"
        class="comment-expandable-text__body"
        v-html="renderHtml(content)"
      />
      <div v-else class="comment-expandable-text__body">{{ plainText }}</div>
    </template>
    <template v-else-if="!expanded">
      <span class="comment-expandable-text__body">{{ collapsedText }}</span>
      <button type="button" class="comment-expandable-text__toggle" @click="toggleExpanded">
        展开
      </button>
    </template>
    <template v-else>
      <div
        v-if="renderHtml"
        class="comment-expandable-text__body"
        v-html="renderHtml(content)"
      />
      <div v-else class="comment-expandable-text__body">{{ plainText }}</div>
      <button type="button" class="comment-expandable-text__toggle" @click="toggleExpanded">
        收起
      </button>
    </template>
  </div>
</template>

<script setup src="@scripts/components/article/CommentExpandableText.js"></script>
<style scoped lang="scss" src="./CommentExpandableText.scss"></style>
