<template>
  <div class="emoji-shop-page shell-page-scroll animate-fade-in">
    <div class="emoji-shop-inner">
      <header
        class="emoji-shop-hero"
        :style="{ '--emoji-shop-hero-image': `url(${xiaomengAtelierHeroUrl})` }"
      >
        <div class="emoji-shop-hero-scene" aria-hidden="true"></div>
        <div class="emoji-shop-hero-left">
          <h1 class="emoji-shop-title">表情商城</h1>
          <p class="emoji-shop-desc">发现有趣、治愈、充满创意的表情包系列</p>
        </div>
        <div v-if="userStore.isLoggedIn" class="emoji-shop-hero-right">
          <button
            type="button"
            class="emoji-shop-upload-btn"
            @click="goUpload()"
          >
            <img :src="uploadIconUrl" alt="" class="emoji-shop-upload-icon" />
            上传表情包
          </button>
        </div>
      </header>

      <div class="emoji-shop-filter-bar">
        <div class="emoji-shop-filter-left">
          <div class="emoji-shop-main-tabs" role="tablist" aria-label="列表分类">
            <button
              v-for="opt in EMOJI_SHOP_CATEGORY_TABS"
              :key="opt.value"
              type="button"
              class="emoji-shop-main-tab"
              :class="{ 'is-active': !isOwnedView && !isDraftView && !isPublishedView && category === opt.value }"
              role="tab"
              :aria-selected="!isOwnedView && !isDraftView && !isPublishedView && category === opt.value"
              @click="setCategory(opt.value)"
            >
              {{ opt.label }}
            </button>
            <button
              v-if="userStore.isLoggedIn"
              type="button"
              class="emoji-shop-main-tab emoji-shop-owned-tab"
              :class="{ 'is-active': isOwnedView }"
              role="tab"
              :aria-selected="isOwnedView"
              @click="showMyOwned"
            >
              我的已购
            </button>
            <button
              v-if="userStore.isLoggedIn"
              type="button"
              class="emoji-shop-main-tab emoji-shop-draft-tab"
              :class="{ 'is-active': isDraftView }"
              role="tab"
              :aria-selected="isDraftView"
              @click="showMyDrafts"
            >
              我的草稿
            </button>
            <button
              v-if="userStore.isLoggedIn"
              type="button"
              class="emoji-shop-main-tab emoji-shop-published-tab"
              :class="{ 'is-active': isPublishedView }"
              role="tab"
              :aria-selected="isPublishedView"
              @click="showMyPublished"
            >
              我的发布
            </button>
          </div>
        </div>
        <div class="emoji-shop-filter-actions">
          <div
            class="emoji-shop-search-wrap"
            :class="{ 'is-vip': isVipMember }"
          >
            <el-input
              v-model="keyword"
              class="emoji-shop-search"
              placeholder="搜索表情包或作者"
              clearable
              :prefix-icon="Search"
              @input="onSearchInput"
              @clear="loadCurrentView(1)"
              @keyup.enter="loadCurrentView(1)"
            />
          </div>
          <el-select
            :model-value="sort"
            class="emoji-shop-sort-select"
            size="small"
            placeholder="综合排序"
            :teleported="false"
            @update:model-value="setSort"
          >
            <el-option
              v-for="opt in SORT_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </div>

      <div
        class="emoji-shop-content"
        v-loading="loading"
        element-loading-background="rgba(255, 255, 255, 0.42)"
      >
        <div v-if="records.length" class="emoji-shop-grid">
          <article
            v-for="item in records"
            :key="item.id"
            class="emoji-shop-card"
            @click="onCardClick(item)"
          >
            <div class="emoji-shop-card-cover">
              <img
                v-if="item.coverUrl"
                :src="item.coverUrl"
                :alt="item.name"
                loading="lazy"
              />
              <div v-else class="emoji-shop-card-cover-ph">
                {{ (item.name || '').slice(0, 4) }}
              </div>
            </div>
            <div class="emoji-shop-card-body">
              <div class="emoji-shop-card-head">
                <h3 class="emoji-shop-card-title">{{ item.name }}</h3>
                <div class="emoji-shop-card-author">
                  <img
                    v-if="isDraftView ? userStore.avatarUrl : item.uploadUserAvatarUrl"
                    :src="isDraftView ? userStore.avatarUrl : item.uploadUserAvatarUrl"
                    :alt="(isDraftView ? userStore.nickname : item.uploadUserNickname) || '作者头像'"
                    class="emoji-shop-card-author-avatar"
                  />
                  <span v-else class="emoji-shop-card-author-avatar emoji-shop-card-author-avatar--fallback">
                    {{ ((isDraftView ? userStore.nickname : item.uploadUserNickname) || '萌').slice(0, 1) }}
                  </span>
                  <span class="emoji-shop-card-author-name">
                    {{ (isDraftView ? userStore.nickname : item.uploadUserNickname) || (isDraftView ? '当前用户' : '萌部落官方') }}
                  </span>
                </div>
              </div>
              <span
                v-if="isPublishedView"
                class="emoji-shop-card-status"
                :class="{
                  'is-online': Number(item.status) === 1,
                  'is-offline': Number(item.status) === 2,
                  'is-pending': Number(item.status) === 0,
                }"
              >
                {{ Number(item.status) === 1 ? '上架中' : (Number(item.status) === 0 ? '审核中' : '已下架') }}
              </span>
              <div class="emoji-shop-card-footer">
                <div class="emoji-shop-card-price">
                  <PawCoinIcon class="emoji-shop-card-price-icon" />
                  <span>{{ formatPrice(item.price) }}</span>
                </div>
                <button
                  type="button"
                  class="emoji-shop-card-action"
                  :class="isDraftView || isPublishedView ? 'is-draft' : (item.owned ? 'is-owned' : 'is-redeem')"
                  :disabled="(isOwnedView && item.owned) || (isPublishedView && Number(item.status) === 0)"
                  @click.stop="onCardClick(item)"
                >
                  {{ isDraftView
                    ? '继续编辑'
                    : (isPublishedView
                      ? (Number(item.status) === 0 ? '审核中' : '编辑')
                      : (isOwnedView && Number(item.status) === 2
                        ? '该表情包系列已被下架'
                        : (item.owned ? '已拥有' : '兑换'))) }}
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else-if="!loading" class="emoji-shop-empty">
          <img :src="emptyShopImageUrl" alt="暂无商品" class="emoji-shop-empty-image" />
          <p class="emoji-shop-empty-text">{{ emptyDescription }}</p>
        </div>
      </div>

      <div v-if="total > pageSize" class="emoji-shop-pagination">
        <AppPagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          @current-change="loadCurrentView"
        />
      </div>
    </div>

    <EmojiShopDetailDialog
      ref="detailDialogRef"
      :on-purchased="onDetailPurchased"
      :on-closed="onDetailClosed"
    />
    <EmojiShopUploadDialog
      ref="uploadDialogRef"
      :on-created="onUploadCreated"
      :on-draft-saved="onDraftSaved"
      :on-published-updated="onPublishedUpdated"
      :on-published-deleted="onPublishedDeleted"
      :on-closed="onUploadClosed"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import EmojiShopDetailDialog from '@/components/emoji-shop/EmojiShopDetailDialog.vue'
