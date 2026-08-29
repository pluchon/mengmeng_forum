<template>
  <div class="video-section" :class="`video-section--${variant}`">
    <div class="editor-gallery-pane-head">
      <span class="editor-gallery-pane-count">{{ url ? '1/1' : '0/1' }}</span>
    </div>

    <div v-if="uploading" class="video-uploading" aria-live="polite">
      <el-icon class="media-upload-spinner" :size="36"><Loading /></el-icon>
      <p class="video-uploading__text">
        {{
          progress >= 100
            ? '已上传，服务器压缩并写入 OSS 中…'
            : progress > 0
              ? `视频上传中 ${progress}%`
              : '准备上传…'
        }}
      </p>
      <div class="video-actions">
        <button type="button" class="video-action-btn" @click="emit('cancel')">取消上传</button>
      </div>
    </div>

    <div v-else-if="uploadError" class="video-uploading video-uploading--error">
      <p class="video-uploading__text">{{ uploadError }}</p>
      <div class="video-actions">
        <button
          v-if="canRetry"
          type="button"
          class="video-action-btn video-action-btn--primary"
          @click="emit('retry')"
        >重试上传</button>
        <button type="button" class="video-action-btn" @click="emit('open')">重新选择视频</button>
      </div>
    </div>

    <template v-else-if="url">
      <div class="video-preview" :class="{ 'video-preview--fill': variant === 'grid' }">
        <video
          class="video-preview__player"
          :src="url"
          controls
          controlslist="nodownload noplaybackrate noremoteplayback"
          disablepictureinpicture
          preload="metadata"
          @contextmenu.prevent
        />
        <button type="button" class="video-remove" @click="emit('remove')">移除视频</button>
      </div>
    </template>

    <button v-else type="button" class="video-empty-cta" @click="emit('open')">
      <el-icon :size="28"><VideoCamera /></el-icon>
      <span>点击上传视频</span>
    </button>
  </div>
</template>

<script setup>
import { Loading, VideoCamera } from '@element-plus/icons-vue'

defineProps({
  variant: { type: String, default: 'grid' },
  url: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 },
  uploadError: { type: String, default: '' },
  canRetry: { type: Boolean, default: false },
})

const emit = defineEmits(['open', 'remove', 'cancel', 'retry'])
</script>

<style scoped>
.video-section {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
}
.video-section--grid {
  flex: 1;
}
.video-preview {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.video-preview__player {
  width: 100%;
  max-height: 360px;
  border-radius: 12px;
  background: #0b0d12;
}
.video-preview--fill {
  position: relative;
  height: 178px;
}
.video-preview--fill .video-preview__player {
  height: 100%;
  max-height: none;
  object-fit: contain;
}
.video-remove {
  height: 40px;
  border: none;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.06);
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
}
.video-preview--fill .video-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  width: auto;
  height: 28px;
  padding: 0 9px;
  border-radius: 8px;
  background: rgba(21, 18, 24, 0.62);
  color: #fff;
  font-size: 11px;
}
.video-remove:hover {
  background: rgba(0, 0, 0, 0.12);
}
.video-preview--fill .video-remove:hover {
  background: rgba(21, 18, 24, 0.78);
  color: #fff;
}

/* 上传中 / 失败态的操作按钮：常规长方形，横向并排 */
.video-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.video-action-btn {
  height: 34px;
  padding: 0 18px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  background: #fff;
  color: var(--text-primary, #1a1a1a);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  line-height: 32px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.video-action-btn:hover {
  background: rgba(0, 0, 0, 0.04);
  border-color: rgba(0, 0, 0, 0.2);
}
.video-action-btn--primary {
  border-color: #eb7da9;
  background: #eb7da9;
  color: #fff;
}
.video-action-btn--primary:hover {
  background: #dc5f91;
  border-color: #dc5f91;
}
.video-uploading {
  padding: 28px 16px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.04);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-height: 120px;
  justify-content: center;
}
.video-uploading--error {
  background: rgba(245, 108, 108, 0.08);
}
.video-uploading__text {
  margin: 0 0 8px;
  font-weight: 700;
  color: var(--text-primary, #1a1a1a);
}
.video-empty-cta {
  display: flex;
  min-height: 0;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin: 0;
  border: 1px dashed #eb7da9;
  border-radius: 10px;
  background: #fff9fc;
  color: #e46498;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.video-empty-cta:hover {
  border-color: #dc5f91;
  background: #fff1f7;
}</style>

