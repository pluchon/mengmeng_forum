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
      <p class="video-uploading__hint">200MB 以下直接上传；更大视频会后台处理，请勿重复点击</p>
    </div>

    <div v-else-if="uploadError" class="video-uploading video-uploading--error">
      <p class="video-uploading__text">{{ uploadError }}</p>
      <button type="button" class="editor-gallery-empty-cta" @click="emit('open')">重新选择视频</button>
    </div>

    <template v-else-if="url">
      <div class="video-preview">
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

    <button v-else type="button" class="editor-gallery-empty-cta" @click="emit('open')">
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
})

const emit = defineEmits(['open', 'remove'])
</script>

<style scoped>
.video-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
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
.video-remove {
  height: 40px;
  border: none;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.06);
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
}
.video-remove:hover {
  background: var(--primary-pale);
  color: var(--primary-red);
  transform: translateY(-1px);
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
.video-uploading__hint {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary, #666);
}
</style>

