<template>
  <div class="gallery-section" :class="`gallery-section--${variant}`">
    <div class="editor-gallery-pane-head">
      <span class="editor-gallery-pane-title">笔记相册</span>
      <span class="editor-gallery-pane-count">{{ urls.length }}/{{ maxCount }}</span>
    </div>
    <p class="editor-gallery-pane-hint">展示在正文下方，有图时正文不少于 10 字</p>

    <template v-if="urls.length">
      <!-- Markdown：单行横向滚动 -->
      <div
        v-if="variant === 'strip'"
        class="editor-gallery-track"
        :class="{
          'is-overflow': stripOverflow,
          'is-fade-left': stripFadeLeft,
        }"
      >
        <div :ref="setItemsRef" class="editor-gallery-items" @scroll="onScroll">
          <div
            v-for="(url, idx) in urls"
            :key="url + '-' + idx"
            class="editor-gallery-slot editor-gallery-slot--filled"
          >
            <img :src="url" alt="" class="editor-gallery-slot-img" />
            <button
              type="button"
              class="editor-gallery-slot-remove"
              aria-label="移除图片"
              @click.stop="emit('remove', idx)"
            >
              ×
            </button>
          </div>
          <button
            v-if="canAdd"
            type="button"
            class="editor-gallery-slot editor-gallery-slot--add"
            aria-label="添加图片"
            @click="emit('open')"
          >
            <el-icon :size="22"><Plus /></el-icon>
          </button>
        </div>
      </div>

      <!-- 富文本：6 列网格铺满右侧 -->
      <div v-else class="editor-gallery-grid">
        <div
          v-for="(url, idx) in urls"
          :key="url + '-' + idx"
          class="editor-gallery-slot editor-gallery-slot--filled"
        >
          <img :src="url" alt="" class="editor-gallery-slot-img" />
          <button
            type="button"
            class="editor-gallery-slot-remove"
            aria-label="移除图片"
            @click.stop="emit('remove', idx)"
          >
            ×
          </button>
        </div>
        <button
          v-if="canAdd"
          type="button"
          class="editor-gallery-slot editor-gallery-slot--add"
          aria-label="添加图片"
          @click="emit('open')"
        >
          <el-icon :size="22"><Plus /></el-icon>
        </button>
      </div>
    </template>

    <button v-else type="button" class="editor-gallery-empty-cta" @click="emit('open')">
      <el-icon :size="28"><Picture /></el-icon>
      <span>点击添加图片</span>
    </button>
  </div>
</template>

<script setup>
import { Picture, Plus } from '@element-plus/icons-vue'
import { onBeforeUnmount } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'strip' },
  urls: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 15 },
  stripOverflow: { type: Boolean, default: false },
  stripFadeLeft: { type: Boolean, default: false },
  canAdd: { type: Boolean, default: true },
})

const emit = defineEmits(['open', 'remove', 'scroll', 'bind-ref'])

function setItemsRef(el) {
  if (props.variant === 'strip') {
    emit('bind-ref', el)
  }
}

function onScroll() {
  emit('scroll')
}

onBeforeUnmount(() => {
  if (props.variant === 'strip') {
    emit('bind-ref', null)
  }
})
</script>
