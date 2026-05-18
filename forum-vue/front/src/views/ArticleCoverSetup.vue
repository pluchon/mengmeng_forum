<template>
  <div class="editor-page shell-page-scroll">
    <div class="editor-page-bg" aria-hidden="true" />
    <div class="editor-page-inner cover-setup-page">
      <div
        class="cover-setup-ring-host"
        :class="{ 'cover-setup-ring-host--on': isVip }"
      >
        <div class="cover-setup-card animate-fade-up">
          <header class="cover-setup-header">
            <h2 class="cover-setup-title">设置帖子封面</h2>
            <span v-if="title" class="cover-setup-post-title">
              <el-icon><Document /></el-icon>
              {{ title }}
            </span>
          </header>

          <div class="cover-setup-two-col">
            <!-- 左：自行上传 -->
            <div class="cover-upload-col">
              <div class="cover-col-label">
                <el-icon><Upload /></el-icon>
                自行上传
              </div>
              <el-upload
                class="cover-upload-uploader"
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                accept="image/jpeg,image/png,image/webp"
                :on-change="handleCoverChange"
              >
                <div class="cover-upload-zone">
                  <div class="cover-upload-icon">
                    <el-icon><Picture /></el-icon>
                  </div>
                  <div class="cover-upload-text">点击或拖拽图片到此处</div>
                  <div class="cover-upload-hint">建议 16:9 横图，JPG / PNG / WebP，≤ 5MB</div>
                </div>
              </el-upload>
            </div>

            <!-- 右：AI 配图 -->
            <div class="cover-ai-col">
              <div class="cover-col-label">
                <el-icon><MagicStick /></el-icon>
                AI 配图
                <span class="cover-badge-rec">推荐</span>
              </div>
              <div class="cover-ai-box" :class="{ 'is-locked': !isVip }">
                <span v-if="!isVip" class="cover-ai-lock-tag">会员专享</span>
                <button
                  type="button"
                  class="cover-suggest-btn cover-suggest-btn--block"
                  :disabled="!isVip || hintsLoading || !articleTextPlain"
                  @click="fetchCoverHints"
                >
                  <el-icon><MagicStick /></el-icon>
                  {{ hintsLoading ? '生成中…' : '根据正文推荐配图要点' }}
                </button>
                <div class="cover-prompt-wrap">
                  <div class="cover-prompt-label">封面描述词</div>
                  <textarea
                    v-model="aiPrompt"
                    class="cover-prompt-textarea"
                    :maxlength="COVER_PROMPT_MAX"
                    :disabled="!isVip"
                    placeholder="描述封面画面；可先点击上方按钮根据正文生成要点，再微调…"
                  />
                  <div class="cover-prompt-footer">
                    <div class="cover-model-row">
                      <img :src="iconAi" alt="" class="cover-model-icon" aria-hidden="true">
                      <div class="cover-seg">
                        <button
                          v-for="opt in IMAGE_MODEL_OPTIONS"
                          :key="opt.value"
                          type="button"
                          class="cover-seg-btn"
                          :class="{ on: imageQuality === opt.value }"
                          :title="opt.label"
                          :disabled="!isVip"
                          @click="setImageModel(opt.value)"
                        >
                          {{ opt.short }}
                        </button>
                      </div>
                      <button
                        type="button"
                        class="cover-regen-btn"
                        :disabled="!canRegenerate"
                        @click="generateAiCover"
                      >
                        <el-icon><Refresh /></el-icon>
                        重新生成
                      </button>
                    </div>
                    <span
                      class="cover-prompt-counter"
                      :class="{ 'is-warn': promptLength >= COVER_PROMPT_MAX }"
                    >{{ promptLength }} / {{ COVER_PROMPT_MAX }}</span>
                  </div>
                </div>
                <button
                  type="button"
                  class="cover-gen-btn"
                  :disabled="!isVip || aiGenerating || !aiPrompt.trim()"
                  @click="generateAiCover"
                >
                  <el-icon><Picture /></el-icon>
                  {{ aiGenerating ? '生成中…' : 'AI 生成封面图' }}
                </button>
              </div>
            </div>
          </div>

          <!-- 封面预览 -->
          <section class="cover-preview-section">
            <div class="cover-preview-header">
              <div class="cover-preview-title">
                <el-icon><View /></el-icon>
                封面预览
              </div>
            </div>
            <div class="cover-preview-frame">
              <img
                v-if="coverPreview"
                :src="coverPreview"
                alt="封面预览"
                class="cover-preview-img"
              >
              <div v-else class="cover-preview-placeholder">
                <el-icon><Picture /></el-icon>
                上传或 AI 生成后，封面预览将显示在这里
              </div>
              <button
                v-if="coverPreview"
                type="button"
                class="cover-preview-download"
                title="下载图片"
                @click.stop="downloadCoverImage"
              >
                <el-icon><Download /></el-icon>
              </button>
            </div>
          </section>

          <!-- 底部 -->
          <footer class="cover-footer-bar">
            <div class="cover-footer-hint">
              <el-icon><InfoFilled /></el-icon>
              <span>AI 生图仅作参考，请自行甄别内容！</span>
            </div>
            <div class="cover-footer-btns">
              <el-button
                class="editor-btn-ghost"
                :loading="processing"
                :disabled="isPublished"
                @click="saveDraftOnly"
              >
                <img :src="iconDraft" alt="" class="cover-footer-btn-icon" aria-hidden="true">
                仅保留草稿
              </el-button>
              <el-button
                class="editor-btn-primary"
                type="primary"
                :loading="processing"
                @click="finishAndSubmitAudit"
              >
                <el-icon><CircleCheck /></el-icon>
                {{ isPublished ? '保存封面' : '完成并提交审核' }}
              </el-button>
            </div>
          </footer>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { CircleCheck } from '@element-plus/icons-vue'
import iconAi from '@/assets/svg/AI.svg'
import iconDraft from '@/assets/svg/草稿.svg'
import { useArticleCoverSetup } from '@scripts/views/ArticleCoverSetup'

const {
  COVER_PROMPT_MAX,
  IMAGE_MODEL_OPTIONS,
  Document,
  Download,
  InfoFilled,
  MagicStick,
  Picture,
  Refresh,
  Upload,
  View,
  aiGenerating,
  aiPrompt,
  articleTextPlain,
  canRegenerate,
  coverPreview,
  downloadCoverImage,
  fetchCoverHints,
  finishAndSubmitAudit,
  generateAiCover,
  handleCoverChange,
  hintsLoading,
  imageQuality,
  isPublished,
  isVip,
  processing,
  promptLength,
  saveDraftOnly,
  setImageModel,
  title,
} = useArticleCoverSetup()
</script>

<style scoped src="@/assets/styles/editor.css"></style>
<style scoped src="@/assets/styles/article-cover-setup.css"></style>
