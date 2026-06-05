<template>
  <div class="editor-page shell-page-scroll">
    <div class="editor-page-bg" aria-hidden="true" />
    <div class="editor-page-inner">
      <div class="editor-action-bar">
        <div class="editor-action-title-wrap">
          <span class="editor-action-accent" aria-hidden="true" />
          <h2 class="editor-action-title">{{ isEdit ? '编辑帖子' : '创作笔记' }}</h2>
        </div>
        <div class="editor-action-btns">
          <el-button class="editor-btn-ghost" @click="handleCancel">取消</el-button>
          <el-button class="editor-btn-outline" :loading="submitting" @click="handleSaveDraft">
            {{ isEdit ? '保存并下一步' : '存草稿' }}
          </el-button>
          <el-button
            v-if="!isEdit"
            class="editor-btn-primary"
            :loading="submitting"
            @click="handlePublish"
          >
            提交审核
          </el-button>
        </div>
      </div>

      <p class="cover-deferred-hint">
        <el-icon class="cover-deferred-hint-icon"><InfoFilled /></el-icon>
        <span>内容保存后将进入封面页；完成后请提交异步审核，通过后将自动发布。</span>
      </p>

      <el-form :model="form" label-position="top" class="editor-form">
        <div class="editor-field-card editor-meta-card">
          <div class="editor-meta-row">
            <div class="editor-meta-col">
              <div class="editor-field-label">
                <span class="editor-req">*</span> 标题
              </div>
              <el-input
                v-model="form.title"
                placeholder="填写标题会有更多赞哦"
                maxlength="50"
                show-word-limit
                class="editor-title-input"
              />
            </div>
            <div class="editor-meta-col">
              <div class="editor-field-label">
                <span class="editor-req">*</span> 发布至版块
              </div>
              <el-cascader
                v-model="selectedBoard"
                :options="cascaderOptions"
                :props="{ label: 'label', value: 'value', children: 'children' }"
                placeholder="请选择合适的版块"
                class="editor-board-cascader"
                @change="handleBoardChange"
              />
            </div>
          </div>
        </div>

        <div class="editor-content-card">
          <div class="editor-content-head">
            <div class="editor-field-label editor-field-label--inline">
              <span class="editor-req">*</span> 内容
            </div>
            <div class="editor-content-head-tools">
            <div class="editor-media-toggle">
              <button
                type="button"
                class="editor-media-toggle__item"
                :class="{ 'is-active': mediaMode === 'gallery' }"
                @click="setMediaMode('gallery')"
              >
                <el-icon><Picture /></el-icon>
                笔记相册
              </button>
              <button
                type="button"
                class="editor-media-toggle__item"
                :class="{ 'is-active': mediaMode === 'video' }"
                @click="setMediaMode('video')"
              >
                <el-icon><VideoCamera /></el-icon>
                笔记视频
              </button>
              <span class="editor-media-toggle__thumb" :class="{ 'is-right': mediaMode === 'video' }" />
            </div>
            <ArticleAiWriteAssist
              :editor-mode="editorMode"
              :title="form.title"
              @apply="applyAiContent"
            />
            <div class="editor-mode-seg" role="tablist" aria-label="编辑器模式">
              <button
                type="button"
                role="tab"
                class="editor-mode-seg-btn"
                :class="{ 'is-active': editorMode === 'rich' }"
                :aria-selected="editorMode === 'rich'"
                @click="setEditorMode('rich')"
              >
                富文本
              </button>
              <button
                type="button"
                role="tab"
                class="editor-mode-seg-btn"
                :class="{ 'is-active': editorMode === 'markdown' }"
                :aria-selected="editorMode === 'markdown'"
                @click="setEditorMode('markdown')"
              >
                Markdown
              </button>
              <span class="editor-mode-seg__thumb" :class="{ 'is-right': editorMode === 'markdown' }" />
            </div>
            </div>
          </div>

          <div v-if="editorMode === 'rich'" class="editor-workspace editor-workspace--single">
            <div class="editor-container rich-container">
              <WangEditor
                v-model="form.content"
                min-height="300px"
                toolbar-suppress-image
                toolbar-slim
              />
            </div>
          </div>

          <div v-else class="editor-md-workspace">
            <div class="editor-container md-container">
              <div class="md-toolbar">
                <template v-if="isEdit">
                  <el-tooltip content="插入图片" placement="top">
                    <button type="button" class="md-tb-btn" @click="handleMdInsertImage">
                      <el-icon><Picture /></el-icon>
                    </button>
                  </el-tooltip>
                  <div class="divider" />
                </template>
                <button type="button" class="md-tb-btn" @click="mdWrap('**', '**')"><b>B</b></button>
                <button type="button" class="md-tb-btn md-tb-btn--italic" @click="mdWrap('*', '*')"><i>I</i></button>
              </div>
              <div class="md-body">
                <el-input
                  ref="mdTextareaRef"
                  v-model="form.content"
                  type="textarea"
                  :rows="16"
                  placeholder="分享你的故事吧…"
                  class="md-input"
                  @keydown="onMdKeydown"
                />
                <div class="md-preview prose" v-html="renderedPreview" />
              </div>
            </div>
          </div>
        </div>

        <div class="editor-field-card editor-tag-card">
          <ArticleTagEditor
            v-model="tagIds"
            :board-id="form.boardId"
            :title="form.title"
            :content="form.content"
            :label="mediaMode === 'video' ? '视频标签' : '帖子标签'"
          />
        </div>

        <div class="editor-field-card editor-gallery-card--below">
          <ArticleCreateGallerySection
            v-if="mediaMode === 'gallery'"
            variant="strip"
            :urls="galleryUrls"
            :max-count="galleryMaxCount"
            :strip-overflow="galleryStripOverflow"
            :strip-fade-left="galleryStripFadeLeft"
            :can-add="canAddGallery"
            @open="openGalleryPicker"
            @remove="removeGalleryAt"
            @scroll="updateGalleryStripState"
            @bind-ref="bindGalleryItemsRef"
          />
          <ArticleCreateVideoSection
            v-else
            variant="strip"
            :url="videoUrl"
            :uploading="videoUploading"
            :progress="videoUploadProgress"
            :upload-error="videoUploadError"
            @open="openVideoPicker"
            @remove="removeVideo"
          />
        </div>
      </el-form>

      <input
        ref="galleryInputRef"
        type="file"
        accept="image/*"
        multiple
        class="gallery-file-input"
        @change="onGalleryFilesSelected"
      >
      <input
        ref="videoInputRef"
        type="file"
        accept="video/*"
        class="gallery-file-input"
        @change="onVideoFileSelected"
      >
    </div>

    <input
      ref="mdFileInput"
      type="file"
      accept="image/*"
      multiple
      style="display: none"
      @change="handleMdFileSelected"
    >
  </div>