import EmojiShopUploadDialog from '@/components/emoji-shop/EmojiShopUploadDialog.vue'
import { useEmojiShop, EMOJI_SHOP_CATEGORY_TABS, SORT_OPTIONS } from '@scripts/views/EmojiShop'
import { EMJIO_SHOP_WEBP_URL as xiaomengAtelierHeroUrl } from '@/utils/clientOss'
import emptyShopImageUrl from '@/assets/images/biaoqing_not_item.png'

const detailDialogRef = ref(null)
const uploadDialogRef = ref(null)

const {
  Search,
  uploadIconUrl,
  userStore,
  loading,
  records,
  total,
  pageNum,
  pageSize,
  sort,
  category,
  keyword,
  isOwnedView,
  isDraftView,
  isPublishedView,
  isVipMember,
  emptyDescription,
  formatPrice,
  setSort,
  setCategory,
  showMyOwned,
  showMyDrafts,
  showMyPublished,
  onSearchInput,
  goDetail,
  onCardClick,
  loadCurrentView,
  goUpload,
  onUploadClosed,
  onUploadCreated,
  onDraftSaved,
  onPublishedUpdated,
  onPublishedDeleted,
  onDetailClosed,
  onDetailPurchased,
  loadList,
  loadMyPacks,
} = useEmojiShop(detailDialogRef, uploadDialogRef)
</script>

<style scoped src="@/assets/styles/emoji-shop.css"></style>
