<template>
  <el-dialog
    v-model="visible"
    class="emoji-shop-upload-dialog"
    width="640px"
    align-center
    destroy-on-close
    :show-close="false"
    @closed="close"
  >
    <template #header>
      <div class="emoji-shop-upload-dialog__head">
        <div class="emoji-shop-upload-dialog__head-title">
          <img :src="uploadHeaderIconUrl" alt="" class="emoji-shop-upload-dialog__head-icon" />
          <span>上传表情包</span>
        </div>
        <el-button text circle aria-label="关闭" @click="close">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="emoji-shop-upload-dialog__body">
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
                <span>点击上传封面</span>
                <span class="emoji-shop-upload-dialog__cover-hint">建议 1:1 正方形</span>
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
              <span>上架须知</span>
            </div>
            <ul class="emoji-shop-upload-dialog__notice-list">
              <li v-for="(item, idx) in UPLOAD_NOTICE_ITEMS" :key="idx">· {{ item }}</li>
            </ul>
          </div>
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
              placeholder="1～100 字，需通过文本审核"
            />
          </div>

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              表情包说明
              <span class="emoji-shop-upload-dialog__optional">选填</span>
            </div>
            <el-input
              v-model="form.description"
              :maxlength="DESCRIPTION_MAX"
              show-word-limit
              placeholder="介绍表情包内容或使用方式，最多 100 字"
            />
          </div>

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__label">
              <span class="emoji-shop-upload-dialog__required">*</span>售价（积分）
            </div>
            <div class="emoji-shop-upload-dialog__price-row">
              <el-input-number
                v-model="form.price"
                :min="0"
                :max="100000"
                :step="1"
                controls-position="right"
              />
              <span class="emoji-shop-upload-dialog__price-hint">0 = 免费领取</span>
            </div>
          </div>

          <hr class="emoji-shop-upload-dialog__sep" />

          <div class="emoji-shop-upload-dialog__field">
            <div class="emoji-shop-upload-dialog__pack-head">
              <div class="emoji-shop-upload-dialog__label emoji-shop-upload-dialog__label--inline">
                <span class="emoji-shop-upload-dialog__required">*</span>包内图片
              </div>
              <span class="emoji-shop-upload-dialog__pack-count">{{ form.imageUrls.length }} / {{ PACK_MAX }} 张</span>
            </div>
            <div class="emoji-shop-upload-dialog__pack-grid">
              <div v-for="(url, i) in form.imageUrls" :key="url + i" class="emoji-shop-upload-dialog__pack-item">
                <img :src="url" alt="" />
                <button
                  type="button"
                  class="emoji-shop-upload-dialog__pack-remove"
                  aria-label="移除"
                  @click.stop="removePack(i)"
                >
                  <el-icon><Close /></el-icon>
                </button>
              </div>
              <button
                v-if="form.imageUrls.length < PACK_MAX"
                type="button"
                class="emoji-shop-upload-dialog__pack-add"
                aria-label="添加图片"
                @click="pickPack"
              >
                <el-icon><Plus /></el-icon>
              </button>
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
              每张图片将经过 AI 内容审核，不符合规范的将被拒绝
            </p>
          </div>

          <div class="emoji-shop-upload-dialog__footer">
            <el-button round @click="close">取消</el-button>
            <button type="button" class="emoji-shop-upload-dialog__submit" :disabled="submitting" @click="submit">
              <img :src="submitIconUrl" alt="" class="emoji-shop-upload-dialog__submit-icon" />
              <span>{{ submitting ? '提交中…' : '提交上架' }}</span>
            </button>
          </div>
        </section>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { Close, Picture, Plus } from '@element-plus/icons-vue'
import {
  useEmojiShopUploadDialog,
  UPLOAD_NOTICE_ITEMS,
} from '@scripts/components/emoji-shop/EmojiShopUploadDialog'

const props = defineProps({
  onCreated: { type: Function, default: null },
  onClosed: { type: Function, default: null },
})

const {
  uploadHeaderIconUrl,
  warnIconUrl,
  submitIconUrl,
  DESCRIPTION_MAX,
  NAME_MAX,
  PACK_MAX,
  visible,
  submitting,
  coverInput,
  packInput,
  form,
  open,
  close,
  pickCover,
  pickPack,
  onCoverFile,
  onPackFiles,
  removePack,
  submit,
} = useEmojiShopUploadDialog({
  onCreated: (id) => props.onCreated?.(id),
  onClosed: () => props.onClosed?.(),
})

defineExpose({ open, close })
</script>

<style scoped src="@/assets/styles/emoji-shop-upload-dialog.css"></style>