</template>

<script setup>
import { InfoFilled, Picture, VideoCamera } from '@element-plus/icons-vue'
import ArticleCreateGallerySection from '@/components/article/ArticleCreateGallerySection.vue'
import ArticleCreateVideoSection from '@/components/article/ArticleCreateVideoSection.vue'
import ArticleAiWriteAssist from '@/components/article/ArticleAiWriteAssist.vue'
import ArticleTagEditor from '@/components/article/ArticleTagEditor.vue'
import { useArticleCreate } from '@scripts/views/ArticleCreate'

const {
  WangEditor,
  applyAiContent,
  bindGalleryItemsRef,
  canAddGallery,
  cascaderOptions,
  editorMode,
  form,
  galleryInputRef,
  galleryMaxCount,
  galleryStripFadeLeft,
  galleryStripOverflow,
  galleryUrls,
  mediaMode,
  videoUrl,
  videoUploading,
  videoUploadProgress,
  videoUploadError,
  galleryUploading,
  videoInputRef,
  handleBoardChange,
  handleCancel,
  handleMdFileSelected,
  handleMdInsertImage,
  handlePublish,
  handleSaveDraft,
  isEdit,
  mdFileInput,
  mdTextareaRef,
  mdWrap,
  onGalleryFilesSelected,
  openGalleryPicker,
  openVideoPicker,
  removeVideo,
  onVideoFileSelected,
  removeGalleryAt,
  renderedPreview,
  selectedBoard,
  setEditorMode,
  setMediaMode,
  onMdKeydown,
  submitting,
  tagIds,
  updateGalleryStripState,
} = useArticleCreate()
</script>

<style scoped src="@/assets/styles/editor.css"></style>
<style scoped>
.editor-media-toggle {
  position: relative;
  display: inline-grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 4px;
  border-radius: 10px;
  background: rgba(29, 33, 41, 0.06);
  border: 1px solid rgba(29, 33, 41, 0.08);
  overflow: hidden;
}
.editor-media-toggle__item {
  position: relative;
  z-index: 2;
  height: 30px;
  padding: 0 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  color: #4a4a4a;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  white-space: nowrap;
}
.editor-media-toggle__item.is-active {
  color: #1d2129;
}
.editor-media-toggle__thumb {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: 30px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 20px rgba(29, 33, 41, 0.12);
  transition: transform 0.28s cubic-bezier(0.2, 0.9, 0.2, 1);
}
.editor-media-toggle__thumb.is-right {
  transform: translateX(100%);
}

.editor-mode-seg {
  position: relative;
  display: inline-grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  padding: 3px;
  border-radius: 20px;
  overflow: hidden;
  border: 0.5px solid var(--color-border-tertiary, #e5e6eb);
  background: rgba(29, 33, 41, 0.04);
}

.editor-mode-seg-btn {
  position: relative;
  z-index: 2;
  border-radius: 18px !important;
  background: transparent !important;
  color: var(--color-text-secondary, #86909c) !important;
}

.editor-mode-seg-btn.is-active {
  color: #1d2129 !important;
  background: transparent !important;
}

.editor-mode-seg__thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: calc(50% - 3px);
  height: calc(100% - 6px);
  border-radius: 18px;
  background: #d4537e;
  box-shadow: 0 6px 16px rgba(212, 83, 126, 0.28);
  transition: transform 0.28s cubic-bezier(0.2, 0.9, 0.2, 1);
  pointer-events: none;
}

.editor-mode-seg__thumb.is-right {
  transform: translateX(100%);
}

.editor-mode-seg-btn.is-active {
  color: #fff !important;
}
</style>
