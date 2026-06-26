<template>
  <el-popover
    v-model:visible="emojiPanelOpen"
    placement="top-end"
    :width="320"
    trigger="click"
    teleported
    popper-class="purchased-emoji-popper"
    :z-index="6500"
    @show="onEmojiPanelShow"
  >
    <template #reference>
      <button
        type="button"
        class="purchased-emoji-trigger"
        :disabled="disabled"
        title="已购表情"
        @click.stop
      >
        <img :src="emojiPackIconUrl" alt="" class="purchased-emoji-trigger-icon">
      </button>
    </template>
    <div v-loading="emojiShopStore.myPacksLoading" class="purchased-emoji-panel">
      <div v-if="!visiblePacks.length" class="purchased-emoji-empty">暂无已购表情包</div>
      <div v-else class="mc-emoji-purchased-layout">
        <div class="mc-emoji-pack-body">
          <div class="mc-emoji-grid mc-emoji-grid--pack mc-emoji-grid--scroll">
            <img
              v-for="(url, uidx) in (selectedPack?.imageUrls || [])"
              :key="uidx"
              :src="url"
              alt=""
              class="mc-emoji-thumb"
              @click="pickEmojiUrl(url, (u) => emit('pick', u))"
            >
          </div>
        </div>
        <div class="mc-emoji-pack-bar">
          <button
            v-if="packBarCanScrollLeft"
            type="button"
            class="mc-emoji-pack-more"
            aria-label="向左查看更多"
            @click="scrollPackBarLeft"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <div ref="packBarRef" class="mc-emoji-pack-bar-scroll" @scroll="onPackBarScroll">
            <div
              v-for="pack in visiblePacks"
              :key="pack.userEmojiId"
              class="mc-emoji-pack-bar-item"
            >
              <button
                type="button"
                class="mc-emoji-pack-cover"
                :class="{ 'is-active': Number(selectedPack?.shopId) === Number(pack.shopId) }"
                :title="pack.name"
                @click="selectPack(pack)"
              >
                <img :src="pack.coverUrl || pack.imageUrls?.[0]" alt="">
              </button>
              <transition name="mc-pack-name">
                <span
                  v-if="Number(selectedPack?.shopId) === Number(pack.shopId)"
                  :key="pack.shopId"
                  class="mc-emoji-pack-name"
                >{{ pack.name }}</span>
              </transition>
            </div>
          </div>
          <button
            v-if="packBarCanScrollRight"
            type="button"
            class="mc-emoji-pack-more"
            aria-label="向右查看更多"
            @click="scrollPackBarRight"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'
import { useGameRoomEmojiPicker } from '@/composables/useGameRoomEmojiPicker'

defineProps({
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['pick'])

const {
  emojiPanelOpen,
  emojiShopStore,
  packBarRef,
  packBarCanScrollLeft,
  packBarCanScrollRight,
  visiblePacks,
  selectedPack,
  onPackBarScroll,
  scrollPackBarLeft,
  scrollPackBarRight,
  selectPack,
  onEmojiPanelShow,
  pickEmojiUrl,
} = useGameRoomEmojiPicker()
</script>

<style src="@/assets/styles/purchased-emoji-popover.css"></style>
