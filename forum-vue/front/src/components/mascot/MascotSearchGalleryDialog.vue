<template>
  <el-dialog
    v-model="visibleModel"
    append-to-body
    class="mascot-search-gallery-dialog"
    width="min(820px, 92vw)"
    :show-close="true"
  >
    <div v-if="items.length" class="mascot-search-gallery">
      <a
        v-for="item in items"
        :key="item.url"
        class="mascot-search-gallery__item"
        :href="item.url"
        target="_blank"
        rel="noreferrer"
      >
        <img :src="item.url" :alt="item.title || '搜索图片'" loading="lazy">
        <span v-if="item.title" class="mascot-search-gallery__caption">{{ item.title }}</span>
      </a>
    </div>
  </el-dialog>
</template>

<script setup src="@/scripts/components/mascot/MascotSearchGalleryDialog.js"></script>

<style scoped src="./MascotSearchGalleryDialog.css"></style>
