<template>
  <div class="editor-page shell-page-scroll">
    <div class="editor-page-bg" aria-hidden="true" />
    <div class="editor-page-inner">
      <div class="editor-action-bar">
        <div class="editor-action-title-wrap">
          <span class="editor-action-accent" aria-hidden="true" />
          <h2 class="editor-action-title">
            {{ isEdit ? (form.articleType === ARTICLE_TYPE.QUESTION ? '编辑问题' : '编辑帖子') : (form.articleType === ARTICLE_TYPE.QUESTION ? '发起提问' : '创作笔记') }}
          </h2>
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
          <div class="editor-type-selector" :class="{ 'is-locked': isEdit }">
            <div class="editor-type-selector__copy">
              <span class="editor-type-selector__eyebrow">发布类型</span>
              <strong>{{ form.articleType === ARTICLE_TYPE.QUESTION ? '把问题交给社区' : '分享正在发生的事' }}</strong>
            </div>
            <div class="editor-type-selector__options" role="radiogroup" aria-label="帖子类型">
              <button
                type="button"
                role="radio"
                class="editor-type-option"
                :class="{ 'is-active': form.articleType === ARTICLE_TYPE.NORMAL }"
                :aria-checked="form.articleType === ARTICLE_TYPE.NORMAL"
                :disabled="isEdit"
                @click="form.articleType = ARTICLE_TYPE.NORMAL"
              >
                <span class="editor-type-option__mark">帖</span>
                <span><b>普通帖子</b><small>分享见闻与灵感</small></span>
              </button>
              <button
                type="button"
                role="radio"
                class="editor-type-option"
                :class="{ 'is-active': form.articleType === ARTICLE_TYPE.QUESTION }"
                :aria-checked="form.articleType === ARTICLE_TYPE.QUESTION"
                :disabled="isEdit"
                @click="form.articleType = ARTICLE_TYPE.QUESTION"
              >
                <span class="editor-type-option__mark">问</span>
                <span><b>问答帖子</b><small>等待一条最佳答案</small></span>
              </button>
            </div>
          </div>
          <div class="editor-meta-row">
            <div class="editor-meta-col">
              <div class="editor-field-label">
                <span class="editor-req">*</span> 标题
              </div>
              <el-input
                v-model="form.title"
                :placeholder="form.articleType === ARTICLE_TYPE.QUESTION ? '用一句话说清楚你想解决的问题' : '填写标题会有更多赞哦'"
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
              <span class="editor-req">*</span> {{ form.articleType === ARTICLE_TYPE.QUESTION ? '问题描述' : '内容' }}
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
                  :placeholder="form.articleType === ARTICLE_TYPE.QUESTION ? '补充背景、已经尝试过的方法，以及你期待的答案…' : '分享你的故事吧…'"
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
            :label="form.articleType === ARTICLE_TYPE.QUESTION ? '问题标签' : (mediaMode === 'video' ? '视频标签' : '帖子标签')"
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
            :can-add="canAddGallery && !galleryUploading"
            :uploading="galleryUploading"
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

<script setup src="./ArticleCreate.js"></script>

<style scoped src="@/assets/styles/editor.css"></style>
<style lang="scss" src="./ArticleCreate.scss"></style>
