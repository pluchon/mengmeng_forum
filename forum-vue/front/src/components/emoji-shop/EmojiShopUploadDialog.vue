<template>
  <el-dialog
    v-model="visible"
    class="emoji-shop-upload-dialog"
    width="800px"
    align-center
    :z-index="4000"
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="!editMode && !interactionLocked"
    :close-on-press-escape="false"
    :before-close="handleDialogBeforeClose"
    @closed="close"
  >
    <template #header>
      <div class="emoji-shop-upload-dialog__head">
        <div class="emoji-shop-upload-dialog__head-title">
          <img :src="uploadHeaderIconUrl" alt="" class="emoji-shop-upload-dialog__head-icon" />
          <span>{{ dialogTitle }}</span>
        </div>
        <div class="emoji-shop-upload-dialog__head-actions">
          <el-button text circle aria-label="关闭" :disabled="interactionLocked" @click="close">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
    </template>

    <div
      v-loading.fullscreen.lock="autoSavingDraft"
      element-loading-text="正在自动保存草稿…"
      :inert="interactionLocked"
      class="emoji-shop-upload-dialog__body"
    >
      <div v-loading="loadingDraft">
        <div class="emoji-shop-upload-dialog__split">
          <aside class="emoji-shop-upload-dialog__aside">
          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              <span class="emoji-shop-upload-dialog__required">*</span>封面图
            </div>
            <button
              type="button"
              class="emoji-shop-upload-dialog__cover"
              :class="{ 'has-cover': !!form.coverUrl }"
              @click="pickCover"
            >
              <img v-if="form.coverUrl" :src="form.coverUrl" alt="" class="emoji-shop-upload-dialog__cover-img" />
              <template v-else>
                <el-icon class="emoji-shop-upload-dialog__cover-ph"><Picture /></el-icon>
                <span>拖拽或点击上传</span>
                <span class="emoji-shop-upload-dialog__cover-hint">建议 1:1 · PNG / JPG</span>
              </template>
            </button>
            <input
              ref="coverInput"
              type="file"
              class="emoji-shop-upload-dialog__file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              @change="onCoverFile"
            />
          </div>

          <div class="emoji-shop-upload-dialog__notice">
            <div class="emoji-shop-upload-dialog__notice-title">
              <img :src="warnIconUrl" alt="" class="emoji-shop-upload-dialog__warn-icon" />
              <span>小提示</span>
            </div>
            <ul class="emoji-shop-upload-dialog__notice-list">
              <li v-for="(item, idx) in UPLOAD_NOTICE_ITEMS" :key="idx">· {{ item }}</li>
            </ul>
          </div>
          <div class="emoji-shop-upload-dialog__review-note">
            <el-icon><Clock /></el-icon>
            <div>
              <strong>预计审核 1～5 分钟</strong>
              <span>审核结果将通过消息中心通知</span>
            </div>
          </div>
          <button
            v-if="isOfflinePublished"
            type="button"
            class="emoji-shop-upload-dialog__relist"
            :disabled="interactionLocked"
            @click="relistPublished"
          >
            {{ relisting ? '正在上架...' : '重新上架' }}
          </button>
          </aside>

          <section class="emoji-shop-upload-dialog__main">
          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              <span class="emoji-shop-upload-dialog__required">*</span>表情包名称
            </div>
            <el-input
              v-model="form.name"
              :maxlength="NAME_MAX"
              show-word-limit
              placeholder="1～20 字，需通过文本审核"
            />
          </div>

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              <span class="emoji-shop-upload-dialog__required">*</span>表情包分类
            </div>
            <el-select v-model="form.category" placeholder="请选择分类" class="emoji-shop-upload-dialog__category">
              <el-option
                v-for="option in EMOJI_SHOP_CATEGORY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </div>

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              <span class="emoji-shop-upload-dialog__required">*</span>表情包说明
            </div>
            <el-input
              v-model="form.description"
              type="textarea"
              :maxlength="DESCRIPTION_MAX"
              :autosize="{ minRows: 2, maxRows: 6 }"
              show-word-limit
              resize="none"
              placeholder="1～50 字，介绍表情包内容或使用方式"
            />
          </div>

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              <span class="emoji-shop-upload-dialog__required">*</span>售价（积分）
            </div>
            <div class="emoji-shop-upload-dialog__price-row">
              <button
                type="button"
                class="emoji-shop-upload-dialog__price-mode"
                :class="{ 'is-active': priceMode === 'free' }"
                @click="setPriceMode('free')"
              >
                免费
              </button>
              <div
                class="emoji-shop-upload-dialog__paid-group"
                :class="{ 'is-active': priceMode === 'paid' }"
              >
                <button
                  type="button"
                  class="emoji-shop-upload-dialog__price-mode emoji-shop-upload-dialog__price-mode--paid"
                  @click="setPriceMode('paid')"
                >
                  付费
                </button>
                <el-input-number
                  v-model="form.price"
                  :min="0"
                  :max="100000"
                  :step="1"
                  :controls="false"
                  :disabled="priceMode === 'free'"
                />
                <span class="emoji-shop-upload-dialog__price-hint">积分</span>
              </div>
            </div>
          </div>

          <hr class="emoji-shop-upload-dialog__sep" />

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__pack-head">
              <div class="emoji-shop-upload-dialog__label emoji-shop-upload-dialog__label--inline">
                <span class="emoji-shop-upload-dialog__required">*</span>包内图片
              </div>
              <span class="emoji-shop-upload-dialog__pack-count">{{ displayPackCount }} / {{ PACK_MAX }} 张</span>
            </div>
            <div class="emoji-shop-upload-dialog__pack-grid">
              <div
                v-for="item in visiblePackImages"
                :key="item.type === 'pending' ? item.id : `ready-${item.index}`"
                class="emoji-shop-upload-dialog__pack-item"
                :class="{ 'is-uploading': item.type === 'pending' }"
              >
                <img :src="item.url" alt="" />
                <div v-if="item.type === 'pending'" class="emoji-shop-upload-dialog__pack-pending" aria-hidden="true">
                  <span class="emoji-shop-upload-dialog__pack-pending-dot" />
                </div>
                <button
                  v-if="item.type === 'ready'"
                  type="button"
                  class="emoji-shop-upload-dialog__pack-remove"
                  aria-label="移除"
                  @click.stop="removePack(item.index)"
                >
                  <el-icon><Close /></el-icon>
                </button>
              </div>
              <button
                v-if="showPackAdd"
                type="button"
                class="emoji-shop-upload-dialog__pack-add"
                aria-label="添加图片"
                @click="pickPack"
              >
                <el-icon><Plus /></el-icon>
              </button>
            </div>
            <div v-if="packPageCount > 1" class="emoji-shop-upload-dialog__pack-pager">
              <AppPagination
                v-model:current-page="packPage"
                size="small"
                :page-size="1"
                :total="packPageCount"
                :pager-count="3"
                :show-jumper="false"
              />
            </div>
            <input
              ref="packInput"
              type="file"
              class="emoji-shop-upload-dialog__file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              multiple
              @change="onPackFiles"
            />
            <p class="emoji-shop-upload-dialog__ai-hint">
              <img :src="warnIconUrl" alt="" class="emoji-shop-upload-dialog__warn-icon" />
              {{ uploadingImageCount > 0 ? '图片上传中，提交时会自动等待整批完成' : '每张图片将经过 AI 内容审核，不符合规范的将被拒绝' }}
            </p>
          </div>

          <div class="emoji-shop-upload-dialog__footer">
            <el-button
              v-if="!editMode"
              class="emoji-shop-upload-dialog__save-draft"
              :loading="savingDraft"
              :disabled="uploadingImageCount > 0 || interactionLocked"
              @click="saveDraft"
            >
              <el-icon><DocumentChecked /></el-icon>
              保存草稿
            </el-button>
            <el-button
              v-if="editMode"
              class="emoji-shop-upload-dialog__delete-series"
              type="danger"
              plain
              round
              :disabled="submitting"
              @click="deletePublished"
            >
              删除该系列表情包
            </el-button>
            <span class="emoji-shop-upload-dialog__footer-spacer" />
            <button type="button" class="emoji-shop-upload-dialog__submit" :disabled="submitting" @click="submit">
              <img :src="submitIconUrl" alt="" class="emoji-shop-upload-dialog__submit-icon" />
              <span>{{ waitingUploads ? '等待图片上传…' : (submitting ? '提交中…' : (editMode ? '保存修改' : '提交上架')) }}</span>
            </button>
          </div>
          </section>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { Clock, Close, DocumentChecked, Picture, Plus } from '@element-plus/icons-vue'
