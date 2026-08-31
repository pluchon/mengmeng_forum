<template>
  <div class="music-hall-discover">
    <MusicHallFeatured
      :track="featured"
      :loading="featuredLoading"
      :active-music-key="activeMusicKey"
      @play="onPlay"
    />
    <MusicHallRecommend
      :tracks="recommendTracks"
      :loading="recommendLoading"
      :page-num="recommendPageNum"
      :page-total="recommendPageTotal"
      :active-music-key="activeMusicKey"
      :moods="moodFilter"
      @play="onPlay"
      @page-change="onRecommendPageChange"
      @open-filter="moodDialogVisible = true"
    />
    <MusicHallHot
      :tracks="hotTracks"
      :loading="hotLoading"
      :page-num="hotPageNum"
      :page-total="hotPageTotal"
      :active-music-key="activeMusicKey"
      @play="onPlay"
      @page-change="onHotPageChange"
    />

    <MusicMoodFilterDialog
      v-model="moodDialogVisible"
      :selected="moodFilter"
      @apply="onMoodFilterApply"
    />
  </div>
</template>

<script src="./MusicHallDiscover.js"></script>
<style lang="scss" src="./MusicHallDiscover.scss"></style>
