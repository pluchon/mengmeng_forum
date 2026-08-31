<template>
  <el-dialog
    v-model="visible"
    class="emoji-shop-detail-dialog"
    width="724px"
    align-center
    destroy-on-close
    :show-close="false"
    @closed="close"
  >
    <template #header>
      <div class="emoji-shop-detail-dialog__head">
        <span class="emoji-shop-detail-dialog__title">{{ dialogTitle }}</span>
        <el-button text circle aria-label="关闭" @click="close">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="emoji-shop-detail-dialog__body">
      <template v-if="detail">
        <div class="emoji-shop-detail-dialog__split">
          <aside class="emoji-shop-detail-dialog__preview">
            <div class="emoji-shop-detail-dialog__main-img">
              <img v-if="previewUrl" :src="previewUrl" alt="" />
              <el-icon v-else class="emoji-shop-detail-dialog__img-ph"><Picture /></el-icon>
            </div>
            <div v-if="detail.imageUrls?.length" class="emoji-shop-detail-dialog__thumbs">
              <button
                v-for="(url, idx) in detail.imageUrls"
                :key="idx"
                type="button"
                class="emoji-shop-detail-dialog__thumb"
                :class="{ 'is-active': previewIndex === idx }"
                @click="setPreview(idx)"
              >
                <img :src="url" alt="" />
              </button>
            </div>
            <div v-if="imageCount > itemPageSize" class="emoji-shop-detail-dialog__pager">
              <AppPagination
                v-model:current-page="itemPage"
                size="small"
                :page-size="itemPageSize"
                :total="imageCount"
                :pager-count="3"
                :show-jumper="false"
                @current-change="onItemPageChange"
              />
            </div>
            <p class="emoji-shop-detail-dialog__thumb-hint">点击预览包内表情</p>
          </aside>

          <section class="emoji-shop-detail-dialog__info">
            <div
              class="emoji-shop-detail-dialog__info-top"
              :class="{ 'is-offline': detail.status === 2 }"
            >
              <div>
                <h2 class="emoji-shop-detail-dialog__name">{{ detail.name }}</h2>
                <div
                  v-if="detail.uploadUserId"
                  class="emoji-shop-detail-dialog__author"
                  role="link"
                  tabindex="0"
                  @click="goUploaderProfile"
                  @keydown.enter.prevent="goUploaderProfile"
                >
                  <UserAvatarVip
                    :size="20"
                    :src="detail.uploadUserAvatarUrl"                  />
                  <span>{{ detail.uploadUserNickname || ('用户' + detail.uploadUserId) }}</span>
                </div>
              </div>
              <el-tag :type="statusLabel.type" round effect="light" size="small">
                {{ statusLabel.text }}
              </el-tag>
            </div>

            <div class="emoji-shop-detail-dialog__stats">
              <span class="emoji-shop-detail-dialog__stat-pill">
                已售 {{ detail.salesCount ?? 0 }}
              </span>
              <span class="emoji-shop-detail-dialog__stat-pill">
                共 {{ imageCount }} 张
              </span>
              <span class="emoji-shop-detail-dialog__stat-pill">
                {{ createDateText }}
              </span>
            </div>

            <hr class="emoji-shop-detail-dialog__sep" />

            <div class="emoji-shop-detail-dialog__section-label">表情包说明</div>
            <p class="emoji-shop-detail-dialog__section-text">{{ descriptionText }}</p>

            <hr class="emoji-shop-detail-dialog__sep" />

            <div class="emoji-shop-detail-dialog__footer">
              <div class="emoji-shop-detail-dialog__price-row">
                <div class="emoji-shop-detail-dialog__price-value">
                  <span class="emoji-shop-detail-dialog__price-label">价格</span>
                  <span>{{ priceText }}</span>
                </div>
              </div>

              <div class="emoji-shop-detail-dialog__action-row">
                <el-button
                  v-if="isAuthor && detail.status === 1"
                  class="emoji-shop-detail-dialog__shelf-btn"
                  round
                  @click="setShelf(2)"
                >
                  下架
                </el-button>
                <el-button
                  v-else-if="isAuthor && detail.status !== 1"
                  class="emoji-shop-detail-dialog__shelf-btn"
                  round
                  @click="setShelf(1)"
                >
                  上架
                </el-button>

                <el-button
                  v-if="detail.owned"
                  type="success"
                  class="emoji-shop-detail-dialog__buy-btn"
                  :class="{ 'is-offline': detail.status === 2 }"
                  disabled
                  round
                >
                  已拥有
                </el-button>
                <el-button
                  v-else-if="isAuthor"
                  type="info"
                  class="emoji-shop-detail-dialog__buy-btn"
                  disabled
                  round
                >
                  您是作者，无需购买~
                </el-button>
                <el-button
                  v-else-if="canPurchase"
                  type="primary"
                  class="emoji-shop-detail-dialog__buy-btn"
                  round
                  :disabled="purchaseDisabled"
                  :loading="purchasing"
                  @click="onPurchase"
                >
                  立即购买
                </el-button>
                <el-button
                  v-else-if="!userStore.isLoggedIn"
                  type="primary"
                  class="emoji-shop-detail-dialog__buy-btn"
                  round
                  @click="router.push('/sign-in')"
                >
                  登录后购买
                </el-button>
              </div>
            </div>
          </section>
        </div>
      </template>
      <div v-else-if="offlineNotice" class="emoji-shop-detail-dialog__offline">
        <img :src="offlineImage" alt="" class="emoji-shop-detail-dialog__offline-img" />
        <p class="emoji-shop-detail-dialog__offline-title">该表情包已下架</p>
        <p class="emoji-shop-detail-dialog__offline-desc">作者已将这个系列下架，暂时无法查看</p>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Close, Picture } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useEmojiShopDetailDialog } from '@scripts/components/emoji-shop/EmojiShopDetailDialog'

const router = useRouter()

const props = defineProps({
  onPurchased: { type: Function, default: null },
  onClosed: { type: Function, default: null },
})

const {
  visible,
  loading,
  offlineNotice,
  offlineImage,
  purchasing,
  detail,
  previewIndex,
  itemPage,
  itemPageSize,
  previewUrl,
  imageCount,
  statusLabel,
  createDateText,
  dialogTitle,
  descriptionText,
  priceText,
  userStore,
  wallet,
  isAuthor,
  canPurchase,
  purchaseDisabled,
  setPreview,
  open,
  onItemPageChange,
  close,
  goUploaderProfile,
  onPurchase,
  setShelf,
} = useEmojiShopDetailDialog({
  onPurchased: (id) => props.onPurchased?.(id),
  onClosed: () => props.onClosed?.(),
})

defineExpose({ open, close })
</script>

<style scoped src="@/assets/styles/emoji-shop-detail-dialog.css"></style>
