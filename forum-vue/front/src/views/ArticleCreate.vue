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
            </div>
          </div>

          <div v-if="editorMode === 'rich'" class="editor-workspace">
            <div class="editor-main-pane">
              <div class="editor-container rich-container">
                <WangEditor
                  v-model="form.content"
                  min-height="300px"
                  toolbar-suppress-image
                  toolbar-slim
                />
              </div>
            </div>
            <aside class="editor-gallery-pane" aria-label="笔记相册">
              <ArticleCreateGallerySection
                variant="grid"
                :urls="galleryUrls"
                :max-count="galleryMaxCount"
                :can-add="canAddGallery"
                @open="openGalleryPicker"
                @remove="removeGalleryAt"
              />
            </aside>
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
                />
                <div class="md-preview prose" v-html="renderedPreview" />
              </div>
            </div>
          </div>
        </div>

        <div v-if="editorMode === 'markdown'" class="editor-field-card editor-gallery-card--below">
          <ArticleCreateGallerySection
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
import { InfoFilled, Picture } from '@element-plus/icons-vue'
import ArticleCreateGallerySection from '@/components/article/ArticleCreateGallerySection.vue'
import { useArticleCreate } from '@scripts/views/ArticleCreate'

const {
  WangEditor,
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
  removeGalleryAt,
  renderedPreview,
  selectedBoard,
  setEditorMode,
  submitting,
  updateGalleryStripState,
} = useArticleCreate()
</script>

<style scoped src="@/assets/styles/editor.css"></style>
