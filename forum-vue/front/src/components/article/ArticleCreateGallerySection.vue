<template>
  <div class="gallery-section" :class="`gallery-section--${variant}`">
    <div class="editor-gallery-pane-head">
      <span class="editor-gallery-pane-count">{{ displayCount }}/{{ maxCount }}</span>
    </div>

    <!-- Markdown：单行横向滚动 -->
    <div
      v-if="variant === 'strip' && (urls.length || pendingCount > 0)"
      class="editor-gallery-track"
      :class="{
        'is-overflow': stripOverflow,
        'is-fade-left': stripFadeLeft,
      }"
    >
      <div :ref="setItemsRef" class="editor-gallery-items" @scroll="onScroll">
        <div
          v-for="(url, idx) in urls"
          :key="'url-' + idx + '-' + url"
          class="editor-gallery-slot editor-gallery-slot--filled"
        >
          <el-image
            :src="url"
            :preview-src-list="urls"
            :initial-index="idx"
            preview-teleported
            fit="contain"
            class="editor-gallery-slot-img"
          />
          <button
            type="button"
            class="editor-gallery-slot-remove"
            aria-label="移除图片"
            @click.stop="emit('remove', idx)"
          >
            ×
          </button>
        </div>
        <div
          v-for="n in pendingCount"
          :key="'pending-' + n"
          class="editor-gallery-slot editor-gallery-slot--loading"
          aria-live="polite"
        >
          <el-icon class="media-upload-spinner" :size="26"><Loading /></el-icon>
          <span class="media-upload-label">{{ uploadLabel }}</span>
        </div>
        <button
          v-if="canAdd && pendingCount <= 0"
          type="button"
          class="editor-gallery-slot editor-gallery-slot--add"
          aria-label="添加图片"
          @click="emit('open')"
        >
          <el-icon :size="22"><Plus /></el-icon>
        </button>
      </div>
    </div>

    <!-- 富文本：固定网格 -->
    <div v-else-if="variant === 'grid' && (urls.length || pendingCount > 0)" class="editor-gallery-grid">
      <div
        v-for="(url, idx) in urls"
        :key="'url-' + idx + '-' + url"
        class="editor-gallery-slot editor-gallery-slot--filled"
      >
        <el-image
          :src="url"
          :preview-src-list="urls"
          :initial-index="idx"
          preview-teleported
          fit="contain"
          class="editor-gallery-slot-img"
        />
        <button
          type="button"
          class="editor-gallery-slot-remove"
          aria-label="移除图片"
          @click.stop="emit('remove', idx)"
        >
          ×
        </button>
      </div>
      <div
        v-for="n in pendingCount"
        :key="'pending-' + n"
        class="editor-gallery-slot editor-gallery-slot--loading"
        aria-live="polite"
      >
        <el-icon class="media-upload-spinner" :size="26"><Loading /></el-icon>
        <span class="media-upload-label">{{ uploadLabel }}</span>
      </div>
      <button
        v-if="canAdd && pendingCount <= 0"
        type="button"
        class="editor-gallery-slot editor-gallery-slot--add"
        aria-label="添加图片"
        @click="emit('open')"
      >
        <el-icon :size="22"><Plus /></el-icon>
      </button>
    </div>

    <div
      v-else-if="pendingCount > 0"
      class="editor-gallery-empty-cta editor-gallery-empty-cta--loading"
      aria-live="polite"
    >
      <el-icon class="media-upload-spinner" :size="32"><Loading /></el-icon>
      <span>{{ uploadLabel }}（{{ pendingCount }}）</span>
    </div>

    <button v-else type="button" class="editor-gallery-empty-cta" @click="emit('open')">
      <el-icon :size="28"><Picture /></el-icon>
      <span>点击添加图片</span>
    </button>
  </div>
</template>

<script setup>
import { Loading, Picture, Plus } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'strip' },
  urls: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 15 },
  stripOverflow: { type: Boolean, default: false },
  stripFadeLeft: { type: Boolean, default: false },
  canAdd: { type: Boolean, default: true },
  uploading: { type: Boolean, default: false },
  pendingCount: { type: Number, default: 0 },
  uploadLabel: { type: String, default: '上传中…' },
})

const emit = defineEmits(['open', 'remove', 'scroll', 'bind-ref'])

const displayCount = computed(() => props.urls.length + Math.max(0, props.pendingCount || 0))

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
