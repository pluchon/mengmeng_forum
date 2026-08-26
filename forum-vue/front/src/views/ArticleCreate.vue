<template>
  <div class="editor-page shell-page-scroll">
    <div class="editor-page-bg" aria-hidden="true" />
    <div class="editor-page-inner editor-page-inner--composer">
      <header class="editor-action-bar">
        <div class="editor-action-title-wrap">
          <span class="editor-action-accent" aria-hidden="true" />
          <h2 class="editor-action-title">
            {{ isEdit ? (form.articleType === ARTICLE_TYPE.QUESTION ? '编辑问题' : '编辑帖子') : (form.articleType === ARTICLE_TYPE.QUESTION ? '发起提问' : '创作笔记') }}
          </h2>
        </div>
        <div class="editor-action-btns">
          <el-button class="editor-btn-ghost" @click="handleCancel">取消</el-button>
          <el-button class="editor-btn-draft" :loading="submitting" @click="handleSaveDraft">
            存草稿
          </el-button>
          <el-button class="editor-btn-primary" :loading="submitting" @click="handlePublish">
            提交审核
          </el-button>
        </div>
      </header>

      <el-form :model="form" label-position="top" class="editor-form">
        <section class="editor-field-card editor-meta-card">
          <div class="editor-type-selector">
            <div class="editor-section-heading">
              <strong>帖子类型</strong>
            </div>
            <div class="editor-type-selector__options" role="radiogroup" aria-label="帖子类型">
              <button
                type="button"
                role="radio"
                class="editor-type-option"
                :class="{ 'is-active': form.articleType === ARTICLE_TYPE.NORMAL }"
                :aria-checked="form.articleType === ARTICLE_TYPE.NORMAL"
                @click="form.articleType = ARTICLE_TYPE.NORMAL"
              >
                普通帖子
              </button>
              <button
                type="button"
                role="radio"
                class="editor-type-option"
                :class="{ 'is-active': form.articleType === ARTICLE_TYPE.QUESTION }"
                :aria-checked="form.articleType === ARTICLE_TYPE.QUESTION"
                @click="form.articleType = ARTICLE_TYPE.QUESTION"
              >
                问答帖子
              </button>
            </div>
          </div>

          <div class="editor-meta-row editor-meta-row--title-music-board">
            <div class="editor-meta-col editor-meta-col--title">
              <div class="editor-field-label"><span class="editor-req">*</span> 标题</div>
              <el-input
                v-model="form.title"
                :placeholder="form.articleType === ARTICLE_TYPE.QUESTION ? '用一句话说清楚你想解决的问题' : '填写标题会有更多赞哦'"
                maxlength="50"
                show-word-limit
                class="editor-title-input"
              />
            </div>
            <div class="editor-meta-col editor-meta-col--music">
              <div class="editor-field-label"><span class="editor-req">*</span> 帖子音乐</div>
              <div class="editor-music-entry-wrap">
                <div
                  class="editor-music-entry"
                  role="button"
                  tabindex="0"
                  @click="openMusicHall"
                  @keydown.enter.prevent="openMusicHall"
                  @keydown.space.prevent="openMusicHall"
                >
                  <button
                    v-if="selectedMusic"
                    type="button"
                    class="editor-music-clear"
                    aria-label="清除配乐"
                    @click.stop="clearSelectedMusic"
                  >
                    <el-icon :size="14"><Close /></el-icon>
                  </button>
                  <el-icon class="editor-music-entry__icon"><Headset /></el-icon>
                  <span class="editor-music-entry__text">
                    {{ selectedMusic?.title || '进入音乐大厅' }}
                  </span>
                  <el-icon class="editor-music-entry__arrow"><ArrowRight /></el-icon>
                </div>
              </div>
            </div>
            <div class="editor-meta-col editor-meta-col--board">
              <div class="editor-field-label"><span class="editor-req">*</span> 发布至版块</div>
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
        </section>

        <div class="editor-compose-layout">
          <BorderGlow
            class="editor-card-glow"
            :animated="aiWriting"
            :edge-sensitivity="30"
            glow-color="320 84 72"
            background-color="#ffffff"
            :border-radius="22"
            :glow-radius="42"
            :glow-intensity="1.1"
            :cone-spread="28"
            :sweep-speed="100"
            :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
          >
            <section class="editor-content-card editor-compose-main">
              <div class="editor-content-head">
                <div class="editor-field-label editor-field-label--inline">
                  <span class="editor-req">*</span>
                  {{ form.articleType === ARTICLE_TYPE.QUESTION ? '问题描述' : '内容' }}
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
                  </div>
                  <span class="editor-content-tool-divider" aria-hidden="true" />
                  <div class="editor-mode-seg" role="tablist" aria-label="编辑器模式">
                    <button
                      type="button"
                      role="tab"
                      class="editor-mode-seg-btn"
                      :class="{ 'is-active': editorMode === 'rich' }"
                      :aria-selected="editorMode === 'rich'"
                      :disabled="aiWriting"
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
                      :disabled="aiWriting"
                      @click="setEditorMode('markdown')"
                    >
                      Markdown
                    </button>
                  </div>
                  <ArticleAiWriteAssist
                    text-only
                    :editor-mode="editorMode"
                    :title="form.title"
                    :content="form.content"
                    @apply="applyAiContent"
                    @generating="setAiWriting"
                  />
                </div>
              </div>

              <div v-if="editorMode === 'rich'" class="editor-workspace editor-workspace--single">
                <div v-if="aiWriting" class="editor-ai-writing-mask" role="status" aria-live="polite">
                  <span class="editor-ai-writing-star editor-ai-writing-star--one" aria-hidden="true">✦</span>
                  <span class="editor-ai-writing-star editor-ai-writing-star--two" aria-hidden="true">✧</span>
                  <span class="editor-ai-writing-star editor-ai-writing-star--three" aria-hidden="true">✦</span>
                  <span class="editor-ai-writing-star editor-ai-writing-star--four" aria-hidden="true">✧</span>
                  <strong>AI注入灵感中......</strong>
                </div>
                <div class="editor-container rich-container">
                  <WangEditor
                    v-model="form.content"
                    min-height="430px"
                    toolbar-suppress-image
                    toolbar-slim
                  />
                </div>
              </div>

              <div v-else class="editor-md-workspace">
                <div v-if="aiWriting" class="editor-ai-writing-mask" role="status" aria-live="polite">
                  <span class="editor-ai-writing-star editor-ai-writing-star--one" aria-hidden="true">✦</span>
                  <span class="editor-ai-writing-star editor-ai-writing-star--two" aria-hidden="true">✧</span>
                  <span class="editor-ai-writing-star editor-ai-writing-star--three" aria-hidden="true">✦</span>
                  <span class="editor-ai-writing-star editor-ai-writing-star--four" aria-hidden="true">✧</span>
                  <strong>AI注入灵感中......</strong>
                </div>
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
                      :rows="20"
                      :placeholder="form.articleType === ARTICLE_TYPE.QUESTION ? '补充背景、已经尝试过的方法，以及你期待的答案…' : '分享你的故事吧…'"
                      class="md-input"
                      @keydown="onMdKeydown"
                    />
                    <div class="md-preview prose" v-html="renderedPreview" />
                  </div>
                </div>
              </div>
            </section>
          </BorderGlow>

          <aside class="editor-publish-aside">
            <BorderGlow
              class="editor-card-glow editor-card-glow--tag"
              :animated="tagAiGenerating"
              :edge-sensitivity="20"
              glow-color="320 84 72"
              background-color="#ffffff"
              :border-radius="18"
              :glow-radius="34"
              :glow-intensity="0.95"
              :cone-spread="26"
              :sweep-speed="110"
              :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
            >
              <section class="editor-field-card editor-side-card editor-tag-card">
                <div class="editor-side-card__head">
                  <div class="editor-side-card__title-group">
                    <div class="editor-side-card__title">帖子标签</div>
                    <span class="editor-side-card__count">{{ tagIds.length }}/5</span>
                  </div>
                </div>
                <ArticleTagEditor
                  v-model="tagIds"
                  compact
                  :board-id="form.boardId"
                  :title="form.title"
                  :content="form.content"
                  :editor-mode="editorMode"
                  :label="form.articleType === ARTICLE_TYPE.QUESTION ? '问题标签' : (mediaMode === 'video' ? '视频标签' : '帖子标签')"
                  @ai-generating="setTagAiGenerating"
                />
              </section>
            </BorderGlow>

            <section class="editor-field-card editor-side-card editor-media-card">
              <div class="editor-side-card__head">
                <div class="editor-side-card__title">{{ mediaMode === 'video' ? '帖子视频' : '帖子图片' }}</div>
                <span class="editor-side-card__count">
                  {{ mediaMode === 'video' ? (videoUrl ? '1/1' : '0/1') : `${galleryUrls.length + galleryPendingCount}/${galleryMaxCount}` }}
                </span>
              </div>
              <ArticleCreateGallerySection
                v-if="mediaMode === 'gallery'"
                variant="grid"
                :urls="galleryUrls"
                :max-count="galleryMaxCount"
                :can-add="canAddGallery && !galleryUploading"
                :uploading="galleryUploading"
                :pending-count="galleryPendingCount"
                @open="openGalleryPicker"
                @remove="removeGalleryAt"
              />
              <ArticleCreateVideoSection
                v-else
                variant="grid"
                :url="videoUrl"
                :uploading="videoUploading"
                :progress="videoUploadProgress"
                :upload-error="videoUploadError"
                @open="openVideoPicker"
                @remove="removeVideo"
              />
            </section>

            <BorderGlow
              class="editor-card-glow editor-card-glow--cover"
              :animated="coverAiGenerating"
              :edge-sensitivity="18"
              glow-color="320 84 72"
              background-color="#ffffff"
              :border-radius="18"
              :glow-radius="34"
              :glow-intensity="0.95"
              :cone-spread="26"
              :sweep-speed="110"
              :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
            >
              <section class="editor-field-card editor-side-card editor-cover-card">
                <div class="editor-side-card__head">
                  <div class="editor-side-card__title"><span class="editor-req">*</span> 帖子封面</div>
                  <div class="editor-cover-models" role="radiogroup" aria-label="AI 配图模型">
                    <button
                      v-for="option in imageModelOptions"
                      :key="option.value"
                      type="button"
                      class="editor-cover-model"
                      :class="{ 'is-active': coverImageQuality === option.value }"
                      :aria-checked="coverImageQuality === option.value"
                      role="radio"
                      @click="setCoverImageQuality(option.value)"
                    >
                      <img :src="option.icon" :alt="option.short">
                      <span>{{ option.short }}</span>
                    </button>
                  </div>
                </div>
                <div class="editor-cover-actions">
                  <button type="button" class="editor-cover-action" @click="openCoverPicker">
                    <el-icon><Upload /></el-icon>
                    上传封面
                  </button>
                  <button
                    type="button"
                    class="editor-cover-action editor-cover-action--ai"
                    :disabled="coverAiGenerating"
                    @click="generateAiCover"
                  >
                    <el-icon><MagicStick /></el-icon>
                    {{ coverAiGenerating ? '生成中...' : 'AI 配图' }}
                  </button>
                </div>
                <div class="editor-cover-preview" :class="{ 'has-cover': coverPreview, 'is-generating': coverAiGenerating }">
                  <el-image
                    v-if="coverPreview"
                    :src="coverPreview"
                    :preview-src-list="[coverPreview]"
                    preview-teleported
                    fit="contain"
                    alt="帖子封面预览"
                    class="editor-cover-preview__image"
                  />
                  <template v-else>
                    <el-icon><Picture /></el-icon>
                    <span>封面预览</span>
                  </template>
                  <div v-if="coverAiGenerating" class="editor-cover-generating" aria-live="polite">
                    <span class="editor-cover-generating__stars" aria-hidden="true">
                      <i /><i /><i /><i /><i />
                    </span>
                    <strong>AI 生图中...</strong>
                  </div>
                  <button v-if="coverPreview" type="button" class="editor-cover-remove" aria-label="移除封面" @click="clearCover">
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
              </section>
            </BorderGlow>
          </aside>
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
      <input
        ref="coverInputRef"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        class="gallery-file-input"
        @change="onCoverFileSelected"
      >
      <input
        ref="mdFileInput"
        type="file"
        accept="image/*"
        multiple
        class="gallery-file-input"
        @change="handleMdFileSelected"
      >
    </div>

    <MusicHall
      v-model="musicHallOpen"
      :selected="selectedMusic"
      :article-title="form.title"
      :article-content="form.content"
      @confirm="onMusicConfirm"
    />
  </div>
</template>

<script setup src="./ArticleCreate.js"></script>

<style scoped src="@/assets/styles/editor.css"></style>
<style lang="scss" src="./ArticleCreate.scss"></style>
