<template>
  <section class="music-hall-featured">
    <div v-if="loading" class="music-hall-featured__loading">加载中...</div>
    <MusicHallEmpty v-else-if="!track" text="暂无精选歌曲" />
    <div v-else class="music-hall-featured__banner">
      <div class="music-hall-featured__info">
        <div class="music-hall-featured__tag">
          <el-icon aria-hidden="true"><MagicStick /></el-icon>
          <span>今日精选</span>
        </div>
        <div class="music-hall-featured__title-row">
          <h3 class="music-hall-featured__name">{{ track.title }}</h3>
          <span class="music-hall-featured__artist">{{ track.artist || '曲库配乐' }}</span>
        </div>
        <div class="music-hall-featured__stats">
          <span v-if="track.playCountText" class="music-hall-featured__plays">{{ track.playCountText }} 播放</span>
          <span v-if="track.playCountText && track.durationText" class="music-hall-featured__dot">·</span>
          <span class="music-hall-featured__duration">{{ track.durationText || '--:--' }}</span>
        </div>
      </div>
      <div class="music-hall-featured__cover-wrap">
        <div class="music-hall-featured__cover" :style="coverStyle">
          <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
        </div>
        <button
          type="button"
          class="music-hall-featured__play"
          :class="{ 'is-active': isPlaying }"
          aria-label="播放今日精选"
          @click="onPlay"
        >
          <el-icon><VideoPlay /></el-icon>
        </button>
      </div>
    </div>
  </section>
</template>

<script src="./MusicHallFeatured.js"></script>
<style lang="scss" src="./MusicHallFeatured.scss"></style>