import AppPagination from '@/components/common/AppPagination.vue'
import {
  useEmojiShopUploadDialog,
  UPLOAD_NOTICE_ITEMS,
} from '@scripts/components/emoji-shop/EmojiShopUploadDialog'

const props = defineProps({
  onCreated: { type: Function, default: null },
  onDraftSaved: { type: Function, default: null },
  onPublishedUpdated: { type: Function, default: null },
  onPublishedDeleted: { type: Function, default: null },
  onClosed: { type: Function, default: null },
})

const {
  uploadHeaderIconUrl,
  warnIconUrl,
  submitIconUrl,
  EMOJI_SHOP_CATEGORY_OPTIONS,
  DESCRIPTION_MAX,
  NAME_MAX,
  PACK_MAX,
  visible,
  submitting,
  savingDraft,
  autoSavingDraft,
  interactionLocked,
  loadingDraft,
  waitingUploads,
  relisting,
  coverInput,
  packInput,
  form,
  priceMode,
  uploadingImageCount,
  displayPackCount,
  editMode,
  isOfflinePublished,
  dialogTitle,
  packPage,
  packPageCount,
  visiblePackImages,
  showPackAdd,
  open,
  close,
  handleDialogBeforeClose,
  pickCover,
  pickPack,
  onCoverFile,
  onPackFiles,
  removePack,
  setPriceMode,
  saveDraft,
  submit,
  deletePublished,
  relistPublished,
} = useEmojiShopUploadDialog({
  onCreated: (id) => props.onCreated?.(id),
  onDraftSaved: (id) => props.onDraftSaved?.(id),
  onPublishedUpdated: (id) => props.onPublishedUpdated?.(id),
  onPublishedDeleted: (id) => props.onPublishedDeleted?.(id),
  onClosed: () => props.onClosed?.(),
})

defineExpose({ open, close })
</script>

<style scoped src="@/assets/styles/emoji-shop-upload-dialog.css"></style>
