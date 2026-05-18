<template>
  <div ref="shopPageRef" class="emoji-shop-page shell-page-scroll animate-fade-in">
    <ParticleSea embedded :host-ref="shopPageRef" />
    <div class="emoji-shop-inner">
      <header class="emoji-shop-header">
        <div class="emoji-shop-title-block">
          <img :src="emojiShopIconUrl" alt="" class="emoji-shop-title-icon" />
          <div>
            <h1 class="emoji-shop-title">表情商城</h1>
            <p class="emoji-shop-desc">用积分购买表情包，在私信里使用「已购」发送</p>
          </div>
        </div>
      </header>

      <div class="emoji-shop-toolbar">
        <div class="emoji-shop-sort-tabs" role="tablist" aria-label="排序">
          <button
            v-for="opt in SORT_OPTIONS"
            :key="opt.value"
            type="button"
            class="emoji-shop-sort-tab"
            :class="{ 'is-active': sort === opt.value }"
            role="tab"
            :aria-selected="sort === opt.value"
            @click="setSort(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>

        <div class="emoji-shop-toolbar-actions">
          <div
            class="emoji-shop-search-wrap"
            :class="{ 'is-vip': isVipMember }"
          >
            <el-input
              v-model="keyword"
              class="emoji-shop-search"
              placeholder="搜索表情包"
              clearable
              :prefix-icon="Search"
              @input="onSearchInput"
              @clear="loadList(1)"
              @keyup.enter="loadList(1)"
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
      </div>

      <hr class="emoji-shop-divider" />

      <div v-loading="loading" class="home-masonry emoji-shop-masonry" :class="{ 'home-masonry--loading': loading }">
        <div v-for="item in records" :key="item.id" class="home-masonry-item">
          <el-card
            class="note-card note-card--masonry emoji-shop-card"
            :body-style="{ padding: '0px' }"
            shadow="hover"
            @click="goDetail(item.id)"
          >
            <div class="note-cover note-cover--fluid">
              <img
                v-if="item.coverUrl"
                class="note-cover-img"
                :src="item.coverUrl"
                :alt="item.name"
                loading="lazy"
              />
              <div v-else class="note-cover-placeholder emoji-shop-cover-placeholder">
                <span class="cover-title">{{ (item.name || '').slice(0, 8) }}</span>
              </div>
            </div>
            <div class="note-info emoji-shop-card-info">
              <h3 class="note-title">{{ item.name }}</h3>
              <div class="emoji-shop-card-meta">
                <span class="emoji-shop-price">{{ formatPrice(item.price) }}</span>
                <div class="emoji-shop-card-tags">
                  <el-tag v-if="item.owned" type="success" size="small" effect="plain">已拥有</el-tag>
                  <span class="emoji-shop-sales">售 {{ item.salesCount ?? 0 }}</span>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <el-empty v-if="!loading && records.length === 0" description="暂无商品" class="emoji-shop-empty" />

      <div v-if="total > pageSize" class="emoji-shop-pagination">
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
import ParticleSea from '@/components/common/ParticleSea.vue'
import EmojiShopDetailDialog from '@/components/emoji-shop/EmojiShopDetailDialog.vue'
import EmojiShopUploadDialog from '@/components/emoji-shop/EmojiShopUploadDialog.vue'
import { useEmojiShop, SORT_OPTIONS } from '@scripts/views/EmojiShop'

const shopPageRef = ref(null)
const detailDialogRef = ref(null)
const uploadDialogRef = ref(null)

const {
  Search,
  emojiShopIconUrl,
  uploadIconUrl,
  userStore,
  loading,
  records,
  total,
  pageNum,
  pageSize,
  sort,
  keyword,
  isVipMember,
  formatPrice,
  setSort,
  onSearchInput,
  goDetail,
  goUpload,
  onUploadClosed,
  onUploadCreated,
  onDetailClosed,
  onDetailPurchased,
  loadList,
} = useEmojiShop(detailDialogRef, uploadDialogRef)
</script>

<style scoped src="@/assets/styles/emoji-shop.css"></style>
