<template>
  <div class="emoji-shop-page shell-page-scroll animate-fade-in">
    <div class="emoji-shop-inner">
      <header class="emoji-shop-hero">
        <div class="emoji-shop-hero-left">
          <h1 class="emoji-shop-title">表情商城</h1>
          <p class="emoji-shop-desc">发现有趣、治愈、充满创意的表情包系列</p>
        </div>
        <div class="emoji-shop-hero-right">
          <div
            class="emoji-shop-search-wrap"
            :class="{ 'is-vip': isVipMember }"
          >
            <el-input
              v-model="keyword"
              class="emoji-shop-search"
              placeholder="搜索你感兴趣的表情包…"
              clearable
              :prefix-icon="Search"
              @input="onSearchInput"
              @clear="isOwnedView ? loadMyPacks() : loadList(1)"
              @keyup.enter="isOwnedView ? loadMyPacks() : loadList(1)"
            />
          </div>
          <button
            v-if="userStore.isLoggedIn"
            type="button"
            class="emoji-shop-upload-btn"
            @click="goUpload"
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
              v-for="opt in MAIN_TABS"
              :key="opt.value"
              type="button"
              class="emoji-shop-main-tab"
              :class="{ 'is-active': !isOwnedView && sort === opt.value }"
              role="tab"
              :aria-selected="!isOwnedView && sort === opt.value"
              @click="setSort(opt.value)"
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
          </div>
        </div>
        <div v-if="!isOwnedView" class="emoji-shop-price-sort" aria-label="积分排序">
          <button
            v-for="opt in PRICE_SORTS"
            :key="opt.value"
            type="button"
            class="emoji-shop-price-sort-btn"
            :class="{ 'is-active': sort === opt.value }"
            @click="setSort(opt.value)"
          >
            {{ opt.label }}
            <span class="emoji-shop-price-sort-arrow">{{ opt.arrow }}</span>
          </button>
        </div>
      </div>

      <div v-if="loading" class="emoji-shop-grid emoji-shop-grid--loading">
        <div v-for="i in 12" :key="i" class="emoji-shop-grid-item">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" style="width: 100%; aspect-ratio: 1; border-radius: 16px" />
              <div style="padding: 12px 4px">
                <el-skeleton-item variant="h3" style="width: 70%" />
                <el-skeleton-item variant="text" style="width: 50%; margin-top: 10px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <div v-else-if="records.length" class="emoji-shop-grid">
        <article
          v-for="item in records"
          :key="item.id"
          class="emoji-shop-card"
          @click="goDetail(item.id)"
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
            <h3 class="emoji-shop-card-title">{{ item.name }}</h3>
            <div class="emoji-shop-card-footer">
              <div class="emoji-shop-card-price">
                <PawCoinIcon class="emoji-shop-card-price-icon" />
                <span>{{ formatPrice(item.price) }}</span>
              </div>
              <button
                type="button"
                class="emoji-shop-card-action"
                :class="item.owned ? 'is-owned' : 'is-redeem'"
                :disabled="item.owned"
                @click.stop="goDetail(item.id)"
              >
                {{ item.owned ? '已拥有' : '兑换' }}
              </button>
            </div>
          </div>
        </article>
      </div>

      <el-empty
        v-if="!loading && records.length === 0"
        :description="isOwnedView ? '还没有购买过表情包哦' : '暂无商品'"
        class="emoji-shop-empty"
      />

      <div v-if="!isOwnedView && total > pageSize" class="emoji-shop-pagination">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadList"
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
      :on-closed="onUploadClosed"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import EmojiShopDetailDialog from '@/components/emoji-shop/EmojiShopDetailDialog.vue'
import EmojiShopUploadDialog from '@/components/emoji-shop/EmojiShopUploadDialog.vue'
import { useEmojiShop, MAIN_TABS, PRICE_SORTS } from '@scripts/views/EmojiShop'

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
  keyword,
  isOwnedView,
  isVipMember,
  formatPrice,
  setSort,
  showMyOwned,
  onSearchInput,
  goDetail,
  goUpload,
  onUploadClosed,
  onUploadCreated,
  onDetailClosed,
  onDetailPurchased,
  loadList,
  loadMyPacks,
} = useEmojiShop(detailDialogRef, uploadDialogRef)
</script>

<style scoped src="@/assets/styles/emoji-shop.css"></style>
